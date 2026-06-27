package com.skyvpn.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import com.skyvpn.app.SkyVPNApp
import com.skyvpn.app.core.TUNManager
import com.skyvpn.app.core.XrayCoreManager
import com.skyvpn.app.domain.model.ConnectionState
import com.skyvpn.app.domain.model.ConnectionStatus
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.repository.ConnectionStateRepository
import com.skyvpn.app.domain.repository.SettingsRepository
import com.skyvpn.app.domain.repository.VPNConfigRepository
import com.skyvpn.app.domain.repository.VPNLogRepository
import com.skyvpn.app.domain.model.VPNLog
import com.skyvpn.app.domain.model.LogLevel
import com.skyvpn.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class SkyVPNService : VpnService() {

    @Inject lateinit var configRepository: VPNConfigRepository
    @Inject lateinit var logRepository: VPNLogRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var reconnectManager: AutoReconnectManager
    @Inject lateinit var connectionStateRepository: ConnectionStateRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    private var vpnInterface: ParcelFileDescriptor? = null
    private var connectJob: Job? = null
    private var statsJob: Job? = null
    private var coreWatchJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentConfigId: Long = -1
    private var statsBaseRx: Long = 0
    private var statsBaseTx: Long = 0
    private var lastRx: Long = 0
    private var lastTx: Long = 0
    private var isDestroyed = false
    private val disconnectMutex = Mutex()

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState

    inner class LocalBinder : Binder() {
        fun getService(): SkyVPNService = this@SkyVPNService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        XrayCoreManager.initialize(this)
        TUNManager.initialize(this)
        reconnectManager.bindService(this)
        Timber.i("VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_CONNECT -> {
                val configId = intent.getLongExtra(EXTRA_CONFIG_ID, -1)
                if (configId > 0) {
                    startConnect(configId)
                }
            }
            ACTION_DISCONNECT -> {
                connectJob?.cancel()
                serviceScope.launch { disconnect(stopSelfWhenDone = false) }
            }
            ACTION_RECONNECT -> {
                serviceScope.launch { reconnect() }
            }
            ACTION_AUTO_CONNECT -> {
                Timber.i("Ignoring auto-connect request in manual-safe mode")
            }
        }
        return START_STICKY
    }

    private fun startConnect(configId: Long) {
        connectJob?.cancel()
        val newJob = serviceScope.launch {
            try {
                connect(configId)
            } catch (e: CancellationException) {
                addLogSafely(LogLevel.INFO, "Service", "Connect cancelled")
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "Unexpected connect failure")
                publishState(_connectionState.value.copy(
                    status = ConnectionStatus.ERROR,
                    errorMessage = e.message ?: "Unexpected connect failure"
                ))
                addLogSafely(LogLevel.ERROR, "Service", "Unexpected connect failure: ${e.message}")
                cleanupVpnResources()
            }
        }
        connectJob = newJob
        newJob.invokeOnCompletion {
            if (connectJob == newJob) {
                connectJob = null
            }
        }
    }

    private suspend fun connect(configId: Long) {
        if (_connectionState.value.status == ConnectionStatus.CONNECTED) {
            disconnect(stopSelfWhenDone = false)
        }

        acquireWakeLock()
        publishState(_connectionState.value.copy(status = ConnectionStatus.CONNECTING))
        addLog(LogLevel.INFO, "Service", "Connecting to server...")
        startForeground(SkyVPNApp.VPN_NOTIFICATION_ID, createNotification("Connecting..."))

        val config = configRepository.getConfigById(configId)
        if (config == null) {
            publishState(_connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = "Config not found"
            ))
            addLog(LogLevel.ERROR, "Service", "Config not found for id: $configId")
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        val configError = config.validationError()
        if (configError != null) {
            publishState(_connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = configError
            ))
            addLog(LogLevel.ERROR, "Service", configError)
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        currentConfigId = configId
        settingsRepository.setLastUsedConfigId(configId)

        val settings = settingsRepository.getSettings().first()
        if (!settings.enableIPv4 && !settings.enableIPv6) {
            publishState(_connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = "IPv4 or IPv6 must be enabled"
            ))
            addLog(LogLevel.ERROR, "Service", "IPv4 or IPv6 must be enabled")
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        val xrayConfig = XrayCoreManager.generateXrayConfig(config)
        addLog(LogLevel.DEBUG, "Xray", "Config generated for ${config.address}:${config.port}")

        val builder = Builder()
            .setSession("SkynetVPN")
            .setMtu(settings.mtu)
            .addDnsServer(config.dnsRemote)

        if (settings.enableIPv4) {
            builder.addAddress("10.10.10.1", 30)
            builder.addRoute("0.0.0.0", 0)
        }

        if (settings.enableIPv6) {
            builder.addAddress("fd00::1", 126)
            builder.addRoute("::", 0)
        }

        if (settings.bypassLAN) {
            builder.addRoute("10.0.0.0", 8)
            builder.addRoute("172.16.0.0", 12)
            builder.addRoute("192.168.0.0", 16)
        }

        if (settings.perAppVPN && settings.selectedApps.isNotEmpty()) {
            settings.selectedApps.filterNot { it == packageName }.forEach { pkg ->
                try {
                    builder.addAllowedApplication(pkg)
                } catch (e: Exception) {
                    Timber.w("Failed to add app: $pkg")
                }
            }
        } else {
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Timber.w(e, "Failed to exclude SkynetVPN from VPN routing")
            }
        }

        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            Timber.e(e, "Failed to establish VPN")
            publishState(_connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = e.message
            ))
            addLog(LogLevel.ERROR, "Service", "Failed to establish: ${e.message}")
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        val xrayStarted = XrayCoreManager.start(config, xrayConfig)
        if (!xrayStarted) {
            val errorMessage = XrayCoreManager.getLastError() ?: "Failed to start Xray core"
            publishState(_connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = errorMessage
            ))
            addLog(LogLevel.ERROR, "Xray", errorMessage)
            stopVpn()
            return
        }

        val tunStarted = TUNManager.start(
            vpnInterface = vpnInterface!!,
            socksHost = XrayCoreManager.LOCAL_SOCKS_HOST,
            socksPort = XrayCoreManager.LOCAL_SOCKS_PORT
        )
        if (!tunStarted) {
            val errorMessage = TUNManager.getLastError() ?: "Failed to start tun2socks"
            publishState(_connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = errorMessage
            ))
            addLog(LogLevel.ERROR, "TUN", errorMessage)
            stopVpn()
            return
        }

        publishState(_connectionState.value.copy(
            status = ConnectionStatus.CONNECTING,
            activeConfig = config,
            errorMessage = "Checking internet through config..."
        ))
        startForeground(SkyVPNApp.VPN_NOTIFICATION_ID, createNotification("Checking config connection..."))

        val health = verifyConfigConnection()
        if (!health.isConnected) {
            val errorMessage = health.message
            publishState(_connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                activeConfig = config,
                errorMessage = errorMessage
            ))
            addLog(LogLevel.ERROR, "Health", errorMessage)
            stopVpn()
            return
        }

        publishState(_connectionState.value.copy(
            status = ConnectionStatus.CONNECTED,
            activeConfig = config,
            ping = health.latencyMs,
            errorMessage = null
        ))

        addLog(LogLevel.INFO, "Service", "Connected to ${config.name} (${config.address}:${config.port}), internet OK ${health.latencyMs}ms")

        startForeground(SkyVPNApp.VPN_NOTIFICATION_ID, createNotification("Connected to ${config.name}"))
        if (settingsRepository.getAutoReconnect()) {
            reconnectManager.startMonitoring()
        }
        reconnectManager.onConnectionEstablished()
        startStatsMonitoring()
        startCoreWatch()
    }

    private suspend fun disconnect(stopSelfWhenDone: Boolean = false) {
        disconnectMutex.withLock {
            val hadActiveResources = hasActiveVpnResources()
            if (hadActiveResources) {
                publishState(_connectionState.value.copy(status = ConnectionStatus.DISCONNECTING))
                addLogSafely(LogLevel.INFO, "Service", "Disconnecting...")
            }

            cleanupVpnResources()

            publishState(ConnectionState())
            if (hadActiveResources) {
                addLogSafely(LogLevel.INFO, "Service", "Disconnected")
            }

            if (stopSelfWhenDone && !isDestroyed) {
                stopSelf()
            }
        }
    }

    private suspend fun reconnect() {
        if (currentConfigId > 0) {
            cleanupVpnResources()

            val reconnectState = _connectionState.value.copy(
                status = ConnectionStatus.RECONNECTING,
                reconnectCount = _connectionState.value.reconnectCount + 1
            )
            publishState(reconnectState)
            addLog(LogLevel.WARN, "Service", "Reconnecting (attempt ${reconnectState.reconnectCount})...")

            connect(currentConfigId)
        }
    }

    private fun startStatsMonitoring() {
        statsJob?.cancel()
        statsBaseRx = normalizedTrafficBytes(TrafficStats.getUidRxBytes(Process.myUid()))
        statsBaseTx = normalizedTrafficBytes(TrafficStats.getUidTxBytes(Process.myUid()))
        lastRx = statsBaseRx
        lastTx = statsBaseTx
        statsJob = serviceScope.launch {
            while (true) {
                delay(1000)
                val state = _connectionState.value
                if (state.status == ConnectionStatus.CONNECTED) {
                    val rx = normalizedTrafficBytes(TrafficStats.getUidRxBytes(Process.myUid()))
                    val tx = normalizedTrafficBytes(TrafficStats.getUidTxBytes(Process.myUid()))
                    publishState(state.copy(
                        uptime = state.uptime + 1000,
                        downloadBytes = (rx - statsBaseRx).coerceAtLeast(0),
                        uploadBytes = (tx - statsBaseTx).coerceAtLeast(0),
                        downloadSpeed = (rx - lastRx).coerceAtLeast(0),
                        uploadSpeed = (tx - lastTx).coerceAtLeast(0)
                    ))
                    lastRx = rx
                    lastTx = tx
                }
            }
        }
    }

    private fun stopStatsMonitoring() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun startCoreWatch() {
        coreWatchJob?.cancel()
        coreWatchJob = serviceScope.launch {
            while (true) {
                delay(3000)
                if (_connectionState.value.status == ConnectionStatus.CONNECTED && !XrayCoreManager.isProcessAlive()) {
                    addLog(LogLevel.ERROR, "Xray", "Core process stopped unexpectedly")
                    reconnectManager.onConnectionLost()
                    break
                }
            }
        }
    }

    private fun stopCoreWatch() {
        coreWatchJob?.cancel()
        coreWatchJob = null
    }

    private suspend fun verifyConfigConnection(): ConnectionHealth {
        delay(800)
        val testUrls = listOf(
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://cp.cloudflare.com/generate_204",
            "https://example.com"
        )
        var lastError = "Internet check failed"

        repeat(2) { attempt ->
            for (url in testUrls) {
                val result = checkUrlThroughXrayProxy(url)
                if (result.isConnected) return result
                lastError = result.message
            }
            if (attempt < 1) delay(1000)
        }

        return ConnectionHealth(
            isConnected = false,
            latencyMs = -1,
            message = "Config connected to tunnel, but internet check failed. Server, UUID/password, TLS/SNI, or network setting may be wrong. Last error: $lastError"
        )
    }

    private suspend fun checkUrlThroughXrayProxy(url: String): ConnectionHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val proxy = Proxy(
            Proxy.Type.HTTP,
            InetSocketAddress(XrayCoreManager.LOCAL_SOCKS_HOST, XrayCoreManager.LOCAL_HTTP_PORT)
        )

        try {
            val connection = (URL(url).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SkynetVPN/1.0")
                setRequestProperty("Connection", "close")
            }
            val code = connection.responseCode
            runCatching {
                if (code >= 400) connection.errorStream?.close() else connection.inputStream?.close()
            }
            connection.disconnect()

            val elapsed = System.currentTimeMillis() - start
            if (code in 200..399) {
                ConnectionHealth(true, elapsed, "Internet OK")
            } else {
                ConnectionHealth(false, -1, "$url returned HTTP $code")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.d(e, "Health check failed for $url")
            ConnectionHealth(false, -1, e.message ?: "Failed to reach $url")
        }
    }

    private suspend fun addLog(level: LogLevel, tag: String, message: String) {
        logRepository.insertLog(VPNLog(level = level, tag = tag, message = message))
        Timber.log(when (level) {
            LogLevel.DEBUG -> android.util.Log.DEBUG
            LogLevel.INFO -> android.util.Log.INFO
            LogLevel.WARN -> android.util.Log.WARN
            LogLevel.ERROR -> android.util.Log.ERROR
        }, "SkynetVPN/$tag", message)
    }

    private suspend fun addLogSafely(level: LogLevel, tag: String, message: String) {
        runCatching {
            addLog(level, tag, message)
        }.onFailure {
            Timber.w(it, "Failed to write VPN log")
        }
    }

    private fun publishState(state: ConnectionState) {
        _connectionState.value = state
        connectionStateRepository.update(state)
    }

    private data class ConnectionHealth(
        val isConnected: Boolean,
        val latencyMs: Long,
        val message: String
    )

    override fun onRevoke() {
        super.onRevoke()
        connectJob?.cancel()
        serviceScope.launch { disconnect() }
    }

    override fun onDestroy() {
        isDestroyed = true
        reconnectManager.stopMonitoring()
        reconnectManager.unbindService()
        connectJob?.cancel()
        cleanupVpnResources()
        publishState(ConnectionState())
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopVpn() {
        cleanupVpnResources()
    }

    private fun cleanupVpnResources() {
        stopStatsMonitoring()
        stopCoreWatch()
        reconnectManager.stopMonitoring()
        try {
            TUNManager.stop()
            XrayCoreManager.stop()
            runCatching {
                vpnInterface?.close()
            }.onFailure {
                Timber.w(it, "Failed to close VPN interface")
            }
            vpnInterface = null
            runCatching {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }.onFailure {
                Timber.w(it, "Failed to stop foreground notification")
            }
            releaseWakeLock()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping VPN")
        }
    }

    private fun hasActiveVpnResources(): Boolean {
        val status = _connectionState.value.status
        return status != ConnectionStatus.DISCONNECTED ||
            vpnInterface != null ||
            TUNManager.isTunActive() ||
            XrayCoreManager.isRunning.value
    }

    private fun VPNConfig.validationError(): String? {
        if (address.isBlank()) return "Config server address is empty"
        if (port !in 1..65535) return "Config server port is invalid"
        return when (protocol) {
            com.skyvpn.app.domain.model.VPNProtocol.VMESS,
            com.skyvpn.app.domain.model.VPNProtocol.VLESS ->
                if (uuid.isBlank()) "Config UUID is empty" else null
            com.skyvpn.app.domain.model.VPNProtocol.TROJAN ->
                if (password.isBlank()) "Config password is empty" else null
            com.skyvpn.app.domain.model.VPNProtocol.SHADOWSOCKS ->
                if (method.isBlank() || password.isBlank()) "Config Shadowsocks credentials are incomplete" else null
            com.skyvpn.app.domain.model.VPNProtocol.SOCKS,
            com.skyvpn.app.domain.model.VPNProtocol.HTTP -> null
        }
    }

    private suspend fun autoConnectLastConfig() {
        startForeground(SkyVPNApp.VPN_NOTIFICATION_ID, createNotification("Preparing auto connect..."))
        val configId = settingsRepository.getLastUsedConfigId()
        if (configId > 0 && VpnService.prepare(this) == null) {
            connect(configId)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SkynetVPN:ConnectionWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Timber.w(e, "Failed to release wake lock")
        } finally {
            wakeLock = null
        }
    }

    private fun normalizedTrafficBytes(value: Long): Long =
        if (value == TrafficStats.UNSUPPORTED.toLong()) 0 else value

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, SkyVPNService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, SkyVPNApp.VPN_CHANNEL_ID)
            .setContentTitle("SkynetVPN")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(
                Notification.Action.Builder(
                    null, "Disconnect", disconnectPending
                ).build()
            )
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.skyvpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.skyvpn.app.DISCONNECT"
        const val ACTION_RECONNECT = "com.skyvpn.app.RECONNECT"
        const val ACTION_AUTO_CONNECT = "com.skyvpn.app.AUTO_CONNECT"
        const val EXTRA_CONFIG_ID = "extra_config_id"
    }
}

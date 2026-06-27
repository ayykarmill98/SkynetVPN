package com.skyvpn.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.skyvpn.app.domain.model.ConnectionStatus
import com.skyvpn.app.domain.repository.SettingsRepository
import com.skyvpn.app.domain.repository.VPNLogRepository
import com.skyvpn.app.domain.model.VPNLog
import com.skyvpn.app.domain.model.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoReconnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val logRepository: VPNLogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var retryCount = 0
    private var currentDelay: Long = 1000
    private var maxRetry = 10
    private var baseDelay: Long = 1000

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting

    private var vpnServiceRef: SkyVPNService? = null
    private var lastNetworkType: String? = null

    fun bindService(service: SkyVPNService) {
        vpnServiceRef = service
    }

    fun unbindService() {
        vpnServiceRef = null
    }

    fun startMonitoring() {
        if (networkCallback != null) return
        registerNetworkCallback()
        Timber.i("Auto reconnect monitoring started")
    }

    fun stopMonitoring() {
        unregisterNetworkCallback()
        cancelRetry()
        Timber.i("Auto reconnect monitoring stopped")
    }

    fun onConnectionLost() {
        val serviceStatus = vpnServiceRef?.connectionState?.value?.status
        if (serviceStatus != ConnectionStatus.CONNECTED &&
            serviceStatus != ConnectionStatus.RECONNECTING &&
            serviceStatus != ConnectionStatus.CONNECTING) {
            return
        }

        scope.launch {
            val autoReconnect = settingsRepository.getAutoReconnect()
            if (!autoReconnect) {
                addLog(LogLevel.WARN, "Reconnect", "Auto reconnect is disabled")
                return@launch
            }

            maxRetry = settingsRepository.getMaxRetryCount()
            baseDelay = settingsRepository.getRetryDelay()
            currentDelay = baseDelay
            retryCount = 0
            _isReconnecting.value = true

            addLog(LogLevel.WARN, "Reconnect", "Connection lost, starting auto reconnect...")
            startRetryLoop()
        }
    }

    fun onConnectionEstablished() {
        cancelRetry()
        retryCount = 0
        currentDelay = baseDelay
        _isReconnecting.value = false
    }

    private fun startRetryLoop() {
        retryJob?.cancel()
        retryJob = scope.launch {
            while (retryCount < maxRetry && _isReconnecting.value) {
                addLog(
                    LogLevel.INFO,
                    "Reconnect",
                    "Retry $retryCount/$maxRetry (delay: ${currentDelay}ms)"
                )

                if (isNetworkAvailable()) {
                    addLog(LogLevel.INFO, "Reconnect", "Network available, reconnecting...")
                    triggerReconnect()
                    delay(5000)

                    val service = vpnServiceRef
                    val state = service?.connectionState?.value
                    if (state?.status == ConnectionStatus.CONNECTED) {
                        onConnectionEstablished()
                        addLog(LogLevel.INFO, "Reconnect", "Successfully reconnected!")
                        return@launch
                    }
                } else {
                    addLog(LogLevel.WARN, "Reconnect", "No network, waiting...")
                }

                retryCount++
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(30000)
            }

            if (retryCount >= maxRetry) {
                _isReconnecting.value = false
                addLog(
                    LogLevel.ERROR,
                    "Reconnect",
                    "Max retry count ($maxRetry) reached, giving up."
                )
            }
        }
    }

    private fun triggerReconnect() {
        val service = vpnServiceRef
        if (service == null) {
            val intent = android.content.Intent(context, SkyVPNService::class.java).apply {
                action = SkyVPNService.ACTION_AUTO_CONNECT
            }
            context.startForegroundService(intent)
            return
        }
        val intent = android.content.Intent(service, SkyVPNService::class.java).apply {
            action = SkyVPNService.ACTION_RECONNECT
        }
        service.onStartCommand(intent, 0, 0)
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Timber.d("Network available")
                    val currentType = getNetworkType(network)
                    if (lastNetworkType != null && lastNetworkType != currentType) {
                        addLog(
                            LogLevel.INFO,
                            "Network",
                            "Network changed: $lastNetworkType -> $currentType"
                        )
                        onNetworkChanged()
                    }
                    lastNetworkType = currentType
                }

                override fun onLost(network: Network) {
                    Timber.d("Network lost")
                    addLog(LogLevel.WARN, "Network", "Network lost")
                    onConnectionLost()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    val newType = when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                        else -> "Other"
                    }
                    if (lastNetworkType != null && lastNetworkType != newType) {
                        addLog(
                            LogLevel.INFO,
                            "Network",
                            "Transport changed: $lastNetworkType -> $newType"
                        )
                        onNetworkChanged()
                    }
                    lastNetworkType = newType
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
            networkCallback = null
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister network callback")
        }
    }

    private fun onNetworkChanged() {
        if (vpnServiceRef?.connectionState?.value?.status == ConnectionStatus.CONNECTED) {
            scope.launch {
                delay(1000)
                onConnectionLost()
            }
        }
    }

    private fun getNetworkType(network: Network): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return try {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun addLog(level: LogLevel, tag: String, message: String) {
        scope.launch {
            logRepository.insertLog(VPNLog(level = level, tag = tag, message = message))
        }
    }
}

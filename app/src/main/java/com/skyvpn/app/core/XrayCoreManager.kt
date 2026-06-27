package com.skyvpn.app.core

import android.content.Context
import android.os.Build
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.model.VPNProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object XrayCoreManager {

    const val LOCAL_SOCKS_HOST = "127.0.0.1"
    const val LOCAL_SOCKS_PORT = 10808
    const val LOCAL_HTTP_PORT = 10809

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var coreProcess: Process? = null
    private var configDir: File? = null
    private var binaryFile: File? = null
    private var lastError: String? = null

    fun initialize(context: Context) {
        configDir = File(context.filesDir, "xray")
        configDir?.mkdirs()
        binaryFile = findNativeExecutable(context, "libxray.so")
        copyAssetIfExists(context, "xray", "geoip.dat")
        copyAssetIfExists(context, "xray", "geosite.dat")
        lastError = if (binaryFile == null) "Xray binary missing for this device ABI" else null
        Timber.d("XrayCoreManager initialized, configDir: ${configDir?.absolutePath}")
    }

    fun generateXrayConfig(config: VPNConfig): String {
        val log = """
            "log": {
                "loglevel": "warning",
                "access": "",
                "error": ""
            }
        """.trimIndent()

        val dns = """
            "dns": {
                "servers": ["${jsonEscape(config.dnsRemote.ifEmpty { "8.8.8.8" })}", "1.1.1.1"]
            }
        """.trimIndent()

        val routing = """
            "routing": {
                "domainStrategy": "IPIfNonMatch",
                "rules": []
            }
        """.trimIndent()

        val outbound = generateOutbound(config)

        return """
            {
                $log,
                $dns,
                $routing,
                "inbounds": [
                    {
                        "listen": "$LOCAL_SOCKS_HOST",
                        "port": $LOCAL_SOCKS_PORT,
                        "protocol": "socks",
                        "settings": {
                            "auth": "noauth",
                            "udp": true
                        },
                        "sniffing": {
                            "enabled": true,
                            "destOverride": ["http", "tls"]
                        }
                    },
                    {
                        "listen": "$LOCAL_SOCKS_HOST",
                        "port": $LOCAL_HTTP_PORT,
                        "protocol": "http",
                        "settings": {}
                    }
                ],
                "outbounds": [
                    $outbound,
                    {
                        "protocol": "freedom",
                        "tag": "direct",
                        "settings": {}
                    }
                ]
            }
        """.trimIndent()
    }

    private fun generateOutbound(config: VPNConfig): String {
        return when (config.protocol) {
            VPNProtocol.VMESS -> generateVMess(config)
            VPNProtocol.VLESS -> generateVLESS(config)
            VPNProtocol.TROJAN -> generateTrojan(config)
            VPNProtocol.SHADOWSOCKS -> generateShadowsocks(config)
            VPNProtocol.SOCKS -> generateSocks(config)
            VPNProtocol.HTTP -> generateHTTP(config)
        }
    }

    private fun generateVMess(config: VPNConfig): String {
        val settings = """
            "vnext": [
                {
                    "address": "${config.address}",
                    "port": ${config.port},
                    "users": [
                        {
                            "id": "${jsonEscape(config.uuid)}",
                            "alterId": ${config.alterId},
                            "encryption": "${jsonEscape(config.encryption.ifEmpty { "auto" })}",
                            "security": "auto"
                        }
                    ]
                }
            ]
        """.trimIndent()

        return """
            {
                "protocol": "vmess",
                "settings": { $settings },
                ${generateStreamSettings(config)}
            }
        """.trimIndent()
    }

    private fun generateVLESS(config: VPNConfig): String {
        val settings = """
            "vnext": [
                {
                    "address": "${config.address}",
                    "port": ${config.port},
                    "users": [
                        {
                            "id": "${jsonEscape(config.uuid)}",
                            "encryption": "none",
                            ${if (config.flow.isNotEmpty()) "\"flow\": \"${jsonEscape(config.flow)}\"," else ""}
                            "level": 8
                        }
                    ]
                }
            ]
        """.trimIndent()

        return """
            {
                "protocol": "vless",
                "settings": { $settings },
                ${generateStreamSettings(config)}
            }
        """.trimIndent()
    }

    private fun generateTrojan(config: VPNConfig): String {
        val settings = """
            "servers": [
                {
                    "address": "${config.address}",
                    "port": ${config.port},
                    "password": "${jsonEscape(config.password)}",
                    "level": 8
                }
            ]
        """.trimIndent()

        return """
            {
                "protocol": "trojan",
                "settings": { $settings },
                ${generateStreamSettings(config)}
            }
        """.trimIndent()
    }

    private fun generateShadowsocks(config: VPNConfig): String {
        val settings = """
            "servers": [
                {
                    "address": "${config.address}",
                    "port": ${config.port},
                    "method": "${jsonEscape(config.method.ifEmpty { "aes-256-gcm" })}",
                    "password": "${jsonEscape(config.password)}",
                    "level": 8
                }
            ]
        """.trimIndent()

        return """
            {
                "protocol": "shadowsocks",
                "settings": { $settings }
                ${if (config.transportType != com.skyvpn.app.domain.model.TransportType.TCP) {
                    ",\n${generateStreamSettings(config)}"
                } else ""}
            }
        """.trimIndent()
    }

    private fun generateSocks(config: VPNConfig): String {
        return """
            {
                "protocol": "socks",
                "settings": {
                    "servers": [
                        {
                            "address": "${config.address}",
                            "port": ${config.port},
                            "users": [
                                {
                                    "user": "${jsonEscape(config.username)}",
                                    "pass": "${jsonEscape(config.password)}"
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()
    }

    private fun generateHTTP(config: VPNConfig): String {
        return """
            {
                "protocol": "http",
                "settings": {
                    "servers": [
                        {
                            "address": "${config.address}",
                            "port": ${config.port},
                            "users": [
                                {
                                    "user": "${jsonEscape(config.username)}",
                                    "pass": "${jsonEscape(config.password)}"
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()
    }

    private fun generateStreamSettings(config: VPNConfig): String {
        val network = when (config.transportType) {
            com.skyvpn.app.domain.model.TransportType.TCP -> "tcp"
            com.skyvpn.app.domain.model.TransportType.WEBSOCKET -> "ws"
            com.skyvpn.app.domain.model.TransportType.GRPC -> "grpc"
            com.skyvpn.app.domain.model.TransportType.HTTP_UPGRADE -> "httpupgrade"
            com.skyvpn.app.domain.model.TransportType.HTTP2 -> "http"
        }

        val security = when (config.security) {
            com.skyvpn.app.domain.model.SecurityType.NONE -> "none"
            com.skyvpn.app.domain.model.SecurityType.TLS -> "tls"
            com.skyvpn.app.domain.model.SecurityType.REALITY -> "reality"
        }

        val parts = mutableListOf(
            "\"network\": \"$network\"",
            "\"security\": \"$security\""
        )

        when (config.transportType) {
            com.skyvpn.app.domain.model.TransportType.WEBSOCKET -> {
                val wsPath = normalizePath(config.path)
                val wsHost = config.host.ifBlank { config.sni }.ifBlank { config.address }
                parts += """
                    "wsSettings": {
                        "path": "${jsonEscape(wsPath)}",
                        "headers": { "Host": "${jsonEscape(wsHost)}" }
                    }
                """.trimIndent()
            }
            com.skyvpn.app.domain.model.TransportType.GRPC -> {
                parts += """
                    "grpcSettings": {
                        "serviceName": "${jsonEscape(config.path)}"${if (config.host.isNotBlank()) ",\n                        \"authority\": \"${jsonEscape(config.host)}\"" else ""}
                    }
                """.trimIndent()
            }
            com.skyvpn.app.domain.model.TransportType.HTTP_UPGRADE -> {
                val host = config.host.ifBlank { config.sni }.ifBlank { config.address }
                parts += """
                    "httpupgradeSettings": {
                        "path": "${jsonEscape(normalizePath(config.path))}",
                        "host": "${jsonEscape(host)}"
                    }
                """.trimIndent()
            }
            com.skyvpn.app.domain.model.TransportType.HTTP2 -> {
                val host = config.host.ifBlank { config.sni }.ifBlank { config.address }
                parts += """
                    "httpSettings": {
                        "path": "${jsonEscape(normalizePath(config.path))}",
                        "host": ["${jsonEscape(host)}"]
                    }
                """.trimIndent()
            }
            com.skyvpn.app.domain.model.TransportType.TCP -> Unit
        }

        when (config.security) {
            com.skyvpn.app.domain.model.SecurityType.TLS -> {
                parts += """
                    "tlsSettings": {
                        "serverName": "${jsonEscape(config.sni.ifBlank { config.host }.ifBlank { config.address })}",
                        "allowInsecure": false,
                        "fingerprint": "${jsonEscape(config.fingerprint.ifEmpty { "chrome" })}"
                    }
                """.trimIndent()
            }
            com.skyvpn.app.domain.model.SecurityType.REALITY -> {
                parts += """
                    "realitySettings": {
                        "serverName": "${jsonEscape(config.sni.ifBlank { config.serverName }.ifBlank { config.address })}",
                        "publicKey": "${jsonEscape(config.publicKey)}",
                        "shortId": "${jsonEscape(config.shortId)}",
                        "fingerprint": "${jsonEscape(config.fingerprint.ifEmpty { "chrome" })}",
                        "spiderX": "${jsonEscape(config.spiderX.ifEmpty { "/" })}"
                    }
                """.trimIndent()
            }
            com.skyvpn.app.domain.model.SecurityType.NONE -> Unit
        }

        return """
            "streamSettings": {
                ${parts.joinToString(",\n                ")}
            }
        """.trimIndent()
    }

    suspend fun start(config: VPNConfig, configJson: String): Boolean {
        return try {
            stop()
            val executable = binaryFile ?: run {
                lastError = "Xray binary missing. Add jniLibs/<abi>/libxray.so"
                Timber.e(lastError)
                _isRunning.value = false
                return false
            }
            val directory = configDir ?: run {
                lastError = "Xray config directory is not initialized"
                Timber.e(lastError)
                _isRunning.value = false
                return false
            }
            val configFile = File(directory, "config.json").apply {
                writeText(configJson)
            }
            coreProcess = ProcessBuilder(
                executable.absolutePath,
                "run",
                "-config",
                configFile.absolutePath
            )
                .directory(directory)
                .redirectErrorStream(true)
                .start()

            kotlinx.coroutines.delay(500)
            if (!isProcessAlive()) {
                val output = readProcessOutput(coreProcess)
                lastError = if (output.isNotBlank()) {
                    "Xray exited: ${output.takeLast(240)}"
                } else {
                    "Xray process exited immediately for ${config.address}:${config.port}"
                }
                Timber.e(lastError)
                _isRunning.value = false
                return false
            }

            lastError = null
            _isRunning.value = true
            Timber.i("Xray core started for ${config.address}:${config.port}")
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Failed to start Xray core"
            Timber.e(e, "Failed to start Xray core")
            _isRunning.value = false
            false
        }
    }

    fun stop() {
        try {
            coreProcess?.let { process ->
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
            coreProcess = null
            _isRunning.value = false
            Timber.i("Xray core stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Xray core")
        }
    }

    fun isProcessAlive(): Boolean = coreProcess?.isAlive == true

    fun getLastError(): String? = lastError

    private fun findNativeExecutable(context: Context, libraryName: String): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        return File(nativeDir, libraryName).takeIf { it.exists() }
    }

    private fun normalizePath(path: String): String {
        val cleanPath = path.ifBlank { "/" }
        return if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"
    }

    private fun jsonEscape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun readProcessOutput(process: Process?): String {
        if (process == null) return ""
        return runCatching {
            process.inputStream.bufferedReader().use { reader ->
                reader.readText()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
            }
        }.getOrDefault("")
    }

    private fun copyAssetIfExists(context: Context, assetName: String, fileName: String) {
        val directory = configDir ?: return
        val abiAsset = Build.SUPPORTED_ABIS
            .asSequence()
            .map { "$assetName/$it/$fileName" }
            .firstOrNull { assetPath ->
                runCatching { context.assets.open(assetPath).close() }.isSuccess
            }
            ?: return

        runCatching {
            context.assets.open(abiAsset).use { input ->
                FileOutputStream(File(directory, fileName)).use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure {
            Timber.w(it, "Failed to copy $abiAsset")
        }
    }
}

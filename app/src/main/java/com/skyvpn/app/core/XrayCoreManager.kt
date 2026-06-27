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

    fun initialize(context: Context) {
        configDir = File(context.filesDir, "xray")
        configDir?.mkdirs()
        binaryFile = extractExecutable(
            context = context,
            assetName = "xray",
            targetName = "xray"
        )
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
                "servers": [
                    {
                        "address": "${config.dnsRemote}",
                        "port": 53,
                        "domains": ["geosite:cn", "geosite:private"]
                    },
                    "${config.dnsRemote}"
                ]
            }
        """.trimIndent()

        val routing = """
            "routing": {
                "domainStrategy": "IPIfNonMatch",
                "rules": [
                    {
                        "type": "field",
                        "outboundTag": "direct",
                        "domain": ["geosite:private"]
                    },
                    {
                        "type": "field",
                        "outboundTag": "direct",
                        "ip": ["geoip:private"]
                    }
                ]
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
                            "id": "${config.uuid}",
                            "alterId": ${config.alterId},
                            "encryption": "${config.encryption.ifEmpty { "auto" }}",
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
                            "id": "${config.uuid}",
                            "encryption": "none",
                            ${if (config.flow.isNotEmpty()) "\"flow\": \"${config.flow}\"," else ""}
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
                    "password": "${config.password}",
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
                    "method": "${config.method.ifEmpty { "aes-256-gcm" }}",
                    "password": "${config.password}",
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
                                    "user": "${config.username}",
                                    "pass": "${config.password}"
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
                                    "user": "${config.username}",
                                    "pass": "${config.password}"
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

        val wsSettings = if (config.transportType == com.skyvpn.app.domain.model.TransportType.WEBSOCKET) {
            """,
                    "wsSettings": {
                        "path": "${config.path}",
                        "headers": { "Host": "${config.host}" }
                    }"""
        } else ""

        val grpcSettings = if (config.transportType == com.skyvpn.app.domain.model.TransportType.GRPC) {
            """,
                    "grpcSettings": {
                        "serviceName": "${config.path}",
                        "authority": "${config.host}"
                    }"""
        } else ""

        val securitySettings = if (config.security == com.skyvpn.app.domain.model.SecurityType.TLS) {
            """,
                "tlsSettings": {
                    "serverName": "${config.sni.ifEmpty { config.address }}",
                    "allowInsecure": false,
                    "fingerprint": "${config.fingerprint.ifEmpty { "chrome" }}"
                }
            """.trimIndent()
        } else if (config.security == com.skyvpn.app.domain.model.SecurityType.REALITY) {
            """,
                "realitySettings": {
                    "serverName": "${config.sni}",
                    "publicKey": "${config.publicKey}",
                    "shortId": "${config.shortId}",
                    "fingerprint": "${config.fingerprint.ifEmpty { "chrome" }}",
                    "spiderX": "${config.spiderX}"
                }
            """.trimIndent()
        } else {
            ""
        }

        return """
            "streamSettings": {
                "network": "$network",
                "security": "$security"$wsSettings$grpcSettings$securitySettings
            }
        """.trimIndent()
    }

    suspend fun start(config: VPNConfig, configJson: String): Boolean {
        return try {
            stop()
            val executable = binaryFile ?: run {
                Timber.e("Xray binary is missing. Add assets/xray/<abi>/xray")
                _isRunning.value = false
                return false
            }
            val directory = configDir ?: run {
                Timber.e("Xray config directory is not initialized")
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
                .redirectErrorStream(true)
                .start()

            kotlinx.coroutines.delay(500)
            if (!isProcessAlive()) {
                Timber.e("Xray process exited immediately for ${config.address}:${config.port}")
                _isRunning.value = false
                return false
            }

            _isRunning.value = true
            Timber.i("Xray core started for ${config.address}:${config.port}")
            true
        } catch (e: Exception) {
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

    private fun extractExecutable(context: Context, assetName: String, targetName: String): File? {
        val abiAsset = Build.SUPPORTED_ABIS
            .asSequence()
            .map { "$assetName/$it/$targetName" }
            .firstOrNull { assetPath ->
                runCatching { context.assets.open(assetPath).close() }.isSuccess
            }
            ?: return null

        val target = File(context.filesDir, "$targetName-${abiAsset.substringAfter("$assetName/").substringBefore("/")}")
        runCatching {
            context.assets.open(abiAsset).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.setExecutable(true, true)
        }.onFailure {
            Timber.e(it, "Failed to extract $abiAsset")
            return null
        }
        return target
    }
}

package com.skyvpn.app.util

import android.net.Uri
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.model.VPNProtocol
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ConfigExporter {

    fun exportStandard(config: VPNConfig): String {
        return when (config.protocol) {
            VPNProtocol.VMESS -> exportToVMess(config)
            VPNProtocol.VLESS -> exportToVLESS(config)
            VPNProtocol.TROJAN -> exportToTrojan(config)
            VPNProtocol.SHADOWSOCKS -> exportToSS(config)
            VPNProtocol.SOCKS -> exportToSocks(config)
            VPNProtocol.HTTP -> exportToHTTP(config)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun exportProtected(config: VPNConfig): String {
        val encrypted = CryptoUtils.encryptAES(exportStandard(config), PROTECTED_EXPORT_KEY)
        val encoded = Base64.UrlSafe.encode(encrypted).trimEnd('=')
        return "${protectedPrefix(config.protocol)}$encoded"
    }

    fun isProtectedExport(raw: String): Boolean =
        protectedPrefixes.any { raw.startsWith(it, ignoreCase = true) }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeProtected(raw: String): String? {
        val prefix = protectedPrefixes.firstOrNull { raw.startsWith(it, ignoreCase = true) } ?: return null
        val encoded = raw.removePrefix(prefix)
        val padded = encoded.padEnd(encoded.length + (4 - encoded.length % 4) % 4, '=')
        return runCatching {
            CryptoUtils.decryptAES(Base64.UrlSafe.decode(padded), PROTECTED_EXPORT_KEY)
        }.getOrNull()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun exportToVMess(config: VPNConfig): String {
        val json = buildString {
            append("{")
            append("\"v\":\"2\",")
            append("\"ps\":\"${jsonEscape(config.name)}\",")
            append("\"add\":\"${jsonEscape(config.address)}\",")
            append("\"port\":\"${config.port}\",")
            append("\"id\":\"${jsonEscape(config.uuid)}\",")
            append("\"aid\":\"${config.alterId}\",")
            append("\"net\":\"${transportToVMess(config.transportType)}\",")
            append("\"type\":\"none\",")
            append("\"host\":\"${jsonEscape(config.host)}\",")
            append("\"path\":\"${jsonEscape(config.path)}\",")
            append("\"tls\":\"${if (config.security == com.skyvpn.app.domain.model.SecurityType.TLS) "tls" else ""}\",")
            append("\"sni\":\"${jsonEscape(config.sni)}\"")
            append("}")
        }
        return "vmess://${Base64.encode(json.toByteArray())}"
    }

    fun exportToVLESS(config: VPNConfig): String {
        val params = mutableListOf<String>()
        params.add("type=${transportToURI(config.transportType)}")
        if (config.host.isNotEmpty()) params.add("host=${config.host}")
        if (config.path.isNotEmpty()) params.add("path=${config.path}")
        if (config.sni.isNotEmpty()) params.add("sni=${config.sni}")
        params.add("security=${securityToURI(config.security)}")
        if (config.flow.isNotEmpty()) params.add("flow=${config.flow}")
        if (config.publicKey.isNotEmpty()) params.add("pbk=${config.publicKey}")
        if (config.shortId.isNotEmpty()) params.add("sid=${config.shortId}")
        if (config.fingerprint.isNotEmpty()) params.add("fp=${config.fingerprint}")
        if (config.spiderX.isNotEmpty()) params.add("spx=${config.spiderX}")

        return "vless://${config.uuid}@${config.address}:${config.port}?${params.joinToString("&")}#${Uri.encode(config.name)}"
    }

    fun exportToTrojan(config: VPNConfig): String {
        val params = mutableListOf<String>()
        params.add("type=${transportToURI(config.transportType)}")
        if (config.host.isNotEmpty()) params.add("host=${config.host}")
        if (config.path.isNotEmpty()) params.add("path=${config.path}")
        if (config.sni.isNotEmpty()) params.add("sni=${config.sni}")
        params.add("security=${securityToURI(config.security)}")

        return "trojan://${Uri.encode(config.password)}@${config.address}:${config.port}?${params.joinToString("&")}#${Uri.encode(config.name)}"
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun exportToSS(config: VPNConfig): String {
        val userInfo = "${config.method}:${config.password}"
        val encoded = Base64.encode(userInfo.toByteArray())
        return "ss://${encoded}@${config.address}:${config.port}#${Uri.encode(config.name)}"
    }

    fun exportToSocks(config: VPNConfig): String {
        val auth = if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
            "${Uri.encode(config.username)}:${Uri.encode(config.password)}@"
        } else {
            ""
        }
        return "socks://$auth${config.address}:${config.port}#${Uri.encode(config.name)}"
    }

    fun exportToHTTP(config: VPNConfig): String {
        val auth = if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
            "${Uri.encode(config.username)}:${Uri.encode(config.password)}@"
        } else {
            ""
        }
        return "http://$auth${config.address}:${config.port}#${Uri.encode(config.name)}"
    }

    private fun transportToVMess(type: com.skyvpn.app.domain.model.TransportType): String {
        return when (type) {
            com.skyvpn.app.domain.model.TransportType.TCP -> "tcp"
            com.skyvpn.app.domain.model.TransportType.WEBSOCKET -> "ws"
            com.skyvpn.app.domain.model.TransportType.GRPC -> "grpc"
            com.skyvpn.app.domain.model.TransportType.HTTP_UPGRADE -> "httpupgrade"
            com.skyvpn.app.domain.model.TransportType.HTTP2 -> "h2"
        }
    }

    private fun transportToURI(type: com.skyvpn.app.domain.model.TransportType): String {
        return when (type) {
            com.skyvpn.app.domain.model.TransportType.TCP -> "tcp"
            com.skyvpn.app.domain.model.TransportType.WEBSOCKET -> "ws"
            com.skyvpn.app.domain.model.TransportType.GRPC -> "grpc"
            com.skyvpn.app.domain.model.TransportType.HTTP_UPGRADE -> "httpupgrade"
            com.skyvpn.app.domain.model.TransportType.HTTP2 -> "http"
        }
    }

    private fun securityToURI(security: com.skyvpn.app.domain.model.SecurityType): String {
        return when (security) {
            com.skyvpn.app.domain.model.SecurityType.NONE -> "none"
            com.skyvpn.app.domain.model.SecurityType.TLS -> "tls"
            com.skyvpn.app.domain.model.SecurityType.REALITY -> "reality"
        }
    }

    private fun protectedPrefix(protocol: VPNProtocol): String {
        return when (protocol) {
            VPNProtocol.VMESS -> "skynet-vmess://"
            VPNProtocol.VLESS -> "skynet-vless://"
            VPNProtocol.TROJAN -> "skynet-trojan://"
            VPNProtocol.SHADOWSOCKS -> "skynet-ss://"
            VPNProtocol.SOCKS -> "skynet-socks://"
            VPNProtocol.HTTP -> "skynet-http://"
        }
    }

    private fun jsonEscape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private val protectedPrefixes = listOf(
        "skynet-vmess://",
        "skynet-vless://",
        "skynet-trojan://",
        "skynet-ss://",
        "skynet-socks://",
        "skynet-http://"
    )

    private const val PROTECTED_EXPORT_KEY = "SkynetVPN-Protected-Export-v1"
}

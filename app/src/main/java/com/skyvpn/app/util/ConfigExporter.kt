package com.skyvpn.app.util

import android.net.Uri
import com.skyvpn.app.domain.model.VPNConfig
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ConfigExporter {

    @OptIn(ExperimentalEncodingApi::class)
    fun exportToVMess(config: VPNConfig): String {
        val json = buildString {
            append("{")
            append("\"v\":\"2\",")
            append("\"ps\":\"${config.name}\",")
            append("\"add\":\"${config.address}\",")
            append("\"port\":\"${config.port}\",")
            append("\"id\":\"${config.uuid}\",")
            append("\"aid\":\"${config.alterId}\",")
            append("\"net\":\"${transportToVMess(config.transportType)}\",")
            append("\"type\":\"none\",")
            append("\"host\":\"${config.host}\",")
            append("\"path\":\"${config.path}\",")
            append("\"tls\":\"${if (config.security == com.skyvpn.app.domain.model.SecurityType.TLS) "tls" else ""}\",")
            append("\"sni\":\"${config.sni}\"")
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
}

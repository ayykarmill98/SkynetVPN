package com.skyvpn.app.util

import android.net.Uri
import com.skyvpn.app.domain.model.SecurityType
import com.skyvpn.app.domain.model.TransportType
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.model.VPNProtocol
import timber.log.Timber
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ConfigParser {

    fun parseConfig(raw: String): VPNConfig? {
        val trimmed = raw.trim()
        return try {
            when {
                trimmed.startsWith("vmess://") -> parseVMess(trimmed)
                trimmed.startsWith("vless://") -> parseVLESS(trimmed)
                trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
                trimmed.startsWith("ss://") || trimmed.startsWith("shadowsocks://") -> parseShadowsocks(trimmed)
                trimmed.startsWith("socks://") -> parseSocks(trimmed)
                trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
                    parseHTTP(trimmed)
                trimmed.startsWith("{") -> parseXrayJSON(trimmed)
                else -> {
                    Timber.w("Unknown config format")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing config")
            null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseVMess(raw: String): VPNConfig? {
        val encoded = raw.removePrefix("vmess://")
        val decoded = Base64.decode(encoded)
        val json = decoded.toString(Charsets.UTF_8)

        val ps = extractJsonString(json, "ps")
        val add = extractJsonString(json, "add")
        val port = extractJsonInt(json, "port", 443)
        val id = extractJsonString(json, "id")
        val aid = extractJsonInt(json, "aid", 0)
        val net = extractJsonString(json, "net", "tcp")
        val type = extractJsonString(json, "type", "none")
        val host = extractJsonString(json, "host")
        val path = extractJsonString(json, "path")
        val tls = extractJsonString(json, "tls", "")
        val sni = extractJsonString(json, "sni")

        return VPNConfig(
            name = ps.ifEmpty { add },
            protocol = VPNProtocol.VMESS,
            address = add,
            port = port,
            uuid = id,
            alterId = aid,
            transportType = parseTransport(net),
            host = host,
            path = path,
            sni = sni,
            security = if (tls == "tls") SecurityType.TLS else SecurityType.NONE,
            rawConfig = raw
        )
    }

    private fun parseVLESS(raw: String): VPNConfig? {
        val uri = Uri.parse(raw)
        val uuid = uri.userInfo ?: return null
        val address = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }

        return VPNConfig(
            name = uri.fragment ?: address,
            protocol = VPNProtocol.VLESS,
            address = address,
            port = port,
            uuid = uuid,
            flow = params["flow"] ?: "",
            transportType = parseTransport(params["type"] ?: "tcp"),
            host = params["host"] ?: "",
            path = params["path"] ?: "",
            sni = params["sni"] ?: "",
            security = when (params["security"]) {
                "tls" -> SecurityType.TLS
                "reality" -> SecurityType.REALITY
                else -> SecurityType.NONE
            },
            publicKey = params["pbk"] ?: "",
            shortId = params["sid"] ?: "",
            fingerprint = params["fp"] ?: "",
            spiderX = params["spx"] ?: "",
            serverName = params["sni"] ?: "",
            rawConfig = raw
        )
    }

    private fun parseTrojan(raw: String): VPNConfig? {
        val uri = Uri.parse(raw)
        val password = uri.userInfo ?: return null
        val address = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }

        return VPNConfig(
            name = uri.fragment ?: address,
            protocol = VPNProtocol.TROJAN,
            address = address,
            port = port,
            password = password,
            transportType = parseTransport(params["type"] ?: "tcp"),
            host = params["host"] ?: "",
            path = params["path"] ?: "",
            sni = params["sni"] ?: "",
            security = when (params["security"]) {
                "tls" -> SecurityType.TLS
                "reality" -> SecurityType.REALITY
                else -> SecurityType.NONE
            },
            publicKey = params["pbk"] ?: "",
            shortId = params["sid"] ?: "",
            fingerprint = params["fp"] ?: "",
            rawConfig = raw
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseShadowsocks(raw: String): VPNConfig? {
        val clean = raw.removePrefix("ss://").removePrefix("shadowsocks://")
        val parts = clean.split("#")
        val name = parts.getOrNull(1) ?: "SS Server"
        val encoded = parts[0]

        val atIdx = encoded.indexOf('@')
        val decoded: String
        val serverPart: String

        if (atIdx > 0) {
            decoded = encoded.substring(0, atIdx)
            serverPart = encoded.substring(atIdx + 1)
        } else {
            decoded = try {
                Base64.decode(encoded).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                return null
            }
            val at = decoded.indexOf('@')
            if (at < 0) return null
            val method = decoded.substring(0, at)
            serverPart = decoded.substring(at + 1)
            val serverParts = serverPart.split(":")
            return VPNConfig(
                name = name,
                protocol = VPNProtocol.SHADOWSOCKS,
                address = serverParts.getOrNull(0) ?: "",
                port = serverParts.getOrNull(1)?.toIntOrNull() ?: 443,
                method = method,
                password = "",
                rawConfig = raw
            )
        }

        val decodedCreds = try {
            Base64.UrlSafe.decode(decoded).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            decoded
        }

        val credParts = decodedCreds.split(":")
        val method = credParts.getOrNull(0) ?: "aes-256-gcm"
        val password = credParts.getOrNull(1) ?: ""
        val serverParts = serverPart.split(":")

        return VPNConfig(
            name = name,
            protocol = VPNProtocol.SHADOWSOCKS,
            address = serverParts.getOrNull(0) ?: "",
            port = serverParts.getOrNull(1)?.toIntOrNull() ?: 443,
            method = method,
            password = password,
            rawConfig = raw
        )
    }

    private fun parseSocks(raw: String): VPNConfig? {
        val uri = Uri.parse(raw)
        val address = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 1080

        return VPNConfig(
            name = uri.fragment ?: "SOCKS $address",
            protocol = VPNProtocol.SOCKS,
            address = address,
            port = port,
            username = uri.userInfo?.split(":")?.getOrNull(0) ?: "",
            password = uri.userInfo?.split(":")?.getOrNull(1) ?: "",
            rawConfig = raw
        )
    }

    private fun parseHTTP(raw: String): VPNConfig {
        val uri = Uri.parse(raw)
        return VPNConfig(
            name = uri.fragment ?: "HTTP ${uri.host}",
            protocol = VPNProtocol.HTTP,
            address = uri.host ?: "",
            port = uri.port.takeIf { it > 0 } ?: 80,
            username = uri.userInfo?.split(":")?.getOrNull(0) ?: "",
            password = uri.userInfo?.split(":")?.getOrNull(1) ?: "",
            rawConfig = raw
        )
    }

    private fun parseXrayJSON(raw: String): VPNConfig? {
        return VPNConfig(
            name = "Custom Config",
            rawConfig = raw
        )
    }

    private fun parseTransport(net: String): TransportType {
        return when (net.lowercase()) {
            "ws", "websocket" -> TransportType.WEBSOCKET
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTP_UPGRADE
            "h2", "http" -> TransportType.HTTP2
            else -> TransportType.TCP
        }
    }

    private fun extractJsonString(json: String, key: String, default: String = ""): String {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val match = Regex(pattern).find(json)
        return match?.groupValues?.getOrNull(1) ?: default
    }

    private fun extractJsonInt(json: String, key: String, default: Int = 0): Int {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val match = Regex(pattern).find(json)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: default
    }
}

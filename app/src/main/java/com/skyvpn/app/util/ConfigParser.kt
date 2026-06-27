package com.skyvpn.app.util

import android.net.Uri
import com.skyvpn.app.domain.model.SecurityType
import com.skyvpn.app.domain.model.TransportType
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.model.VPNProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ConfigParser {

    fun parseConfigs(rawText: String): List<VPNConfig> {
        val normalized = rawText.trim()
        if (normalized.isEmpty()) return emptyList()

        val direct = parseConfig(normalized)
        if (direct != null) return listOf(direct)

        val subscriptionText = decodeSubscription(normalized) ?: normalized
        return subscriptionText
            .lines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseConfig(it) }
            .toList()
    }

    fun parseConfig(raw: String): VPNConfig? {
        val trimmed = raw.trim()
        return try {
            val config = when {
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
            config?.takeIf { getValidationError(it) == null }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing config")
            null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseVMess(raw: String): VPNConfig? {
        val encoded = raw.removePrefix("vmess://")
        val json = decodeBase64ToString(encoded) ?: return null
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()

        val ps = obj?.jsonString("ps") ?: extractJsonString(json, "ps")
        val add = obj?.jsonString("add") ?: extractJsonString(json, "add")
        val port = obj?.jsonInt("port") ?: extractJsonInt(json, "port", 443)
        val id = obj?.jsonString("id") ?: extractJsonString(json, "id")
        val aid = obj?.jsonInt("aid") ?: extractJsonInt(json, "aid", 0)
        val net = obj?.jsonString("net", "tcp") ?: extractJsonString(json, "net", "tcp")
        val host = obj?.jsonString("host") ?: extractJsonString(json, "host")
        val path = obj?.jsonString("path") ?: extractJsonString(json, "path")
        val tls = obj?.jsonString("tls") ?: extractJsonString(json, "tls", "")
        val sni = obj?.jsonString("sni") ?: extractJsonString(json, "sni")

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
            val fullConfig = decodeBase64ToString(encoded) ?: return null
            decoded = fullConfig
            val at = decoded.indexOf('@')
            if (at < 0) return null
            val credentials = decoded.substring(0, at)
            serverPart = decoded.substring(at + 1)
            val credentialParts = credentials.split(":", limit = 2)
            val method = credentialParts.getOrNull(0) ?: "aes-256-gcm"
            val password = credentialParts.getOrNull(1) ?: ""
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

        val decodedCreds = decodeBase64ToString(decoded) ?: decoded

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
        Timber.w("Raw Xray JSON import is not supported yet")
        return null
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

    private fun kotlinx.serialization.json.JsonObject.jsonString(key: String, default: String = ""): String =
        get(key)?.jsonPrimitive?.content ?: default

    private fun kotlinx.serialization.json.JsonObject.jsonInt(key: String): Int? =
        get(key)?.jsonPrimitive?.intOrNull

    fun getValidationError(config: VPNConfig): String? {
        if (config.address.isBlank()) return "server address is empty"
        if (config.port !in 1..65535) return "server port is invalid"
        return when (config.protocol) {
            VPNProtocol.VMESS, VPNProtocol.VLESS ->
                if (config.uuid.isBlank()) "UUID is empty" else null
            VPNProtocol.TROJAN ->
                if (config.password.isBlank()) "password is empty" else null
            VPNProtocol.SHADOWSOCKS ->
                if (config.method.isBlank() || config.password.isBlank()) "Shadowsocks credentials are incomplete" else null
            VPNProtocol.SOCKS, VPNProtocol.HTTP -> null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeSubscription(raw: String): String? {
        if (raw.contains("://")) return null
        return decodeBase64ToString(raw)
            ?.takeIf { decoded ->
                decoded.lineSequence().any { line ->
                    val trimmed = line.trim()
                    trimmed.startsWith("vmess://") ||
                        trimmed.startsWith("vless://") ||
                        trimmed.startsWith("trojan://") ||
                        trimmed.startsWith("ss://") ||
                        trimmed.startsWith("shadowsocks://")
                }
            }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64ToString(value: String): String? {
        val compact = value.trim().filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return null
        val padded = compact.padEnd(compact.length + (4 - compact.length % 4) % 4, '=')
        return listOf(
            { Base64.decode(padded) },
            { Base64.UrlSafe.decode(padded) }
        ).firstNotNullOfOrNull { decode ->
            runCatching { decode().toString(Charsets.UTF_8) }.getOrNull()
        }
    }
}

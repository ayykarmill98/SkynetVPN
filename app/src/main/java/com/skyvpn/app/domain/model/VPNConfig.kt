package com.skyvpn.app.domain.model

enum class VPNProtocol {
    VMESS, VLESS, TROJAN, SHADOWSOCKS, SOCKS, HTTP
}

enum class TransportType {
    TCP, WEBSOCKET, GRPC, HTTP_UPGRADE, HTTP2
}

enum class SecurityType {
    NONE, TLS, REALITY
}

enum class VPNConfigSource {
    MANUAL, FREE
}

data class VPNConfig(
    val id: Long = 0,
    val name: String = "",
    val protocol: VPNProtocol = VPNProtocol.VMESS,
    val address: String = "",
    val port: Int = 443,
    val uuid: String = "",
    val alterId: Int = 0,
    val flow: String = "",
    val encryption: String = "",
    val transportType: TransportType = TransportType.TCP,
    val host: String = "",
    val path: String = "",
    val sni: String = "",
    val security: SecurityType = SecurityType.TLS,
    val publicKey: String = "",
    val shortId: String = "",
    val serverName: String = "",
    val fingerprint: String = "",
    val spiderX: String = "",
    val username: String = "",
    val password: String = "",
    val method: String = "",
    val rawConfig: String = "",
    val isLocked: Boolean = false,
    val lockPassword: String = "",
    val isExpired: Boolean = false,
    val expireDate: Long = 0,
    val watermark: String = "",
    val dnsRemote: String = "8.8.8.8",
    val isPinned: Boolean = false,
    val source: VPNConfigSource = VPNConfigSource.MANUAL,
    val freeAccountId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isFreeAccount: Boolean
        get() = source == VPNConfigSource.FREE
}

data class ServerInfo(
    val ping: Long = -1,
    val isConnected: Boolean = false,
    val isAvailable: Boolean = true
)

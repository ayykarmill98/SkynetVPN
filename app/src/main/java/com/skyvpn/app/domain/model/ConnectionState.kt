package com.skyvpn.app.domain.model

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    RECONNECTING,
    ERROR
}

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeConfig: VPNConfig? = null,
    val uptime: Long = 0,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val ping: Long = -1,
    val errorMessage: String? = null,
    val reconnectCount: Int = 0
)

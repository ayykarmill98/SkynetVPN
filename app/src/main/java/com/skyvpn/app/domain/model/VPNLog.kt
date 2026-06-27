package com.skyvpn.app.domain.model

data class VPNLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "",
    val message: String = ""
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

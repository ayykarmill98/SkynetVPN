package com.skyvpn.app.domain.model

data class AppSettings(
    val autoConnect: Boolean = false,
    val autoReconnect: Boolean = true,
    val retryDelay: Long = 1000,
    val maxRetryCount: Int = 10,
    val dnsMode: String = "remote",
    val enableIPv4: Boolean = true,
    val enableIPv6: Boolean = false,
    val mtu: Int = 1500,
    val enableUDP: Boolean = true,
    val enableTCP: Boolean = true,
    val enableFakeDNS: Boolean = false,
    val bypassLAN: Boolean = true,
    val perAppVPN: Boolean = false,
    val selectedApps: List<String> = emptyList(),
    val killSwitch: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val lastUsedConfigId: Long = -1
)

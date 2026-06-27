package com.skyvpn.app.domain.repository

import com.skyvpn.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
    suspend fun getAutoConnect(): Boolean
    suspend fun setAutoConnect(enabled: Boolean)
    suspend fun getAutoReconnect(): Boolean
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun getRetryDelay(): Long
    suspend fun setRetryDelay(delay: Long)
    suspend fun getMaxRetryCount(): Int
    suspend fun setMaxRetryCount(count: Int)
    suspend fun getLastUsedConfigId(): Long
    suspend fun setLastUsedConfigId(id: Long)
    suspend fun getKillSwitch(): Boolean
    suspend fun setKillSwitch(enabled: Boolean)
    suspend fun getIsDarkTheme(): Boolean?
    suspend fun setIsDarkTheme(isDark: Boolean?)
    suspend fun getSelectedApps(): List<String>
    suspend fun setSelectedApps(apps: List<String>)
    suspend fun getPerAppVPN(): Boolean
    suspend fun setPerAppVPN(enabled: Boolean)
    suspend fun getBypassLAN(): Boolean
    suspend fun setBypassLAN(enabled: Boolean)
    suspend fun getMTU(): Int
    suspend fun setMTU(mtu: Int)
    suspend fun getDNSMode(): String
    suspend fun setDNSMode(mode: String)
    suspend fun setEnableIPv4(enabled: Boolean)
    suspend fun setEnableIPv6(enabled: Boolean)
    suspend fun setEnableUDP(enabled: Boolean)
    suspend fun setEnableTCP(enabled: Boolean)
    suspend fun setEnableFakeDNS(enabled: Boolean)
}

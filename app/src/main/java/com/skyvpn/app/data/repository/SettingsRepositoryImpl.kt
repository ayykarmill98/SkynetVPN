package com.skyvpn.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.skyvpn.app.domain.model.AppSettings
import com.skyvpn.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object Keys {
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val RETRY_DELAY = longPreferencesKey("retry_delay")
        val MAX_RETRY_COUNT = intPreferencesKey("max_retry_count")
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val ENABLE_IPV4 = booleanPreferencesKey("enable_ipv4")
        val ENABLE_IPV6 = booleanPreferencesKey("enable_ipv6")
        val MTU = intPreferencesKey("mtu")
        val ENABLE_UDP = booleanPreferencesKey("enable_udp")
        val ENABLE_TCP = booleanPreferencesKey("enable_tcp")
        val ENABLE_FAKE_DNS = booleanPreferencesKey("enable_fake_dns")
        val BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        val PER_APP_VPN = booleanPreferencesKey("per_app_vpn")
        val SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val LAST_CONFIG_ID = longPreferencesKey("last_config_id")
    }

    override fun getSettings(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: false,
            autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: true,
            retryDelay = prefs[Keys.RETRY_DELAY] ?: 1000L,
            maxRetryCount = prefs[Keys.MAX_RETRY_COUNT] ?: 10,
            dnsMode = prefs[Keys.DNS_MODE] ?: "remote",
            enableIPv4 = prefs[Keys.ENABLE_IPV4] ?: true,
            enableIPv6 = prefs[Keys.ENABLE_IPV6] ?: false,
            mtu = prefs[Keys.MTU] ?: 1500,
            enableUDP = prefs[Keys.ENABLE_UDP] ?: true,
            enableTCP = prefs[Keys.ENABLE_TCP] ?: true,
            enableFakeDNS = prefs[Keys.ENABLE_FAKE_DNS] ?: false,
            bypassLAN = prefs[Keys.BYPASS_LAN] ?: true,
            perAppVPN = prefs[Keys.PER_APP_VPN] ?: false,
            selectedApps = prefs[Keys.SELECTED_APPS]?.toList() ?: emptyList(),
            killSwitch = prefs[Keys.KILL_SWITCH] ?: false,
            isDarkTheme = prefs[Keys.DARK_THEME]?.let {
                when (it) { "true" -> true; "false" -> false; else -> null }
            },
            lastUsedConfigId = prefs[Keys.LAST_CONFIG_ID] ?: -1L
        )
    }

    override suspend fun updateSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_CONNECT] = settings.autoConnect
            prefs[Keys.AUTO_RECONNECT] = settings.autoReconnect
            prefs[Keys.RETRY_DELAY] = settings.retryDelay
            prefs[Keys.MAX_RETRY_COUNT] = settings.maxRetryCount
            prefs[Keys.DNS_MODE] = settings.dnsMode
            prefs[Keys.ENABLE_IPV4] = settings.enableIPv4
            prefs[Keys.ENABLE_IPV6] = settings.enableIPv6
            prefs[Keys.MTU] = settings.mtu
            prefs[Keys.ENABLE_UDP] = settings.enableUDP
            prefs[Keys.ENABLE_TCP] = settings.enableTCP
            prefs[Keys.ENABLE_FAKE_DNS] = settings.enableFakeDNS
            prefs[Keys.BYPASS_LAN] = settings.bypassLAN
            prefs[Keys.PER_APP_VPN] = settings.perAppVPN
            prefs[Keys.SELECTED_APPS] = settings.selectedApps.toSet()
            prefs[Keys.KILL_SWITCH] = settings.killSwitch
            prefs[Keys.DARK_THEME] = settings.isDarkTheme?.toString() ?: "system"
            prefs[Keys.LAST_CONFIG_ID] = settings.lastUsedConfigId
        }
    }

    override suspend fun getAutoConnect(): Boolean =
        dataStore.data.first()[Keys.AUTO_CONNECT] ?: false

    override suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    override suspend fun getAutoReconnect(): Boolean =
        dataStore.data.first()[Keys.AUTO_RECONNECT] ?: true

    override suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    override suspend fun getRetryDelay(): Long =
        dataStore.data.first()[Keys.RETRY_DELAY] ?: 1000L

    override suspend fun setRetryDelay(delay: Long) {
        dataStore.edit { it[Keys.RETRY_DELAY] = delay }
    }

    override suspend fun getMaxRetryCount(): Int =
        dataStore.data.first()[Keys.MAX_RETRY_COUNT] ?: 10

    override suspend fun setMaxRetryCount(count: Int) {
        dataStore.edit { it[Keys.MAX_RETRY_COUNT] = count }
    }

    override suspend fun getLastUsedConfigId(): Long =
        dataStore.data.first()[Keys.LAST_CONFIG_ID] ?: -1L

    override suspend fun setLastUsedConfigId(id: Long) {
        dataStore.edit { it[Keys.LAST_CONFIG_ID] = id }
    }

    override suspend fun getKillSwitch(): Boolean =
        dataStore.data.first()[Keys.KILL_SWITCH] ?: false

    override suspend fun setKillSwitch(enabled: Boolean) {
        dataStore.edit { it[Keys.KILL_SWITCH] = enabled }
    }

    override suspend fun getIsDarkTheme(): Boolean? {
        val value = dataStore.data.first()[Keys.DARK_THEME]
        return when (value) { "true" -> true; "false" -> false; else -> null }
    }

    override suspend fun setIsDarkTheme(isDark: Boolean?) {
        dataStore.edit { it[Keys.DARK_THEME] = isDark?.toString() ?: "system" }
    }

    override suspend fun getSelectedApps(): List<String> =
        dataStore.data.first()[Keys.SELECTED_APPS]?.toList() ?: emptyList()

    override suspend fun setSelectedApps(apps: List<String>) {
        dataStore.edit { it[Keys.SELECTED_APPS] = apps.toSet() }
    }

    override suspend fun getPerAppVPN(): Boolean =
        dataStore.data.first()[Keys.PER_APP_VPN] ?: false

    override suspend fun setPerAppVPN(enabled: Boolean) {
        dataStore.edit { it[Keys.PER_APP_VPN] = enabled }
    }

    override suspend fun getBypassLAN(): Boolean =
        dataStore.data.first()[Keys.BYPASS_LAN] ?: true

    override suspend fun setBypassLAN(enabled: Boolean) {
        dataStore.edit { it[Keys.BYPASS_LAN] = enabled }
    }

    override suspend fun getMTU(): Int =
        dataStore.data.first()[Keys.MTU] ?: 1500

    override suspend fun setMTU(mtu: Int) {
        dataStore.edit { it[Keys.MTU] = mtu }
    }

    override suspend fun getDNSMode(): String =
        dataStore.data.first()[Keys.DNS_MODE] ?: "remote"

    override suspend fun setDNSMode(mode: String) {
        dataStore.edit { it[Keys.DNS_MODE] = mode }
    }

    override suspend fun setEnableIPv4(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_IPV4] = enabled }
    }

    override suspend fun setEnableIPv6(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_IPV6] = enabled }
    }

    override suspend fun setEnableUDP(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_UDP] = enabled }
    }

    override suspend fun setEnableTCP(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_TCP] = enabled }
    }

    override suspend fun setEnableFakeDNS(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_FAKE_DNS] = enabled }
    }
}

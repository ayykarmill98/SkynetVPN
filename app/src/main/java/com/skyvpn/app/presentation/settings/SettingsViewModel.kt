package com.skyvpn.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyvpn.app.domain.model.AppSettings
import com.skyvpn.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect {
                _settings.value = it
            }
        }
    }

    fun updateAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoConnect(enabled)
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoReconnect(enabled)
        }
    }

    fun updateRetryDelay(delay: Long) {
        viewModelScope.launch {
            settingsRepository.setRetryDelay(delay)
        }
    }

    fun updateMaxRetryCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxRetryCount(count)
        }
    }

    fun updateKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKillSwitch(enabled)
        }
    }

    fun updateDarkTheme(isDark: Boolean?) {
        viewModelScope.launch {
            settingsRepository.setIsDarkTheme(isDark)
        }
    }

    fun updateBypassLAN(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBypassLAN(enabled)
        }
    }

    fun updatePerAppVPN(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPerAppVPN(enabled)
        }
    }

    fun updateMTU(mtu: Int) {
        viewModelScope.launch {
            settingsRepository.setMTU(mtu)
        }
    }

    fun updateDNSMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setDNSMode(mode)
        }
    }

    fun updateEnableIPv4(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableIPv4(enabled)
        }
    }

    fun updateEnableIPv6(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableIPv6(enabled)
        }
    }

    fun updateEnableUDP(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableUDP(enabled)
        }
    }

    fun updateEnableTCP(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableTCP(enabled)
        }
    }

    fun updateEnableFakeDNS(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableFakeDNS(enabled)
        }
    }
}

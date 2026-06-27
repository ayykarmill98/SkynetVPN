package com.skyvpn.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.repository.ConnectionStateRepository
import com.skyvpn.app.domain.repository.SettingsRepository
import com.skyvpn.app.domain.repository.VPNConfigRepository
import com.skyvpn.app.service.AutoReconnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configRepository: VPNConfigRepository,
    private val settingsRepository: SettingsRepository,
    private val reconnectManager: AutoReconnectManager,
    private val connectionStateRepository: ConnectionStateRepository
) : ViewModel() {

    val connectionState = connectionStateRepository.connectionState

    private val _configs = MutableStateFlow<List<VPNConfig>>(emptyList())
    val configs: StateFlow<List<VPNConfig>> = _configs.asStateFlow()

    private val _isAutoReconnecting = MutableStateFlow(false)
    val isAutoReconnecting: StateFlow<Boolean> = _isAutoReconnecting.asStateFlow()

    private val _lastUsedConfigId = MutableStateFlow(-1L)
    val lastUsedConfigId: StateFlow<Long> = _lastUsedConfigId.asStateFlow()

    init {
        loadConfigs()
        observeReconnect()
        observeSettings()
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            configRepository.getAllConfigs().collect { configs ->
                _configs.value = configs
            }
        }
    }

    private fun observeReconnect() {
        viewModelScope.launch {
            reconnectManager.isReconnecting.collect {
                _isAutoReconnecting.value = it
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _lastUsedConfigId.value = settings.lastUsedConfigId
            }
        }
    }

    fun setSelectedConfig(config: VPNConfig) {
        connectionStateRepository.update(connectionState.value.copy(activeConfig = config))
    }

    fun updateConnectionState(state: com.skyvpn.app.domain.model.ConnectionState) =
        connectionStateRepository.update(state)
}

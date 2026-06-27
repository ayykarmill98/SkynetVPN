package com.skyvpn.app.presentation.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.repository.SettingsRepository
import com.skyvpn.app.domain.repository.VPNConfigRepository
import com.skyvpn.app.domain.usecase.ExportConfigUseCase
import com.skyvpn.app.util.ConfigParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConfigListViewModel @Inject constructor(
    private val configRepository: VPNConfigRepository,
    private val settingsRepository: SettingsRepository,
    private val exportConfigUseCase: ExportConfigUseCase
) : ViewModel() {

    private val _configs = MutableStateFlow<List<VPNConfig>>(emptyList())
    val configs: StateFlow<List<VPNConfig>> = _configs.asStateFlow()

    private val _filteredConfigs = MutableStateFlow<List<VPNConfig>>(emptyList())
    val filteredConfigs: StateFlow<List<VPNConfig>> = _filteredConfigs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    init {
        loadConfigs()
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            configRepository.getAllConfigs().collect { configs ->
                _configs.value = configs
                _filteredConfigs.value = filterConfigs(configs, _searchQuery.value)
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        _filteredConfigs.value = filterConfigs(_configs.value, query)
    }

    fun importFromText(text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _importMessage.value = null
            try {
                val configs = ConfigParser.parseConfigs(text)
                var firstInsertedId: Long? = null
                configs.forEach { config ->
                    val id = configRepository.insertConfig(config)
                    if (firstInsertedId == null) firstInsertedId = id
                }
                firstInsertedId?.let { settingsRepository.setLastUsedConfigId(it) }
                _importMessage.value = if (configs.isEmpty()) {
                    "No valid config found"
                } else {
                    "Imported ${configs.size} config(s)"
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import config text")
                _importMessage.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _importMessage.value = null
            try {
                val response = withContext(Dispatchers.IO) {
                    java.net.URL(url).readText()
                }
                val configs = ConfigParser.parseConfigs(response)
                var firstInsertedId: Long? = null
                configs.forEach { config ->
                    val id = configRepository.insertConfig(config)
                    if (firstInsertedId == null) firstInsertedId = id
                }
                firstInsertedId?.let { settingsRepository.setLastUsedConfigId(it) }
                _importMessage.value = if (configs.isEmpty()) {
                    "No valid config found"
                } else {
                    "Imported ${configs.size} config(s)"
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import config URL")
                _importMessage.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectConfig(id: Long) {
        viewModelScope.launch {
            settingsRepository.setLastUsedConfigId(id)
        }
    }

    fun deleteConfig(config: VPNConfig) {
        viewModelScope.launch {
            configRepository.deleteConfig(config)
        }
    }

    fun renameConfig(id: Long, name: String) {
        viewModelScope.launch {
            configRepository.renameConfig(id, name)
        }
    }

    fun updateConfig(config: VPNConfig) {
        viewModelScope.launch {
            configRepository.updateConfig(config.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun togglePin(id: Long, isPinned: Boolean) {
        viewModelScope.launch {
            configRepository.updatePinned(id, isPinned)
        }
    }

    fun lockConfig(config: VPNConfig) {
        updateConfig(config.copy(isLocked = true))
    }

    fun exportConfig(config: VPNConfig): String? {
        if (config.isLocked) return null
        return exportConfigUseCase(config).getOrNull()
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }

    private fun filterConfigs(configs: List<VPNConfig>, query: String): List<VPNConfig> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return configs
        return configs.filter { config ->
            config.name.contains(cleanQuery, ignoreCase = true) ||
                config.address.contains(cleanQuery, ignoreCase = true) ||
                config.protocol.name.contains(cleanQuery, ignoreCase = true)
        }
    }
}

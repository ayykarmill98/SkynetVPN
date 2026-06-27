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

    private val _selectedConfigId = MutableStateFlow(-1L)
    val selectedConfigId: StateFlow<Long> = _selectedConfigId.asStateFlow()

    init {
        loadConfigs()
        observeSelectedConfig()
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
                val sourceText = text.trim()
                val importText = if (looksLikeRemoteConfigUrl(sourceText)) {
                    withContext(Dispatchers.IO) {
                        readRemoteText(sourceText)
                    }
                } else {
                    text
                }
                val configs = withContext(Dispatchers.Default) {
                    ConfigParser.parseConfigs(importText)
                }
                var firstInsertedId: Long? = null
                configs.forEach { config ->
                    val id = configRepository.insertConfig(config)
                    if (firstInsertedId == null) firstInsertedId = id
                }
                firstInsertedId?.let {
                    settingsRepository.setLastUsedConfigId(it)
                    _selectedConfigId.value = it
                }
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
                    readRemoteText(url)
                }
                val configs = withContext(Dispatchers.Default) {
                    ConfigParser.parseConfigs(response)
                }
                var firstInsertedId: Long? = null
                configs.forEach { config ->
                    val id = configRepository.insertConfig(config)
                    if (firstInsertedId == null) firstInsertedId = id
                }
                firstInsertedId?.let {
                    settingsRepository.setLastUsedConfigId(it)
                    _selectedConfigId.value = it
                }
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
            val selectedConfig = _configs.value.firstOrNull { it.id == id }
            val validationError = selectedConfig?.let { ConfigParser.getValidationError(it) }
            if (selectedConfig == null || validationError != null) {
                _importMessage.value = "Invalid config${validationError?.let { ": $it" }.orEmpty()}"
                return@launch
            }
            settingsRepository.setLastUsedConfigId(id)
            _selectedConfigId.value = id
            val selectedName = selectedConfig.name.ifBlank { "Unnamed" }
            _importMessage.value = "Selected $selectedName"
        }
    }

    fun deleteConfig(config: VPNConfig) {
        viewModelScope.launch {
            configRepository.deleteConfig(config)
            if (_selectedConfigId.value == config.id) {
                val nextConfig = _configs.value
                    .filterNot { it.id == config.id }
                    .filter { ConfigParser.getValidationError(it) == null }
                    .let { configs -> configs.firstOrNull { it.isPinned } ?: configs.firstOrNull() }
                val nextId = nextConfig?.id ?: -1L
                settingsRepository.setLastUsedConfigId(nextId)
                _selectedConfigId.value = nextId
                _importMessage.value = if (nextConfig == null) {
                    "No config selected"
                } else {
                    "Selected ${nextConfig.name.ifBlank { "Unnamed" }}"
                }
            }
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

    fun exportConfig(config: VPNConfig, protect: Boolean): String? {
        if (config.isLocked && !protect) return null
        return exportConfigUseCase(config, protect).getOrNull()
    }

    fun showMessage(message: String) {
        _importMessage.value = message
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

    private fun observeSelectedConfig() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _selectedConfigId.value = settings.lastUsedConfigId
            }
        }
    }

    private fun looksLikeRemoteConfigUrl(text: String): Boolean {
        if (text.lineSequence().count() != 1) return false
        return text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
    }

    private fun readRemoteText(url: String): String {
        val connection = java.net.URL(url).openConnection().apply {
            connectTimeout = 15000
            readTimeout = 30000
        }
        return connection.getInputStream().bufferedReader().use { it.readText() }
    }
}

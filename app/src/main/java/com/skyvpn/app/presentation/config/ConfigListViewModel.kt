package com.skyvpn.app.presentation.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.repository.VPNConfigRepository
import com.skyvpn.app.util.ConfigParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigListViewModel @Inject constructor(
    private val configRepository: VPNConfigRepository
) : ViewModel() {

    private val _configs = MutableStateFlow<List<VPNConfig>>(emptyList())
    val configs: StateFlow<List<VPNConfig>> = _configs.asStateFlow()

    private val _filteredConfigs = MutableStateFlow<List<VPNConfig>>(emptyList())
    val filteredConfigs: StateFlow<List<VPNConfig>> = _filteredConfigs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadConfigs()
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            configRepository.getAllConfigs().collect { configs ->
                _configs.value = configs
                _filteredConfigs.value = configs
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                _filteredConfigs.value = _configs.value
            } else {
                configRepository.searchConfigs(query).collect {
                    _filteredConfigs.value = it
                }
            }
        }
    }

    fun importFromText(text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            lines.forEach { line ->
                val config = ConfigParser.parseConfig(line)
                if (config != null) {
                    configRepository.insertConfig(config)
                }
            }
            _isLoading.value = false
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = java.net.URL(url).readText()
                val lines = response.lines().map { it.trim() }.filter { it.isNotEmpty() }
                lines.forEach { line ->
                    val config = ConfigParser.parseConfig(line)
                    if (config != null) {
                        configRepository.insertConfig(config)
                    }
                }
            } catch (e: Exception) {
                // handle error
            }
            _isLoading.value = false
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

    fun togglePin(id: Long, isPinned: Boolean) {
        viewModelScope.launch {
            configRepository.updatePinned(id, isPinned)
        }
    }
}

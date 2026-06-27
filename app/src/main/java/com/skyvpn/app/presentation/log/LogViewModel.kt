package com.skyvpn.app.presentation.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyvpn.app.domain.model.VPNLog
import com.skyvpn.app.domain.repository.VPNLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: VPNLogRepository
) : ViewModel() {

    private val _logs = MutableStateFlow<List<VPNLog>>(emptyList())
    val logs: StateFlow<List<VPNLog>> = _logs.asStateFlow()

    init {
        viewModelScope.launch {
            logRepository.getAllLogs().collect {
                _logs.value = it
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logRepository.clearLogs()
        }
    }
}

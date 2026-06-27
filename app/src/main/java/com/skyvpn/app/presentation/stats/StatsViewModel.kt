package com.skyvpn.app.presentation.stats

import androidx.lifecycle.ViewModel
import com.skyvpn.app.domain.model.ConnectionState
import com.skyvpn.app.domain.repository.ConnectionStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val connectionStateRepository: ConnectionStateRepository
) : ViewModel() {

    val connectionState = connectionStateRepository.connectionState

    fun updateState(state: ConnectionState) = connectionStateRepository.update(state)
}

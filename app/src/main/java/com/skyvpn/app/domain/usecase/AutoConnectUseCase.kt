package com.skyvpn.app.domain.usecase

import com.skyvpn.app.domain.model.ConnectionStatus
import com.skyvpn.app.domain.repository.SettingsRepository
import com.skyvpn.app.domain.repository.VPNConfigRepository
import javax.inject.Inject

class AutoConnectUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val configRepository: VPNConfigRepository
) {
    suspend operator fun invoke(): Long? {
        val autoConnect = settingsRepository.getAutoConnect()
        if (!autoConnect) return null

        val lastConfigId = settingsRepository.getLastUsedConfigId()
        if (lastConfigId > 0) {
            val config = configRepository.getConfigById(lastConfigId)
            if (config != null && !config.isExpired) {
                return lastConfigId
            }
        }

        return null
    }
}

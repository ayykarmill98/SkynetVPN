package com.skyvpn.app.domain.usecase

import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.repository.VPNConfigRepository
import com.skyvpn.app.util.ConfigParser
import javax.inject.Inject

class ImportConfigUseCase @Inject constructor(
    private val repository: VPNConfigRepository
) {
    suspend operator fun invoke(rawConfig: String): Result<Long> {
        return try {
            val config = ConfigParser.parseConfig(rawConfig)
                ?: return Result.failure(Exception("Failed to parse config"))
            val id = repository.insertConfig(config)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importMultiple(rawConfigs: String): Result<Int> {
        return try {
            val configs = ConfigParser.parseConfigs(rawConfigs)
            configs.forEach { config ->
                repository.insertConfig(config)
            }
            Result.success(configs.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

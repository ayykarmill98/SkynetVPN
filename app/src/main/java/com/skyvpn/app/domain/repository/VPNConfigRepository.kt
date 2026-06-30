package com.skyvpn.app.domain.repository

import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.model.VPNConfigSource
import kotlinx.coroutines.flow.Flow

interface VPNConfigRepository {
    fun getAllConfigs(): Flow<List<VPNConfig>>
    fun searchConfigs(query: String): Flow<List<VPNConfig>>
    suspend fun getConfigById(id: Long): VPNConfig?
    fun getConfigByIdFlow(id: Long): Flow<VPNConfig?>
    suspend fun insertConfig(config: VPNConfig): Long
    suspend fun insertConfigs(configs: List<VPNConfig>)
    suspend fun updateConfig(config: VPNConfig)
    suspend fun deleteConfig(config: VPNConfig)
    suspend fun deleteConfigById(id: Long)
    suspend fun deleteConfigsBySource(source: VPNConfigSource)
    suspend fun getConfigCount(): Int
    suspend fun updatePinned(id: Long, isPinned: Boolean)
    suspend fun renameConfig(id: Long, name: String)
}

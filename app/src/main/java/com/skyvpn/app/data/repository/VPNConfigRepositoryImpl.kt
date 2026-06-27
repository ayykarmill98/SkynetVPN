package com.skyvpn.app.data.repository

import com.skyvpn.app.data.local.VPNConfigDao
import com.skyvpn.app.data.local.toDomain
import com.skyvpn.app.data.local.toEntity
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.repository.VPNConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VPNConfigRepositoryImpl @Inject constructor(
    private val dao: VPNConfigDao
) : VPNConfigRepository {

    override fun getAllConfigs(): Flow<List<VPNConfig>> =
        dao.getAllConfigs().map { list -> list.map { it.toDomain() } }

    override fun searchConfigs(query: String): Flow<List<VPNConfig>> =
        dao.searchConfigs(query).map { list -> list.map { it.toDomain() } }

    override suspend fun getConfigById(id: Long): VPNConfig? =
        dao.getConfigById(id)?.toDomain()

    override fun getConfigByIdFlow(id: Long): Flow<VPNConfig?> =
        dao.getConfigByIdFlow(id).map { it?.toDomain() }

    override suspend fun insertConfig(config: VPNConfig): Long =
        dao.insertConfig(config.toEntity())

    override suspend fun insertConfigs(configs: List<VPNConfig>) =
        dao.insertConfigs(configs.map { it.toEntity() })

    override suspend fun updateConfig(config: VPNConfig) =
        dao.updateConfig(config.toEntity())

    override suspend fun deleteConfig(config: VPNConfig) =
        dao.deleteConfig(config.toEntity())

    override suspend fun deleteConfigById(id: Long) =
        dao.deleteConfigById(id)

    override suspend fun getConfigCount(): Int =
        dao.getConfigCount()

    override suspend fun updatePinned(id: Long, isPinned: Boolean) =
        dao.updatePinned(id, isPinned)

    override suspend fun renameConfig(id: Long, name: String) =
        dao.renameConfig(id, name)
}

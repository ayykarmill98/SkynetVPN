package com.skyvpn.app.data.repository

import com.skyvpn.app.data.local.VPNLogDao
import com.skyvpn.app.data.local.toDomain
import com.skyvpn.app.data.local.toEntity
import com.skyvpn.app.domain.model.VPNLog
import com.skyvpn.app.domain.repository.VPNLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VPNLogRepositoryImpl @Inject constructor(
    private val dao: VPNLogDao
) : VPNLogRepository {

    override fun getAllLogs(): Flow<List<VPNLog>> =
        dao.getAllLogs().map { list -> list.map { it.toDomain() } }

    override suspend fun insertLog(log: VPNLog) =
        dao.insertLog(log.toEntity())

    override suspend fun clearLogs() = dao.clearLogs()

    override suspend fun getLogCount(): Int = dao.getLogCount()
}

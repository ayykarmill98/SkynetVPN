package com.skyvpn.app.domain.repository

import com.skyvpn.app.domain.model.VPNLog
import kotlinx.coroutines.flow.Flow

interface VPNLogRepository {
    fun getAllLogs(): Flow<List<VPNLog>>
    suspend fun insertLog(log: VPNLog)
    suspend fun clearLogs()
    suspend fun getLogCount(): Int
}

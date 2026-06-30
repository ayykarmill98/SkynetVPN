package com.skyvpn.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VPNConfigDao {
    @Query("SELECT * FROM vpn_configs ORDER BY isPinned DESC, name ASC")
    fun getAllConfigs(): Flow<List<VPNConfigEntity>>

    @Query("SELECT * FROM vpn_configs WHERE id = :id")
    suspend fun getConfigById(id: Long): VPNConfigEntity?

    @Query("SELECT * FROM vpn_configs WHERE id = :id")
    fun getConfigByIdFlow(id: Long): Flow<VPNConfigEntity?>

    @Query("SELECT * FROM vpn_configs WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchConfigs(query: String): Flow<List<VPNConfigEntity>>

    @Query("SELECT * FROM vpn_configs WHERE isLocked = 0 ORDER BY name ASC")
    fun getUnlockedConfigs(): Flow<List<VPNConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: VPNConfigEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<VPNConfigEntity>)

    @Update
    suspend fun updateConfig(config: VPNConfigEntity)

    @Delete
    suspend fun deleteConfig(config: VPNConfigEntity)

    @Query("DELETE FROM vpn_configs WHERE id = :id")
    suspend fun deleteConfigById(id: Long)

    @Query("DELETE FROM vpn_configs WHERE source = :source")
    suspend fun deleteConfigsBySource(source: String)

    @Query("SELECT COUNT(*) FROM vpn_configs")
    suspend fun getConfigCount(): Int

    @Query("UPDATE vpn_configs SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: Long, isPinned: Boolean)

    @Query("UPDATE vpn_configs SET name = :name WHERE id = :id")
    suspend fun renameConfig(id: Long, name: String)
}

@Dao
interface VPNLogDao {
    @Query("SELECT * FROM vpn_logs ORDER BY timestamp DESC LIMIT 500")
    fun getAllLogs(): Flow<List<VPNLogEntity>>

    @Insert
    suspend fun insertLog(log: VPNLogEntity)

    @Query("DELETE FROM vpn_logs")
    suspend fun clearLogs()

    @Query("SELECT COUNT(*) FROM vpn_logs")
    suspend fun getLogCount(): Int
}

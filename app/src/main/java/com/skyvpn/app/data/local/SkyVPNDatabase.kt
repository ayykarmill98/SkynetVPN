package com.skyvpn.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VPNConfigEntity::class, VPNLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SkyVPNDatabase : RoomDatabase() {
    abstract fun vpnConfigDao(): VPNConfigDao
    abstract fun vpnLogDao(): VPNLogDao
}

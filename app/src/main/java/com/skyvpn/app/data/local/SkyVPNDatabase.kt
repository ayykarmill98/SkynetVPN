package com.skyvpn.app.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VPNConfigEntity::class, VPNLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SkyVPNDatabase : RoomDatabase() {
    abstract fun vpnConfigDao(): VPNConfigDao
    abstract fun vpnLogDao(): VPNLogDao

    companion object {
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vpn_configs ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE vpn_configs ADD COLUMN freeAccountId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

package com.skyvpn.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_configs")
data class VPNConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val protocol: String = "VMESS",
    val address: String = "",
    val port: Int = 443,
    val uuid: String = "",
    val alterId: Int = 0,
    val flow: String = "",
    val encryption: String = "",
    val transportType: String = "TCP",
    val host: String = "",
    val path: String = "",
    val sni: String = "",
    val security: String = "TLS",
    val publicKey: String = "",
    val shortId: String = "",
    val serverName: String = "",
    val fingerprint: String = "",
    val spiderX: String = "",
    val username: String = "",
    val password: String = "",
    val method: String = "",
    val rawConfig: String = "",
    val isLocked: Boolean = false,
    val lockPassword: String = "",
    val isExpired: Boolean = false,
    val expireDate: Long = 0,
    val watermark: String = "",
    val dnsRemote: String = "8.8.8.8",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vpn_logs")
data class VPNLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val tag: String = "",
    val message: String = ""
)

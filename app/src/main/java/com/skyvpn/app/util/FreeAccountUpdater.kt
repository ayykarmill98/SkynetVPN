package com.skyvpn.app.util

import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.model.VPNConfigSource
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FreeAccountUpdateResult(
    val configs: List<VPNConfig>,
    val skippedCount: Int
)

object FreeAccountUpdater {
    const val FreeAccountsUrl = "https://panel.skynetvpn.biz.id/download/apk/free_accounts.json"

    private const val ConnectTimeoutMs = 8000
    private const val ReadTimeoutMs = 15000

    fun fetchFreeAccounts(): FreeAccountUpdateResult {
        val connection = (URL(FreeAccountsUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = ConnectTimeoutMs
            readTimeout = ReadTimeoutMs
            setRequestProperty("Accept", "application/json")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseFreeAccountJson(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFreeAccountJson(body: String): FreeAccountUpdateResult {
        val root = JSONObject(body)
        val accounts = root.optJSONArray("accounts")
            ?: root.optJSONArray("configs")
            ?: JSONArray()

        val configs = mutableListOf<VPNConfig>()
        var skippedCount = 0

        for (index in 0 until accounts.length()) {
            val parsed = when (val item = accounts.opt(index)) {
                is JSONObject -> parseAccountObject(item, index)
                is String -> parseAccountString(item, index, "")
                else -> null
            }

            if (parsed == null) {
                skippedCount++
            } else {
                configs.add(parsed)
            }
        }

        return FreeAccountUpdateResult(configs, skippedCount)
    }

    private fun parseAccountObject(account: JSONObject, index: Int): VPNConfig? {
        val rawConfig = account.optString("config").ifBlank {
            account.optString("rawConfig").ifBlank {
                account.optString("url").ifBlank {
                    account.optString("link")
                }
            }
        }
        val name = account.optString("name")
        val id = account.optString("id").ifBlank {
            account.optString("accountId")
        }

        return parseAccountString(rawConfig, index, name, id)
    }

    private fun parseAccountString(
        rawConfig: String,
        index: Int,
        name: String,
        accountId: String = ""
    ): VPNConfig? {
        val cleanConfig = rawConfig.trim()
        if (cleanConfig.isEmpty()) return null

        val parsed = ConfigParser.parseConfig(cleanConfig) ?: return null
        val now = System.currentTimeMillis()
        val displayName = name.trim()
            .ifBlank { parsed.name }
            .ifBlank { "Akun Free ${index + 1}" }

        return parsed.copy(
            name = displayName,
            source = VPNConfigSource.FREE,
            freeAccountId = accountId.trim().ifBlank { "free-${index + 1}-${cleanConfig.hashCode()}" },
            isPinned = false,
            isLocked = false,
            rawConfig = cleanConfig,
            createdAt = now,
            updatedAt = now
        )
    }
}

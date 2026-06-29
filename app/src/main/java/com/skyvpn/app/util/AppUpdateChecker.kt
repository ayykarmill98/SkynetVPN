package com.skyvpn.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.skyvpn.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val message: String
)

object AppUpdateChecker {
    const val UpdateInfoUrl = "https://panel.skynetvpn.biz.id/download/apk/update.json"
    const val DefaultApkUrl = "https://panel.skynetvpn.biz.id/download/apk/skynetvpn.apk"

    private const val PreferencesName = "app_update_checker"
    private const val LastMondayCheckDateKey = "last_monday_check_date"
    private const val MondayJitterMinuteKey = "monday_jitter_minute"
    private const val MinutesPerDay = 24 * 60
    private const val ConnectTimeoutMs = 8000
    private const val ReadTimeoutMs = 8000

    suspend fun checkForMondayUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val today = LocalDate.now(ZoneId.systemDefault())
        val now = LocalTime.now(ZoneId.systemDefault())

        if (!isMondayCheckDue(appContext, today, now)) return@withContext null

        try {
            runCatching {
                fetchUpdateInfo()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            }.getOrNull()
        } finally {
            markMondayCheckDone(appContext, today)
        }
    }

    fun openDownload(context: Context, apkUrl: String = DefaultApkUrl): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.isSuccess
    }

    private fun fetchUpdateInfo(): AppUpdateInfo? {
        val connection = (URL(UpdateInfoUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = ConnectTimeoutMs
            readTimeout = ReadTimeoutMs
            setRequestProperty("Accept", "application/json")
        }

        return try {
            if (connection.responseCode !in 200..299) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val latestVersionCode = json.optInt("versionCode", -1)

            if (latestVersionCode <= 0) return null

            AppUpdateInfo(
                versionCode = latestVersionCode,
                versionName = json.optString("versionName", "Versi $latestVersionCode"),
                apkUrl = json.optString("apkUrl", DefaultApkUrl).ifBlank { DefaultApkUrl },
                message = json.optString("message", "Update terbaru tersedia")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isMondayCheckDue(
        context: Context,
        today: LocalDate,
        now: LocalTime
    ): Boolean {
        if (today.dayOfWeek != DayOfWeek.MONDAY) return false

        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        if (preferences.getString(LastMondayCheckDateKey, null) == today.toString()) return false

        val jitterMinute = getOrCreateMondayJitterMinute(preferences)
        val currentMinute = now.hour * 60 + now.minute
        return currentMinute >= jitterMinute
    }

    private fun markMondayCheckDone(context: Context, today: LocalDate) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(LastMondayCheckDateKey, today.toString())
            .apply()
    }

    private fun getOrCreateMondayJitterMinute(
        preferences: android.content.SharedPreferences
    ): Int {
        if (preferences.contains(MondayJitterMinuteKey)) {
            return preferences.getInt(MondayJitterMinuteKey, 0)
        }

        val jitterMinute = Random.nextInt(MinutesPerDay)
        preferences.edit()
            .putInt(MondayJitterMinuteKey, jitterMinute)
            .apply()
        return jitterMinute
    }
}

package com.skyvpn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.skyvpn.app.service.AutoReconnectWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SkyVPNApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannels()
        AutoReconnectWorker.enqueue(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val vpnChannel = NotificationChannel(
            VPN_CHANNEL_ID,
            "VPN Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VPN connection status notifications"
            setShowBadge(false)
        }

        val reconnectChannel = NotificationChannel(
            RECONNECT_CHANNEL_ID,
            "Auto Reconnect",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Auto reconnect status notifications"
            setShowBadge(false)
        }

        manager.createNotificationChannel(vpnChannel)
        manager.createNotificationChannel(reconnectChannel)
    }

    companion object {
        const val VPN_CHANNEL_ID = "vpn_status_channel"
        const val RECONNECT_CHANNEL_ID = "reconnect_channel"
        const val VPN_NOTIFICATION_ID = 1
        const val RECONNECT_NOTIFICATION_ID = 2

        lateinit var instance: SkyVPNApp
            private set
    }
}

package com.skyvpn.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import timber.log.Timber

class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == android.net.ConnectivityManager.CONNECTIVITY_ACTION) {
            Timber.d("Network connectivity changed")
            if (VpnService.prepare(context) == null) {
                val serviceIntent = Intent(context, SkyVPNService::class.java).apply {
                    action = SkyVPNService.ACTION_AUTO_CONNECT
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}

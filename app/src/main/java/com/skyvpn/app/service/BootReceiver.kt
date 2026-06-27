package com.skyvpn.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skyvpn.app.domain.usecase.AutoConnectUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var autoConnectUseCase: AutoConnectUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val configId = autoConnectUseCase()
                    if (configId != null && android.net.VpnService.prepare(context) == null) {
                        val serviceIntent = Intent(context, SkyVPNService::class.java).apply {
                            action = SkyVPNService.ACTION_CONNECT
                            putExtra(SkyVPNService.EXTRA_CONFIG_ID, configId)
                        }
                        context.startForegroundService(serviceIntent)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

package com.skyvpn.app.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skyvpn.app.domain.repository.SettingsRepository
import com.skyvpn.app.domain.usecase.AutoConnectUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoReconnectWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val autoConnectUseCase: AutoConnectUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.getAutoReconnect()) return Result.success()
        val configId = autoConnectUseCase() ?: return Result.success()
        if (VpnService.prepare(applicationContext) != null) return Result.success()

        val intent = Intent(applicationContext, SkyVPNService::class.java).apply {
            action = SkyVPNService.ACTION_CONNECT
            putExtra(SkyVPNService.EXTRA_CONFIG_ID, configId)
        }
        applicationContext.startForegroundService(intent)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "skynet_auto_reconnect"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoReconnectWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}

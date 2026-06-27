package com.skyvpn.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skyvpn.app.domain.repository.SettingsRepository
import com.skyvpn.app.domain.usecase.AutoConnectUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutoReconnectWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val autoConnectUseCase: AutoConnectUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
        }
    }
}

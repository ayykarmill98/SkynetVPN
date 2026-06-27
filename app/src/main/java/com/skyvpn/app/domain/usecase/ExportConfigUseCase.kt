package com.skyvpn.app.domain.usecase

import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.util.ConfigExporter
import javax.inject.Inject

class ExportConfigUseCase @Inject constructor() {

    operator fun invoke(config: VPNConfig, protect: Boolean = false): Result<String> {
        return try {
            val exported = if (protect) {
                ConfigExporter.exportProtected(config)
            } else {
                ConfigExporter.exportStandard(config)
            }
            Result.success(exported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

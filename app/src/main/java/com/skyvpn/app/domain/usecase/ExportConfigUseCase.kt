package com.skyvpn.app.domain.usecase

import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.util.ConfigExporter
import javax.inject.Inject

class ExportConfigUseCase @Inject constructor() {

    operator fun invoke(config: VPNConfig): Result<String> {
        return try {
            val exported = when (config.protocol) {
                com.skyvpn.app.domain.model.VPNProtocol.VMESS -> ConfigExporter.exportToVMess(config)
                com.skyvpn.app.domain.model.VPNProtocol.VLESS -> ConfigExporter.exportToVLESS(config)
                com.skyvpn.app.domain.model.VPNProtocol.TROJAN -> ConfigExporter.exportToTrojan(config)
                com.skyvpn.app.domain.model.VPNProtocol.SHADOWSOCKS -> ConfigExporter.exportToSS(config)
                else -> config.rawConfig
            }
            Result.success(exported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

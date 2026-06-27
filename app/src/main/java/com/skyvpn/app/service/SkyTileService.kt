package com.skyvpn.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.skyvpn.app.domain.model.ConnectionStatus
import com.skyvpn.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SkyTileService : TileService() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = android.content.Intent(this, SkyVPNService::class.java)
        val isConnected = qsTile?.state == Tile.STATE_ACTIVE
        if (isConnected) {
            intent.action = SkyVPNService.ACTION_DISCONNECT
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val lastConfigId = settingsRepository.getLastUsedConfigId()
                if (lastConfigId > 0) {
                    intent.action = SkyVPNService.ACTION_CONNECT
                    intent.putExtra(SkyVPNService.EXTRA_CONFIG_ID, lastConfigId)
                    startService(intent)
                }
            }
            return
        }
        startService(intent)
    }

    private fun updateTile() {
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}

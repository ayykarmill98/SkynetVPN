package com.skyvpn.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("Connection") {
                SettingsSwitch(
                    title = "Auto Connect",
                    subtitle = "Connect automatically when app opens",
                    checked = settings.autoConnect,
                    onCheckedChange = { viewModel.updateAutoConnect(it) }
                )
                SettingsSwitch(
                    title = "Auto Reconnect",
                    subtitle = "Automatically reconnect when connection is lost",
                    checked = settings.autoReconnect,
                    onCheckedChange = { viewModel.updateAutoReconnect(it) }
                )
                SettingsSwitch(
                    title = "Kill Switch",
                    subtitle = "Block all traffic when VPN disconnects",
                    checked = settings.killSwitch,
                    onCheckedChange = { viewModel.updateKillSwitch(it) }
                )
            }

            SettingsSection("Reconnect") {
                SettingsSlider(
                    title = "Retry Delay",
                    value = settings.retryDelay.toFloat(),
                    onValueChange = { viewModel.updateRetryDelay(it.toLong()) },
                    valueRange = 500f..10000f,
                    steps = 18,
                    valueLabel = "${settings.retryDelay}ms"
                )
                SettingsSlider(
                    title = "Max Retry Count",
                    value = settings.maxRetryCount.toFloat(),
                    onValueChange = { viewModel.updateMaxRetryCount(it.toInt()) },
                    valueRange = 1f..50f,
                    steps = 48,
                    valueLabel = "${settings.maxRetryCount}"
                )
            }

            SettingsSection("Network") {
                SettingsSwitch(
                    title = "IPv4",
                    subtitle = "Enable IPv4 tunnel address and routes",
                    checked = settings.enableIPv4,
                    onCheckedChange = { viewModel.updateEnableIPv4(it) }
                )
                SettingsSwitch(
                    title = "IPv6",
                    subtitle = "Enable IPv6 tunnel address and routes",
                    checked = settings.enableIPv6,
                    onCheckedChange = { viewModel.updateEnableIPv6(it) }
                )
                SettingsSwitch(
                    title = "UDP",
                    subtitle = "Allow UDP forwarding through the tunnel",
                    checked = settings.enableUDP,
                    onCheckedChange = { viewModel.updateEnableUDP(it) }
                )
                SettingsSwitch(
                    title = "TCP",
                    subtitle = "Allow TCP forwarding through the tunnel",
                    checked = settings.enableTCP,
                    onCheckedChange = { viewModel.updateEnableTCP(it) }
                )
                SettingsSwitch(
                    title = "FakeDNS",
                    subtitle = "Use FakeDNS mode when supported by the core",
                    checked = settings.enableFakeDNS,
                    onCheckedChange = { viewModel.updateEnableFakeDNS(it) }
                )
                SettingsSwitch(
                    title = "Bypass LAN",
                    subtitle = "Exclude local network traffic from VPN",
                    checked = settings.bypassLAN,
                    onCheckedChange = { viewModel.updateBypassLAN(it) }
                )
                SettingsSwitch(
                    title = "Per-App VPN",
                    subtitle = "Route only selected apps through VPN",
                    checked = settings.perAppVPN,
                    onCheckedChange = { viewModel.updatePerAppVPN(it) }
                )
            }

            SettingsSection("Advanced") {
                SettingsSlider(
                    title = "MTU",
                    value = settings.mtu.toFloat(),
                    onValueChange = { viewModel.updateMTU(it.toInt()) },
                    valueRange = 1200f..1500f,
                    steps = 5,
                    valueLabel = "${settings.mtu}"
                )
            }

            SettingsSection("Appearance") {
                SettingsSwitch(
                    title = "Dark Theme",
                    subtitle = when (settings.isDarkTheme) {
                        true -> "Dark mode enabled"
                        false -> "Light mode enabled"
                        null -> "Follow system"
                    },
                    checked = settings.isDarkTheme == true,
                    onCheckedChange = { viewModel.updateDarkTheme(it) }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

package com.skyvpn.app.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skyvpn.app.presentation.about.AboutScreen
import com.skyvpn.app.presentation.config.ConfigListScreen
import com.skyvpn.app.presentation.config.ConfigListViewModel
import com.skyvpn.app.presentation.home.HomeScreen
import com.skyvpn.app.presentation.home.HomeViewModel
import com.skyvpn.app.presentation.log.LogScreen
import com.skyvpn.app.presentation.log.LogViewModel
import com.skyvpn.app.presentation.settings.SettingsScreen
import com.skyvpn.app.presentation.settings.SettingsViewModel
import com.skyvpn.app.presentation.splash.SplashScreen
import com.skyvpn.app.presentation.stats.StatsScreen
import com.skyvpn.app.presentation.stats.StatsViewModel
import com.skyvpn.app.presentation.theme.SkyVPNTheme
import com.skyvpn.app.service.SkyVPNService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingConnectConfigId: Long? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingConnectConfigId?.let { connectVPN(it) }
        }
        pendingConnectConfigId = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SkyVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onNavigateHome = {
                                    navController.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            val viewModel: HomeViewModel = hiltViewModel()
                            val configs by viewModel.configs.collectAsState()
                            val lastUsedConfigId by viewModel.lastUsedConfigId.collectAsState()
                            HomeScreen(
                                connectionState = viewModel.connectionState.collectAsState().value,
                                configs = configs,
                                lastUsedConfigId = lastUsedConfigId,
                                onConnect = { configId -> handleConnect(configId) },
                                onDisconnect = { handleDisconnect() },
                                onNavigateConfigs = { navController.navigate("configs") },
                                onNavigateSettings = { navController.navigate("settings") },
                                onNavigateLogs = { navController.navigate("logs") },
                                onNavigateStats = { navController.navigate("stats") },
                                onNavigateAbout = { navController.navigate("about") }
                            )
                        }
                        composable("configs") {
                            val viewModel: ConfigListViewModel = hiltViewModel()
                            ConfigListScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            val viewModel: SettingsViewModel = hiltViewModel()
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("logs") {
                            val viewModel: LogViewModel = hiltViewModel()
                            LogScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("stats") {
                            val viewModel: StatsViewModel = hiltViewModel()
                            StatsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("about") {
                            AboutScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleConnect(configId: Long) {
        val prepareIntent = android.net.VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingConnectConfigId = configId
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            connectVPN(configId)
        }
    }

    private fun connectVPN(configId: Long = -1) {
        val intent = Intent(this, SkyVPNService::class.java).apply {
            action = SkyVPNService.ACTION_CONNECT
            putExtra(SkyVPNService.EXTRA_CONFIG_ID, configId)
        }
        startService(intent)
    }

    private fun handleDisconnect() {
        val intent = Intent(this, SkyVPNService::class.java).apply {
            action = SkyVPNService.ACTION_DISCONNECT
        }
        startService(intent)
    }
}

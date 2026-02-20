package com.thisux.droidclaw

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.thisux.droidclaw.connection.ConnectionService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thisux.droidclaw.ui.components.PermissionStatusBar
import com.thisux.droidclaw.model.ConnectionState
import com.thisux.droidclaw.ui.screens.HomeScreen
import com.thisux.droidclaw.ui.screens.LogsScreen
import com.thisux.droidclaw.ui.screens.OnboardingScreen
import com.thisux.droidclaw.ui.screens.SettingsScreen
import com.thisux.droidclaw.ui.theme.DroidClawTheme
import com.thisux.droidclaw.ui.theme.InstrumentSerif
import com.thisux.droidclaw.ui.theme.StatusRed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

sealed class Screen(val route: String, val label: String) {
    data object Home : Screen("home", "Home")
    data object Settings : Screen("settings", "Settings")
    data object Logs : Screen("logs", "Logs")
    data object Onboarding : Screen("onboarding", "Onboarding")
}

class MainActivity : ComponentActivity() {
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled — user can tap overlay pill again
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidClawTheme {
                MainNavigation()
            }
        }
        if (intent?.getBooleanExtra("request_audio_permission", false) == true) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        autoConnectIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("request_audio_permission", false)) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onResume() {
        super.onResume()
        val service = ConnectionService.instance ?: return
        if (Settings.canDrawOverlays(this)) {
            service.overlay?.show()
        }
    }

    private fun autoConnectIfNeeded() {
        if (ConnectionService.connectionState.value != com.thisux.droidclaw.model.ConnectionState.Disconnected) return
        val app = application as DroidClawApp
        lifecycleScope.launch {
            val apiKey = app.settingsStore.apiKey.first()
            if (apiKey.isNotBlank()) {
                val intent = Intent(this@MainActivity, ConnectionService::class.java).apply {
                    action = ConnectionService.ACTION_CONNECT
                }
                startForegroundService(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as DroidClawApp
    val hasOnboarded by app.settingsStore.hasOnboarded.collectAsState(initial = true)

    val navController = rememberNavController()
    val bottomNavScreens = listOf(Screen.Home, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showChrome = currentRoute != Screen.Onboarding.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showChrome) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "DroidClaw",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = InstrumentSerif
                            )
                        )
                    },
                    actions = {
                        PermissionStatusBar(
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        IconButton(onClick = {
                            navController.navigate(Screen.Logs.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = "Logs"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    when (screen) {
                                        is Screen.Home -> Icons.Filled.Home
                                        is Screen.Settings -> Icons.Filled.Settings
                                        else -> Icons.Filled.Home
                                    },
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val startDestination = if (hasOnboarded) Screen.Home.route else Screen.Onboarding.route

        val connectionState by ConnectionService.connectionState.collectAsState()
        val errorMessage by ConnectionService.errorMessage.collectAsState()

        Column(modifier = Modifier.padding(innerPadding)) {
            if (showChrome && connectionState == ConnectionState.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusRed.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = errorMessage ?: "Connection error",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed
                    )
                }
            }

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f)
            ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Logs.route) { LogsScreen() }
        }
        }
    }
}

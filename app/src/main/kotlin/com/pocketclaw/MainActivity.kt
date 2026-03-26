package com.pocketclaw

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketclaw.service.AgentForegroundService
import com.pocketclaw.ui.dashboard.DashboardScreen
import com.pocketclaw.ui.onboarding.PermissionOnboardingScreen
import com.pocketclaw.ui.onboarding.PermissionOnboardingViewModel
import com.pocketclaw.ui.settings.SettingsScreen
import com.pocketclaw.ui.terminal.TerminalScreen
import com.pocketclaw.ui.theme.PocketClawTheme
import dagger.hilt.android.AndroidEntryPoint

private object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val TERMINAL = "terminal"
    const val SETTINGS = "settings"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionViewModel: PermissionOnboardingViewModel by viewModels()

        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Notify ViewModel so the permission row turns green
                permissionViewModel.onMediaProjectionGranted()

                // Forward the token to AgentForegroundService
                startForegroundService(
                    Intent(this, AgentForegroundService::class.java).apply {
                        action = AgentForegroundService.ACTION_MEDIA_PROJECTION_GRANTED
                        putExtra(
                            AgentForegroundService.EXTRA_MEDIA_PROJECTION_RESULT_CODE,
                            result.resultCode
                        )
                        putExtra(
                            AgentForegroundService.EXTRA_MEDIA_PROJECTION_DATA,
                            result.data
                        )
                    }
                )
            }
        }

        setContent {
            PocketClawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PocketClawApp(
                        onRequestMediaProjection = {
                            val pm = getSystemService(MediaProjectionManager::class.java)
                            mediaProjectionLauncher.launch(pm.createScreenCaptureIntent())
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PocketClawApp(
    onRequestMediaProjection: () -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            PermissionOnboardingScreen(
                onAllPermissionsGranted = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onRequestMediaProjection = onRequestMediaProjection,
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToTerminal = {
                    navController.navigate(Routes.TERMINAL)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(Routes.TERMINAL) {
            TerminalScreen(
                onNavigateToDashboard = {
                    navController.navigateUp()
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
            )
        }
    }
}


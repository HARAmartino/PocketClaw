package com.pocketclaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketclaw.ui.dashboard.DashboardScreen
import com.pocketclaw.ui.onboarding.PermissionOnboardingScreen
import com.pocketclaw.ui.terminal.TerminalScreen
import com.pocketclaw.ui.theme.PocketClawTheme
import dagger.hilt.android.AndroidEntryPoint

private object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val TERMINAL = "terminal"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketClawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PocketClawApp()
                }
            }
        }
    }
}

@Composable
fun PocketClawApp() {
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
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToTerminal = {
                    navController.navigate(Routes.TERMINAL)
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
    }
}


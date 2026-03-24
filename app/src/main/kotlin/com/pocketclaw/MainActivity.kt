package com.pocketclaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pocketclaw.ui.theme.PocketClawTheme
import dagger.hilt.android.AndroidEntryPoint

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
    // Navigation graph — onboarding → dashboard
    // Full implementation in subsequent phases
    com.pocketclaw.ui.onboarding.PermissionOnboardingScreen(
        onAllPermissionsGranted = { /* navigate to Dashboard */ },
    )
}

package com.pocketclaw.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketclaw.R

/**
 * Permission onboarding screen shown on first launch and accessible from Settings.
 * Displays status of all required permissions and provides deep-link "Grant →" buttons.
 *
 * Required permissions (agent MUST NOT start until all are granted):
 * 1. AccessibilityService
 * 2. NotificationListenerService
 * 3. SYSTEM_ALERT_WINDOW (overlay permission)
 * 4. FOREGROUND_SERVICE_MEDIA_PROJECTION
 * 5. Battery Optimization Exemption (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
 */
@Composable
fun PermissionOnboardingScreen(
    onAllPermissionsGranted: () -> Unit,
    onRequestMediaProjection: () -> Unit = {},
    viewModel: PermissionOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.permissionState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.allGranted) {
        onAllPermissionsGranted()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_description),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = stringResource(R.string.permission_accessibility_label),
                isGranted = state.accessibilityGranted,
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )

            PermissionRow(
                label = stringResource(R.string.permission_notification_listener_label),
                isGranted = state.notificationListenerGranted,
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )

            PermissionRow(
                label = stringResource(R.string.permission_overlay_label),
                isGranted = state.overlayGranted,
                onGrant = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )

            PermissionRow(
                label = stringResource(R.string.permission_media_projection_label),
                isGranted = state.mediaProjectionGranted,
                onGrant = onRequestMediaProjection,
            )

            PermissionRow(
                label = stringResource(R.string.permission_battery_optimization_label),
                isGranted = state.batteryOptimizationExempt,
                onGrant = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.onboarding_oem_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!isGranted) {
                Button(onClick = onGrant) {
                    Text(stringResource(R.string.onboarding_grant_button))
                }
            }
        }
    }
}

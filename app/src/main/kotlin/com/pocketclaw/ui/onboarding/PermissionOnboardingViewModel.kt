package com.pocketclaw.ui.onboarding

import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketclaw.service.AgentAccessibilityService
import com.pocketclaw.service.AgentNotificationListenerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionState(
    val accessibilityGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val mediaProjectionGranted: Boolean = false,
    val batteryOptimizationExempt: Boolean = false,
) {
    val allGranted: Boolean get() =
        accessibilityGranted &&
            notificationListenerGranted &&
            overlayGranted &&
            mediaProjectionGranted &&
            batteryOptimizationExempt
}

@HiltViewModel
class PermissionOnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                refreshPermissions()
                delay(1_000L)
            }
        }
    }

    fun refreshPermissions() {
        _permissionState.update {
            PermissionState(
                accessibilityGranted = isAccessibilityEnabled(),
                notificationListenerGranted = isNotificationListenerEnabled(),
                overlayGranted = android.provider.Settings.canDrawOverlays(context),
                mediaProjectionGranted = it.mediaProjectionGranted, // Set by Activity result
                batteryOptimizationExempt = isBatteryOptimizationExempt(),
            )
        }
    }

    fun onMediaProjectionGranted() {
        _permissionState.update { it.copy(mediaProjectionGranted = true) }
    }

    fun requestMediaProjection() {
        // Signal to Activity to launch MediaProjectionManager.createScreenCaptureIntent()
        // Handled at Activity level via callback
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = ComponentName(context, AgentAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.contains(service.flattenToString())
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(context, AgentNotificationListenerService::class.java)
        val flat = cn.flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.contains(flat)
    }

    private fun isBatteryOptimizationExempt(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

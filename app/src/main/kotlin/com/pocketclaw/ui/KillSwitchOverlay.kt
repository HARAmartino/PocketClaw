package com.pocketclaw.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pocketclaw.R
import com.pocketclaw.agent.orchestrator.AgentOrchestrator
import com.pocketclaw.service.AgentForegroundService

/**
 * Always-on-top Kill Switch FAB displayed via [WindowManager].
 *
 * Security properties:
 * - Uses TYPE_APPLICATION_OVERLAY with FLAG_NOT_FOCUSABLE to prevent accessibility services
 *   from targeting it by focus.
 * - Hard-coded in [ActionValidator]'s Hard Deny list: any LLM action targeting this view
 *   is permanently blocked.
 * - Cannot be hidden, moved, or dismissed by any agent action or LLM output.
 *
 * On press: cancels root CoroutineScope, disables AccessibilityService, releases WakeLock,
 * logs KILL_SWITCH_ACTIVATED.
 */
class KillSwitchOverlay(
    private val context: Context,
    private val orchestrator: AgentOrchestrator,
    private val service: AgentForegroundService,
    /** Called after the root scope and wake lock are released. Use to disable the AccessibilityService. */
    private val onKillSwitchActivated: (() -> Unit)? = null,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: ComposeView? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        x = 16
        y = 64
    }

    fun show(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (overlayView != null) return

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner as? androidx.savedstate.SavedStateRegistryOwner)
            setContent {
                KillSwitchFab(onClick = ::onKillSwitchPressed)
            }
        }

        overlayView = view
        windowManager.addView(view, layoutParams)
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun onKillSwitchPressed() {
        android.util.Log.w("KillSwitch", "KILL_SWITCH_ACTIVATED: User pressed Kill Switch FAB.")
        orchestrator.killSwitch()
        service.releaseWakeLock()
        onKillSwitchActivated?.invoke()
    }
}

@Composable
private fun KillSwitchFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        containerColor = Color.Red,
        contentColor = Color.White,
    ) {
        Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = stringResource(R.string.kill_switch_content_description),
        )
    }
}

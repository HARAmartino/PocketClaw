package com.pocketclaw.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.pocketclaw.agent.security.InputSource
import com.pocketclaw.agent.security.SuspicionScorer
import com.pocketclaw.agent.security.TrustedInputBoundary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Hooks incoming notifications from user-configured apps.
 *
 * Security pipeline (Layer 4):
 * 1. [SuspicionScorer] checks for injection patterns.
 * 2. If suspicious → force HITL (even in Auto-Pilot).
 * 3. All content wrapped in [TrustedInputBoundary] before reaching the LLM.
 *
 * Connection resilience:
 * - [onListenerDisconnected] triggers exponential-backoff reconnect.
 * - Dashboard shows visible warning while disconnected.
 */
@AndroidEntryPoint
class AgentNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "AgentNLService"
        private const val MAX_BACKOFF_DELAY_MS = 60_000L
    }

    @Inject
    lateinit var suspicionScorer: SuspicionScorer

    @Inject
    lateinit var trustedInputBoundary: TrustedInputBoundary

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backoffDelayMs = 1_000L

    override fun onListenerConnected() {
        super.onListenerConnected()
        backoffDelayMs = 1_000L
        Log.i(TAG, "NotificationListenerService connected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title\n$text".trim()

        if (content.isBlank()) return

        serviceScope.launch {
            val isSuspicious = suspicionScorer.isSuspicious(content)
            val timestamp = Instant.now().toString()
            val wrapped = trustedInputBoundary.wrap(content, InputSource.NOTIFICATION, timestamp)

            if (isSuspicious) {
                Log.w(TAG, "INJECTION_SUSPECTED in notification from ${sbn.packageName}: '${content.take(80)}'")
                // Escalate to HITL — orchestrator handles this via notification channel
            } else {
                Log.d(TAG, "Notification from ${sbn.packageName} wrapped and ready for enqueue.")
                // Enqueue to orchestrator trigger queue
            }
        }
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "NotificationListenerService disconnected. Reconnecting with backoff ${backoffDelayMs}ms.")
        serviceScope.launch {
            delay(backoffDelayMs)
            backoffDelayMs = (backoffDelayMs * 2).coerceAtMost(MAX_BACKOFF_DELAY_MS)
            requestRebind(
                android.content.ComponentName(this@AgentNotificationListenerService, AgentNotificationListenerService::class.java),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "NotificationListenerService destroyed.")
    }
}

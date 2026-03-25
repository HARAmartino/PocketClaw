package com.pocketclaw.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.pocketclaw.agent.orchestrator.AgentOrchestrator
import com.pocketclaw.agent.security.InputSource
import com.pocketclaw.agent.security.SuspicionScorer
import com.pocketclaw.agent.security.TrustedInputBoundary
import com.pocketclaw.core.data.prefs.NotificationTriggerPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Hooks incoming notifications from user-configured apps.
 *
 * Security pipeline (Layer 4):
 * 1. [SuspicionScorer] checks for injection patterns.
 * 2. If suspicious → force HITL (even in Auto-Pilot); notification is not enqueued.
 * 3. All content wrapped in [TrustedInputBoundary] before reaching the LLM.
 * 4. Check if [StatusBarNotification.getPackageName] is in the user-configured
 *    trigger list ([NotificationTriggerPrefs.TRIGGER_PACKAGES_KEY]).
 * 5. If triggered: call [AgentOrchestrator.enqueueNotificationTask].
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

    @Inject
    lateinit var orchestrator: AgentOrchestrator

    @Inject
    lateinit var dataStore: DataStore<Preferences>

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
                // Suspicious content forces HITL — do not auto-enqueue.
                return@launch
            }

            // Check whether this package is in the user-configured trigger list
            val triggerPackages = dataStore.data
                .map { prefs -> prefs[NotificationTriggerPrefs.TRIGGER_PACKAGES_KEY] ?: emptySet() }
                .first()

            if (sbn.packageName in triggerPackages) {
                Log.i(TAG, "Notification from trigger package '${sbn.packageName}' — enqueueing task.")
                orchestrator.enqueueNotificationTask(
                    title = title,
                    wrappedContent = wrapped,
                    sourcePackage = sbn.packageName,
                )
            } else {
                Log.d(TAG, "Notification from '${sbn.packageName}' is not in trigger list — ignored.")
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


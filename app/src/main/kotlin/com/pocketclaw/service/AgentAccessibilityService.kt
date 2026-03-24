package com.pocketclaw.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.pocketclaw.agent.accessibility.AccessibilityTreeCompressor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PocketClaw's accessibility service.
 *
 * Responsibilities:
 * - Provides [AccessibilityTreeCompressor] access to the live UI tree.
 * - Detects Hard-Deny apps entering the foreground via [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED].
 * - Self-disables when the Kill Switch is activated.
 *
 * All node traversal is dispatched to [Dispatchers.IO] via [serviceScope].
 */
@AndroidEntryPoint
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentA11yService"
    }

    @Inject
    lateinit var treeCompressor: AccessibilityTreeCompressor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        Log.i(TAG, "AccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            // Hard-Deny app foreground detection — handled by orchestrator listener
            Log.d(TAG, "Window state changed: $packageName")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "AccessibilityService destroyed.")
    }

    /**
     * Captures and compresses the current accessibility tree.
     * Called by [com.pocketclaw.agent.orchestrator.AgentOrchestrator] before each LLM call.
     */
    fun captureCurrentScreen(
        onResult: (com.pocketclaw.agent.llm.schema.CompressedDomTree) -> Unit,
    ) {
        val root = rootInActiveWindow ?: return
        serviceScope.launch {
            val compressed = treeCompressor.compress(root)
            onResult(compressed)
        }
    }

    /** Disables the service — called by the Kill Switch. */
    fun selfDisable() {
        Log.w(TAG, "Self-disabling accessibility service (Kill Switch).")
        disableSelf()
    }
}

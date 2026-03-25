package com.pocketclaw.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketclaw.agent.accessibility.AccessibilityTreeCompressor
import com.pocketclaw.agent.accessibility.ActionResult
import com.pocketclaw.agent.llm.schema.AccessibilityActionType
import com.pocketclaw.agent.llm.schema.LlmAction
import com.pocketclaw.agent.llm.schema.NodeBounds
import com.pocketclaw.agent.orchestrator.AgentOrchestrator
import com.pocketclaw.agent.validator.ActionValidatorImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * PocketClaw's accessibility service.
 *
 * Responsibilities:
 * - Provides [AccessibilityTreeCompressor] access to the live UI tree.
 * - Executes [LlmAction]s against the live UI via [executeAction].
 * - Detects Hard-Deny apps entering the foreground via [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED].
 * - Self-disables when the Kill Switch is activated.
 *
 * Memory-safety rules for node access (CRITICAL):
 * - Every [AccessibilityNodeInfo] obtained from the framework is recycled in a try-finally block.
 * - Accessibility actions are always dispatched on [Dispatchers.Main].
 */
@AndroidEntryPoint
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentA11yService"
        private const val SWIPE_DURATION_MS = 300L
    }

    @Inject
    lateinit var treeCompressor: AccessibilityTreeCompressor

    @Inject
    lateinit var orchestrator: AgentOrchestrator

    @Inject
    lateinit var serviceState: AgentServiceState

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
            serviceState.setForegroundPackage(packageName)
            Log.d(TAG, "Window state changed: $packageName")

            if (packageName in ActionValidatorImpl.HARD_DENY_PACKAGES) {
                Log.w(TAG, "Hard-deny app in foreground: $packageName — pausing agent.")
                serviceScope.launch {
                    orchestrator.pauseForHardDenyApp(packageName)
                }
            }
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
     * Called by [AgentOrchestrator] before each LLM call.
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

    /**
     * Executes an [LlmAction] using the Android Accessibility API.
     *
     * Node-lookup order:
     * 1. [LlmAction.targetNodeId] via [AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId]
     * 2. Content-description hint via [AccessibilityNodeInfo.findAccessibilityNodeInfosByText]
     *    (derived from the last `/`-separated segment of [LlmAction.targetNodeId])
     * 3. Bounds match via [LlmAction.targetBounds]
     *
     * Every [AccessibilityNodeInfo] obtained from the framework is recycled in a
     * try-finally block, regardless of the execution path.
     *
     * All API calls that require the main thread are dispatched on [Dispatchers.Main].
     *
     * @return [ActionResult.Success], [ActionResult.NodeNotFound], or [ActionResult.ExecutionFailed].
     */
    suspend fun executeAction(action: LlmAction): ActionResult = withContext(Dispatchers.Main) {
        try {
            when (action.actionType) {
                AccessibilityActionType.PRESS_BACK -> {
                    if (performGlobalAction(GLOBAL_ACTION_BACK)) ActionResult.Success
                    else ActionResult.ExecutionFailed("GLOBAL_ACTION_BACK returned false")
                }
                AccessibilityActionType.PRESS_HOME -> {
                    if (performGlobalAction(GLOBAL_ACTION_HOME)) ActionResult.Success
                    else ActionResult.ExecutionFailed("GLOBAL_ACTION_HOME returned false")
                }
                AccessibilityActionType.SWIPE -> executeSwipe(action)
                else -> executeNodeAction(action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action ${action.actionType} threw an exception: ${e.message}", e)
            ActionResult.ExecutionFailed(e.message ?: "Unknown error")
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun executeSwipe(action: LlmAction): ActionResult {
        val b = action.targetBounds
        val path = Path().apply {
            moveTo(b.left.toFloat(), b.top.toFloat())
            lineTo(b.right.toFloat(), b.bottom.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()
        return if (dispatchGesture(gesture, null, null)) {
            ActionResult.Success
        } else {
            ActionResult.ExecutionFailed("dispatchGesture returned false for SWIPE")
        }
    }

    /**
     * Executes [action] on a looked-up node.
     * The root node and any found node are always recycled via try-finally.
     */
    private fun executeNodeAction(action: LlmAction): ActionResult {
        val root = rootInActiveWindow
            ?: return ActionResult.ExecutionFailed("No active window available")

        try {
            val node = findNode(root, action)
                ?: return ActionResult.NodeNotFound(
                    nodeId = action.targetNodeId,
                    description = action.targetNodeId.substringAfterLast('/').ifBlank { null },
                )

            return try {
                val performed = when (action.actionType) {
                    AccessibilityActionType.CLICK ->
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    AccessibilityActionType.LONG_CLICK ->
                        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    AccessibilityActionType.TYPE -> {
                        val bundle = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                action.value,
                            )
                        }
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    }
                    AccessibilityActionType.SCROLL -> {
                        val scrollAction = if (action.value.equals("backward", ignoreCase = true)) {
                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        } else {
                            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        }
                        node.performAction(scrollAction)
                    }
                    else -> false // SWIPE, PRESS_BACK, PRESS_HOME handled above
                }
                if (performed) {
                    ActionResult.Success
                } else {
                    ActionResult.ExecutionFailed(
                        "performAction returned false for ${action.actionType}",
                    )
                }
            } finally {
                node.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * Finds the best-matching [AccessibilityNodeInfo] for [action].
     *
     * Lookup order:
     * 1. viewIdResourceName ([LlmAction.targetNodeId])
     * 2. Text/content-description hint (last segment of targetNodeId after `/`)
     * 3. Screen bounds ([LlmAction.targetBounds])
     *
     * Extra nodes returned by bulk-search calls are recycled immediately.
     * The caller is responsible for recycling the returned node (and [root]).
     */
    private fun findNode(root: AccessibilityNodeInfo, action: LlmAction): AccessibilityNodeInfo? {
        // 1. By view resource ID
        if (action.targetNodeId.isNotBlank()) {
            val byId = root.findAccessibilityNodeInfosByViewId(action.targetNodeId)
            if (!byId.isNullOrEmpty()) {
                val result = byId.first()
                byId.drop(1).forEach { it.recycle() }
                return result
            }
        }

        // 2. By content description / text (use the last segment of the resource ID as hint)
        val descHint = action.targetNodeId.substringAfterLast('/')
        if (descHint.isNotBlank()) {
            val byText = root.findAccessibilityNodeInfosByText(descHint)
            if (!byText.isNullOrEmpty()) {
                val result = byText.first()
                byText.drop(1).forEach { it.recycle() }
                return result
            }
        }

        // 3. By bounds
        val bounds = action.targetBounds
        val hasNonZeroBounds = bounds.left != 0 || bounds.top != 0 ||
            bounds.right != 0 || bounds.bottom != 0
        if (hasNonZeroBounds) {
            return findNodeByBounds(root, bounds)
        }

        return null
    }

    /**
     * Recursively searches [node]'s subtree for a node whose on-screen bounds
     * match [target] exactly.
     *
     * Child nodes are recycled when they do not match and are not the result;
     * the caller is responsible for recycling the returned node (if non-null)
     * and the initial [node] parameter.
     */
    private fun findNodeByBounds(
        node: AccessibilityNodeInfo,
        target: NodeBounds,
    ): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.left == target.left && rect.top == target.top &&
            rect.right == target.right && rect.bottom == target.bottom
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByBounds(child, target)
            if (found != null) {
                // If the found node IS the child, do not recycle the child here —
                // the caller will recycle it.  If found is a deeper descendant,
                // the child itself is no longer needed.
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }
}


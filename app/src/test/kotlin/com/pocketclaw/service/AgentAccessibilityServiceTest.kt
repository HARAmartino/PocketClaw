package com.pocketclaw.service

import com.pocketclaw.agent.accessibility.ActionResult
import com.pocketclaw.agent.llm.schema.AccessibilityActionType
import com.pocketclaw.agent.llm.schema.LlmAction
import com.pocketclaw.agent.llm.schema.LlmOutputType
import com.pocketclaw.agent.llm.schema.NodeBounds
import com.pocketclaw.agent.validator.ActionValidatorImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AgentAccessibilityService] logic that does not require
 * the Android framework (no Robolectric needed).
 *
 * Tests that require live [AccessibilityNodeInfo] or global-action dispatch
 * are verified via Android instrumented tests (androidTest).
 */
class AgentAccessibilityServiceTest {

    // ── ActionResult sealed-class contract ────────────────────────────────────

    @Test
    fun actionResult_success_isSuccess() {
        val result: ActionResult = ActionResult.Success
        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun actionResult_nodeNotFound_storesNodeId() {
        val result = ActionResult.NodeNotFound(nodeId = "com.example:id/btn_ok", description = "OK")
        assertEquals("com.example:id/btn_ok", result.nodeId)
        assertEquals("OK", result.description)
    }

    @Test
    fun actionResult_nodeNotFound_nullDescriptionAllowed() {
        val result = ActionResult.NodeNotFound(nodeId = "com.example:id/btn")
        assertNotNull(result)
        assertEquals(null, result.description)
    }

    @Test
    fun actionResult_executionFailed_storesReason() {
        val result = ActionResult.ExecutionFailed(reason = "No active window available")
        assertEquals("No active window available", result.reason)
    }

    // ── HARD_DENY_PACKAGES content contract ───────────────────────────────────

    @Test
    fun hardDenyPackages_includesPocketClaw() {
        assertTrue(ActionValidatorImpl.HARD_DENY_PACKAGES.contains("com.pocketclaw"))
    }

    @Test
    fun hardDenyPackages_includesAndroidSettings() {
        assertTrue(ActionValidatorImpl.HARD_DENY_PACKAGES.contains("com.android.settings"))
    }

    @Test
    fun hardDenyPackages_includesPackageInstaller() {
        assertTrue(ActionValidatorImpl.HARD_DENY_PACKAGES.contains("com.android.packageinstaller"))
    }

    @Test
    fun hardDenyPackages_doesNotIncludeArbitraryApp() {
        assertTrue(!ActionValidatorImpl.HARD_DENY_PACKAGES.contains("com.example.myapp"))
    }

    // ── LlmAction construction helpers ────────────────────────────────────────

    private fun buildAction(
        actionType: AccessibilityActionType,
        targetNodeId: String = "com.example:id/button",
        value: String = "",
        bounds: NodeBounds = NodeBounds(0, 0, 0, 0),
    ) = LlmAction(
        type = LlmOutputType.ACTION,
        actionType = actionType,
        targetNodeId = targetNodeId,
        targetBounds = bounds,
        value = value,
        reasoning = "test reasoning",
    )

    @Test
    fun llmAction_clickType_correctActionType() {
        val action = buildAction(AccessibilityActionType.CLICK)
        assertEquals(AccessibilityActionType.CLICK, action.actionType)
    }

    @Test
    fun llmAction_typeAction_valueIsPreserved() {
        val action = buildAction(AccessibilityActionType.TYPE, value = "Hello World")
        assertEquals("Hello World", action.value)
    }

    @Test
    fun llmAction_swipeAction_boundsArePreserved() {
        val bounds = NodeBounds(left = 100, top = 500, right = 300, bottom = 200)
        val action = buildAction(AccessibilityActionType.SWIPE, bounds = bounds)
        assertEquals(100, action.targetBounds.left)
        assertEquals(500, action.targetBounds.top)
        assertEquals(300, action.targetBounds.right)
        assertEquals(200, action.targetBounds.bottom)
    }

    @Test
    fun llmAction_pressBack_noNodeIdRequired() {
        val action = buildAction(AccessibilityActionType.PRESS_BACK, targetNodeId = "")
        assertEquals(AccessibilityActionType.PRESS_BACK, action.actionType)
        assertTrue(action.targetNodeId.isBlank())
    }

    @Test
    fun llmAction_pressHome_noNodeIdRequired() {
        val action = buildAction(AccessibilityActionType.PRESS_HOME, targetNodeId = "")
        assertEquals(AccessibilityActionType.PRESS_HOME, action.actionType)
    }

    // ── Scroll direction logic (mirrors service implementation) ───────────────

    @Test
    fun scrollDirection_backwardValue_isRecognised() {
        val action = buildAction(AccessibilityActionType.SCROLL, value = "backward")
        assertTrue(action.value.equals("backward", ignoreCase = true))
    }

    @Test
    fun scrollDirection_forwardValue_isDefault() {
        val action = buildAction(AccessibilityActionType.SCROLL, value = "forward")
        assertTrue(!action.value.equals("backward", ignoreCase = true))
    }
}

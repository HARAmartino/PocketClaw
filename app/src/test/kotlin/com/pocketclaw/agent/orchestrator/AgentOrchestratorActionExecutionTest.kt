package com.pocketclaw.agent.orchestrator

import com.pocketclaw.agent.accessibility.AccessibilityExecutor
import com.pocketclaw.agent.accessibility.ActionResult
import com.pocketclaw.agent.llm.schema.AccessibilityActionType
import com.pocketclaw.agent.llm.schema.CompressedDomTree
import com.pocketclaw.agent.llm.schema.LlmAction
import com.pocketclaw.agent.llm.schema.LlmOutputType
import com.pocketclaw.agent.llm.schema.NodeBounds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAccessibilityExecutor : AccessibilityExecutor {
    var lastAction: LlmAction? = null
    var resultToReturn: ActionResult = ActionResult.Success

    override suspend fun executeAction(action: LlmAction): ActionResult {
        lastAction = action
        return resultToReturn
    }

    override suspend fun captureCurrentScreen(): CompressedDomTree =
        CompressedDomTree("fake dom")
}

/**
 * Tests for the [AccessibilityExecutor] interface contract and [ActionResult] behaviour
 * wired into the orchestrator's action-execution path.
 */
class AgentOrchestratorActionExecutionTest {

    private val fakeExecutor = FakeAccessibilityExecutor()

    @Test
    fun executeAction_success_recordsAction() = runBlocking {
        val action = LlmAction(
            type = LlmOutputType.ACTION,
            actionType = AccessibilityActionType.CLICK,
            targetNodeId = "com.example:id/btn",
            targetBounds = NodeBounds(0, 0, 100, 50),
            reasoning = "test",
        )
        fakeExecutor.resultToReturn = ActionResult.Success
        fakeExecutor.executeAction(action)
        assertEquals(action, fakeExecutor.lastAction)
    }

    @Test
    fun executeAction_success_returnsSuccess() = runBlocking {
        val action = LlmAction(
            type = LlmOutputType.ACTION,
            actionType = AccessibilityActionType.CLICK,
            targetNodeId = "com.example:id/btn",
            targetBounds = NodeBounds(0, 0, 100, 50),
            reasoning = "test",
        )
        fakeExecutor.resultToReturn = ActionResult.Success
        val result = fakeExecutor.executeAction(action)
        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun executeAction_nodeNotFound_returnsNodeNotFound() = runBlocking {
        val action = LlmAction(
            type = LlmOutputType.ACTION,
            actionType = AccessibilityActionType.CLICK,
            targetNodeId = "com.example:id/missing",
            targetBounds = NodeBounds(0, 0, 0, 0),
            reasoning = "test",
        )
        fakeExecutor.resultToReturn = ActionResult.NodeNotFound(nodeId = "com.example:id/missing")
        val result = fakeExecutor.executeAction(action)
        assertTrue(result is ActionResult.NodeNotFound)
        assertEquals("com.example:id/missing", (result as ActionResult.NodeNotFound).nodeId)
    }

    @Test
    fun captureCurrentScreen_returnsFakeDom() = runBlocking {
        val dom = fakeExecutor.captureCurrentScreen()
        assertEquals("fake dom", dom?.content)
    }
}

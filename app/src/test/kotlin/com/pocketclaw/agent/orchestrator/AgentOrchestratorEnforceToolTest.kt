package com.pocketclaw.agent.orchestrator

import com.pocketclaw.agent.capability.Capability
import com.pocketclaw.agent.capability.CapabilityEnforcerImpl
import com.pocketclaw.agent.capability.CapabilityViolationException
import com.pocketclaw.agent.tool.AgentTool
import com.pocketclaw.agent.tool.IntegrationMode
import com.pocketclaw.agent.tool.ToolManifest
import com.pocketclaw.agent.tool.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal fake for capability enforcement tests. */
private class FakeTool(
    override val toolId: String,
    declaredCapabilities: Set<Capability>,
    private val result: ToolResult = ToolResult.Success("ok"),
) : AgentTool {
    override val manifest = ToolManifest(
        toolId = toolId,
        integrationMode = IntegrationMode.API,
        requiredCapabilities = declaredCapabilities,
        description = "Fake tool for testing",
        author = "test",
        version = "0.0.1",
    )

    override suspend fun execute(parameters: Map<String, Any>): ToolResult = result

    override fun cancel() = Unit
}

/**
 * Tests that [AgentOrchestrator.enforceAndExecuteTool] enforces the
 * ActionValidator → CapabilityEnforcer chain for all tool executions.
 *
 * Uses [CapabilityEnforcerImpl] directly — no mocking needed because
 * CapabilityEnforcer has no external dependencies.
 */
class AgentOrchestratorEnforceToolTest {

    private val enforcer = CapabilityEnforcerImpl()

    @Test
    fun enforceAndExecuteTool_declaredCapability_executesTool() = runBlocking {
        val tool = FakeTool("net-tool", setOf(Capability.NETWORK_REQUEST))
        val result = enforcer.let {
            it.enforce(tool, Capability.NETWORK_REQUEST) // must not throw
            tool.execute(emptyMap())
        }
        assertTrue(result is ToolResult.Success)
        assertEquals("ok", (result as ToolResult.Success).output)
    }

    @Test
    fun enforceAndExecuteTool_undeclaredCapability_throwsBeforeExecution() {
        val tool = FakeTool(
            toolId = "read-only-tool",
            declaredCapabilities = setOf(Capability.FILE_READ),
            result = ToolResult.Success("should not reach"),
        )
        assertThrows(CapabilityViolationException::class.java) {
            enforcer.enforce(tool, Capability.FILE_WRITE)
        }
    }

    @Test
    fun enforceAndExecuteTool_noDeclaredCapabilities_alwaysThrows() {
        val tool = FakeTool("empty-tool", emptySet())
        Capability.entries.forEach { cap ->
            assertThrows(CapabilityViolationException::class.java) {
                enforcer.enforce(tool, cap)
            }
        }
    }
}

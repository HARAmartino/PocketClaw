package com.pocketclaw.agent.capability

import com.pocketclaw.agent.tool.AgentTool
import com.pocketclaw.agent.tool.IntegrationMode
import com.pocketclaw.agent.tool.ToolManifest
import com.pocketclaw.agent.tool.ToolResult
import org.junit.Assert.assertThrows
import org.junit.Test

/** Minimal fake tool for capability enforcement tests. */
private class FakeTool(
    override val toolId: String,
    declaredCapabilities: Set<Capability>,
) : AgentTool {
    override val manifest = ToolManifest(
        toolId = toolId,
        integrationMode = IntegrationMode.API,
        requiredCapabilities = declaredCapabilities,
        description = "Fake tool for testing",
        author = "test",
        version = "0.0.1",
    )

    override suspend fun execute(parameters: Map<String, Any>): ToolResult =
        ToolResult.Success("ok")

    override fun cancel() = Unit
}

class CapabilityEnforcerTest {

    private val enforcer = CapabilityEnforcerImpl()

    @Test
    fun enforce_declaredCapability_doesNotThrow() {
        val tool = FakeTool("net-tool", setOf(Capability.NETWORK_REQUEST))
        enforcer.enforce(tool, Capability.NETWORK_REQUEST) // Should not throw
    }

    @Test
    fun enforce_undeclaredCapability_throwsCapabilityViolationException() {
        val tool = FakeTool("read-only-tool", setOf(Capability.FILE_READ))
        assertThrows(CapabilityViolationException::class.java) {
            enforcer.enforce(tool, Capability.FILE_WRITE)
        }
    }

    @Test
    fun enforce_exceptionContainsToolIdAndCapabilities() {
        val tool = FakeTool("net-tool", setOf(Capability.NETWORK_REQUEST))
        val ex = assertThrows(CapabilityViolationException::class.java) {
            enforcer.enforce(tool, Capability.ACCESSIBILITY_WRITE)
        }
        assert(ex.toolId == "net-tool")
        assert(ex.requestedCapability == Capability.ACCESSIBILITY_WRITE)
        assert(ex.declaredCapabilities == setOf(Capability.NETWORK_REQUEST))
    }

    @Test
    fun enforce_emptyDeclaredCapabilities_alwaysThrows() {
        val tool = FakeTool("empty-tool", emptySet())
        Capability.entries.forEach { cap ->
            assertThrows(CapabilityViolationException::class.java) {
                enforcer.enforce(tool, cap)
            }
        }
    }
}

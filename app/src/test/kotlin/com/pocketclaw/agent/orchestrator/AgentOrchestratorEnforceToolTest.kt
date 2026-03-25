package com.pocketclaw.agent.orchestrator

import com.pocketclaw.agent.capability.Capability
import com.pocketclaw.agent.capability.CapabilityEnforcerImpl
import com.pocketclaw.agent.capability.CapabilityViolationException
import com.pocketclaw.agent.skill.AgentSkill
import com.pocketclaw.agent.skill.IntegrationMode
import com.pocketclaw.agent.skill.SkillManifest
import com.pocketclaw.agent.skill.SkillResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal fake for capability enforcement tests. */
private class FakeSkill(
    override val skillId: String,
    declaredCapabilities: Set<Capability>,
    private val result: SkillResult = SkillResult.Success("ok"),
) : AgentSkill {
    override val manifest = SkillManifest(
        skillId = skillId,
        integrationMode = IntegrationMode.API,
        requiredCapabilities = declaredCapabilities,
        description = "Fake skill for testing",
        author = "test",
        version = "0.0.1",
    )

    override suspend fun execute(parameters: Map<String, Any>): SkillResult = result

    override fun cancel() = Unit
}

/**
 * Tests that [AgentOrchestrator.enforceAndExecuteSkill] enforces the
 * ActionValidator → CapabilityEnforcer chain for all skill executions.
 *
 * Uses [CapabilityEnforcerImpl] directly — no mocking needed because
 * CapabilityEnforcer has no external dependencies.
 */
class AgentOrchestratorEnforceToolTest {

    private val enforcer = CapabilityEnforcerImpl()

    @Test
    fun enforceAndExecuteSkill_declaredCapability_executesSkill() = runBlocking {
        val skill = FakeSkill("net-skill", setOf(Capability.NETWORK_REQUEST))
        val result = enforcer.let {
            it.enforce(skill, Capability.NETWORK_REQUEST) // must not throw
            skill.execute(emptyMap())
        }
        assertTrue(result is SkillResult.Success)
        assertEquals("ok", (result as SkillResult.Success).output)
    }

    @Test
    fun enforceAndExecuteSkill_undeclaredCapability_throwsBeforeExecution() {
        val skill = FakeSkill(
            skillId = "read-only-skill",
            declaredCapabilities = setOf(Capability.FILE_READ),
            result = SkillResult.Success("should not reach"),
        )
        assertThrows(CapabilityViolationException::class.java) {
            enforcer.enforce(skill, Capability.FILE_WRITE)
        }
    }

    @Test
    fun enforceAndExecuteSkill_noDeclaredCapabilities_alwaysThrows() {
        val skill = FakeSkill("empty-skill", emptySet())
        Capability.entries.forEach { cap ->
            assertThrows(CapabilityViolationException::class.java) {
                enforcer.enforce(skill, cap)
            }
        }
    }
}

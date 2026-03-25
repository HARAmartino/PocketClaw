package com.pocketclaw.agent.capability

import com.pocketclaw.agent.skill.AgentSkill
import com.pocketclaw.agent.skill.IntegrationMode
import com.pocketclaw.agent.skill.SkillManifest
import com.pocketclaw.agent.skill.SkillResult
import org.junit.Assert.assertThrows
import org.junit.Test

/** Minimal fake skill for capability enforcement tests. */
private class FakeSkill(
    override val skillId: String,
    declaredCapabilities: Set<Capability>,
) : AgentSkill {
    override val manifest = SkillManifest(
        skillId = skillId,
        integrationMode = IntegrationMode.API,
        requiredCapabilities = declaredCapabilities,
        description = "Fake skill for testing",
        author = "test",
        version = "0.0.1",
    )

    override suspend fun execute(parameters: Map<String, Any>): SkillResult =
        SkillResult.Success("ok")

    override fun cancel() = Unit
}

class CapabilityEnforcerTest {

    private val enforcer = CapabilityEnforcerImpl()

    @Test
    fun enforce_declaredCapability_doesNotThrow() {
        val skill = FakeSkill("net-skill", setOf(Capability.NETWORK_REQUEST))
        enforcer.enforce(skill, Capability.NETWORK_REQUEST) // Should not throw
    }

    @Test
    fun enforce_undeclaredCapability_throwsCapabilityViolationException() {
        val skill = FakeSkill("read-only-skill", setOf(Capability.FILE_READ))
        assertThrows(CapabilityViolationException::class.java) {
            enforcer.enforce(skill, Capability.FILE_WRITE)
        }
    }

    @Test
    fun enforce_exceptionContainsSkillIdAndCapabilities() {
        val skill = FakeSkill("net-skill", setOf(Capability.NETWORK_REQUEST))
        val ex = assertThrows(CapabilityViolationException::class.java) {
            enforcer.enforce(skill, Capability.ACCESSIBILITY_WRITE)
        }
        assert(ex.skillId == "net-skill")
        assert(ex.requestedCapability == Capability.ACCESSIBILITY_WRITE)
        assert(ex.declaredCapabilities == setOf(Capability.NETWORK_REQUEST))
    }

    @Test
    fun enforce_emptyDeclaredCapabilities_alwaysThrows() {
        val skill = FakeSkill("empty-skill", emptySet())
        Capability.entries.forEach { cap ->
            assertThrows(CapabilityViolationException::class.java) {
                enforcer.enforce(skill, cap)
            }
        }
    }
}

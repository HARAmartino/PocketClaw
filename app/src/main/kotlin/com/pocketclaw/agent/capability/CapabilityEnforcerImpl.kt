package com.pocketclaw.agent.capability

import com.pocketclaw.agent.skill.AgentSkill
import javax.inject.Inject

/**
 * Production implementation of [CapabilityEnforcer].
 * Checks the requested capability against the tool's declared manifest capabilities.
 */
class CapabilityEnforcerImpl @Inject constructor() : CapabilityEnforcer {

    override fun enforce(skill: AgentSkill, requestedCapability: Capability) {
        if (requestedCapability !in skill.manifest.requiredCapabilities) {
            throw CapabilityViolationException(
                skillId = skill.skillId,
                requestedCapability = requestedCapability,
                declaredCapabilities = skill.manifest.requiredCapabilities,
            )
        }
    }
}

package com.pocketclaw.agent.capability

import com.pocketclaw.agent.skill.AgentSkill

/** The set of capabilities that agent tools can declare and request. */
enum class Capability {
    FILE_READ,
    FILE_WRITE,
    NETWORK_REQUEST,
    ACCESSIBILITY_READ,
    ACCESSIBILITY_WRITE,
    NOTIFICATION_READ,
    NOTIFICATION_REPLY,
    CALENDAR_API,
    EMAIL_API,
    CAMERA,      // Reserved for future sensor plugins
    MICROPHONE,  // Reserved for future sensor plugins
}

/**
 * Thrown when an [AgentSkill] attempts an operation that exceeds its declared
 * [com.pocketclaw.agent.skill.SkillManifest.requiredCapabilities].
 * Triggers automatic skill quarantine and HITL escalation.
 */
class CapabilityViolationException(
    val skillId: String,
    val requestedCapability: Capability,
    val declaredCapabilities: Set<Capability>,
) : SecurityException(
    "Skill '$skillId' requested capability $requestedCapability " +
        "but only declared: $declaredCapabilities",
)

/**
 * Wraps every [AgentSkill.execute] call and verifies the requested operation
 * matches the skill's declared [com.pocketclaw.agent.skill.SkillManifest.requiredCapabilities].
 * Any violation throws [CapabilityViolationException] — never bypassed.
 */
interface CapabilityEnforcer {
    /**
     * @throws CapabilityViolationException if [requestedCapability] is not in
     *   [AgentSkill.manifest.requiredCapabilities].
     */
    fun enforce(skill: AgentSkill, requestedCapability: Capability)
}

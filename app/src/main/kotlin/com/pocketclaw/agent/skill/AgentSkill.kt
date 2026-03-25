package com.pocketclaw.agent.skill

import com.pocketclaw.agent.capability.Capability

/** Declares how an agent skill integrates with the OS or external APIs. */
enum class IntegrationMode { API, ACCESSIBILITY, HYBRID }

/**
 * Metadata declaration for every [AgentSkill].
 * Used by [com.pocketclaw.agent.capability.CapabilityEnforcer] to verify
 * that runtime operations match declared capabilities.
 */
data class SkillManifest(
    val skillId: String,
    val integrationMode: IntegrationMode,
    val requiredCapabilities: Set<Capability>,
    val description: String,
    val author: String,
    val version: String,
)

/** Result returned by [AgentSkill.execute]. */
sealed class SkillResult {
    data class Success(val output: String) : SkillResult()
    data class Failure(val error: String, val isRecoverable: Boolean) : SkillResult()
}

/**
 * Base interface for all agent skills (built-in and plugin-provided).
 * Every implementation MUST declare its capabilities honestly in [manifest].
 * Runtime capability checks are enforced by [com.pocketclaw.agent.capability.CapabilityEnforcer].
 */
interface AgentSkill {
    val skillId: String
    val manifest: SkillManifest

    /**
     * Execute this skill with the given [parameters].
     * Before calling, the orchestrator will invoke [com.pocketclaw.agent.capability.CapabilityEnforcer]
     * to verify the required capabilities are declared.
     */
    suspend fun execute(parameters: Map<String, Any>): SkillResult

    /** Cancel any in-progress operation (e.g., on Kill Switch activation). */
    fun cancel()
}

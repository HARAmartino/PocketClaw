package com.pocketclaw.agent.tool

import com.pocketclaw.agent.capability.Capability

/** Declares how an agent tool integrates with the OS or external APIs. */
enum class IntegrationMode { API, ACCESSIBILITY, HYBRID }

/**
 * Metadata declaration for every [AgentTool].
 * Used by [com.pocketclaw.agent.capability.CapabilityEnforcer] to verify
 * that runtime operations match declared capabilities.
 */
data class ToolManifest(
    val toolId: String,
    val integrationMode: IntegrationMode,
    val requiredCapabilities: Set<Capability>,
    val description: String,
    val author: String,
    val version: String,
)

/** Result returned by [AgentTool.execute]. */
sealed class ToolResult {
    data class Success(val output: String) : ToolResult()
    data class Failure(val error: String, val isRecoverable: Boolean) : ToolResult()
}

/**
 * Base interface for all agent tools (built-in and plugin-provided).
 * Every implementation MUST declare its capabilities honestly in [manifest].
 * Runtime capability checks are enforced by [com.pocketclaw.agent.capability.CapabilityEnforcer].
 */
interface AgentTool {
    val toolId: String
    val manifest: ToolManifest

    /**
     * Execute this tool with the given [parameters].
     * Before calling, the orchestrator will invoke [com.pocketclaw.agent.capability.CapabilityEnforcer]
     * to verify the required capabilities are declared.
     */
    suspend fun execute(parameters: Map<String, Any>): ToolResult

    /** Cancel any in-progress operation (e.g., on Kill Switch activation). */
    fun cancel()
}

package com.pocketclaw.agent.capability

import com.pocketclaw.agent.tool.AgentTool

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
 * Thrown when an [AgentTool] attempts an operation that exceeds its declared
 * [com.pocketclaw.agent.tool.ToolManifest.requiredCapabilities].
 * Triggers automatic plugin quarantine and HITL escalation.
 */
class CapabilityViolationException(
    val toolId: String,
    val requestedCapability: Capability,
    val declaredCapabilities: Set<Capability>,
) : SecurityException(
    "Tool '$toolId' requested capability $requestedCapability " +
        "but only declared: $declaredCapabilities",
)

/**
 * Wraps every [AgentTool.execute] call and verifies the requested operation
 * matches the tool's declared [com.pocketclaw.agent.tool.ToolManifest.requiredCapabilities].
 * Any violation throws [CapabilityViolationException] — never bypassed.
 */
interface CapabilityEnforcer {
    /**
     * @throws CapabilityViolationException if [requestedCapability] is not in
     *   [AgentTool.manifest.requiredCapabilities].
     */
    fun enforce(tool: AgentTool, requestedCapability: Capability)
}

package com.pocketclaw.agent.capability

import com.pocketclaw.agent.tool.AgentTool
import javax.inject.Inject

/**
 * Production implementation of [CapabilityEnforcer].
 * Checks the requested capability against the tool's declared manifest capabilities.
 */
class CapabilityEnforcerImpl @Inject constructor() : CapabilityEnforcer {

    override fun enforce(tool: AgentTool, requestedCapability: Capability) {
        if (requestedCapability !in tool.manifest.requiredCapabilities) {
            throw CapabilityViolationException(
                toolId = tool.toolId,
                requestedCapability = requestedCapability,
                declaredCapabilities = tool.manifest.requiredCapabilities,
            )
        }
    }
}

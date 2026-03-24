package com.pocketclaw.agent.accessibility

/**
 * Result of an accessibility action execution via [com.pocketclaw.service.AgentAccessibilityService.executeAction].
 */
sealed class ActionResult {
    /** The action completed successfully. */
    object Success : ActionResult()

    /**
     * No matching accessibility node was found.
     * @param nodeId The target_node_id (resource name) that was searched first.
     * @param description The content description searched as fallback, if any.
     */
    data class NodeNotFound(
        val nodeId: String,
        val description: String? = null,
    ) : ActionResult()

    /**
     * The action threw an exception or the underlying API returned failure.
     * @param reason Human-readable description of the failure.
     */
    data class ExecutionFailed(val reason: String) : ActionResult()
}

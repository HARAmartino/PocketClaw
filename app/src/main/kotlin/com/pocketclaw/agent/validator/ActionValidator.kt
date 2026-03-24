package com.pocketclaw.agent.validator

import com.pocketclaw.agent.capability.Capability

/** The type of action being proposed by the LLM or an agent tool. */
enum class ActionType {
    ACCESSIBILITY_CLICK,
    ACCESSIBILITY_TYPE,
    ACCESSIBILITY_SCROLL,
    NETWORK_REQUEST,
    FILE_WRITE,
    FILE_READ,
    PACKAGE_INSTALL,
    PACKAGE_UNINSTALL,
    SETTINGS_CHANGE,
    SYSTEM_CALL,
    TOOL_CALL,
}

/**
 * A pending action proposed by the LLM, awaiting validation before execution.
 * All fields are nullable; provide only what is relevant to the action type.
 */
data class PendingAction(
    val type: ActionType,
    val targetPackage: String? = null,
    val targetComponent: String? = null,
    val targetDomain: String? = null,
    val filePath: String? = null,
    val requiredCapability: Capability? = null,
    val rawPayload: String,
)

/** Result of [ActionValidator.validate]. */
sealed class ValidationResult {
    /** The action is safe to proceed. */
    object Allow : ValidationResult()

    /**
     * The action is permanently blocked.
     * Must NOT retry; must abort the task step immediately.
     */
    data class HardDeny(val reason: String) : ValidationResult()

    /**
     * The action requires Human-in-the-Loop approval.
     * ALWAYS requires HITL even when Auto-Pilot mode is active.
     */
    data class SoftDeny(val reason: String, val requiresHitl: Boolean = true) : ValidationResult()
}

/**
 * Every proposed LLM action MUST pass through this validator before execution.
 * Hard-coded in [com.pocketclaw.agent.orchestrator.AgentOrchestrator].
 * Cannot be bypassed by any flag, setting, or LLM instruction.
 */
interface ActionValidator {
    /**
     * Validates a [PendingAction] against the Hard Deny and Soft Deny lists.
     * @return [ValidationResult.Allow], [ValidationResult.HardDeny], or [ValidationResult.SoftDeny].
     */
    fun validate(action: PendingAction): ValidationResult
}

package com.pocketclaw.agent.llm.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ─── Message types for LLM conversation ───────────────────────────────────────

@Serializable
enum class MessageRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL,
}

@Serializable
data class Message(
    val role: MessageRole,
    val content: String,
)

// ─── LLM configuration ────────────────────────────────────────────────────────

@Serializable
data class LlmConfig(
    val maxOutputTokens: Int = 1024,
    val temperature: Double = 0.2,
    val systemPrompt: String,
)

// ─── Tool definition provided to LLM ─────────────────────────────────────────

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)

// ─── LLM response wrapper ─────────────────────────────────────────────────────

@Serializable
data class LlmResponse(
    val rawContent: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val providerId: String,
    val modelId: String,
)

// ─── Parsed action schemas (LLM output) ──────────────────────────────────────

@Serializable
enum class LlmOutputType {
    @SerialName("action") ACTION,
    @SerialName("tool_call") TOOL_CALL,
    @SerialName("hitl_escalation") HITL_ESCALATION,
}

@Serializable
enum class AccessibilityActionType {
    @SerialName("CLICK") CLICK,
    @SerialName("TYPE") TYPE,
    @SerialName("SCROLL") SCROLL,
    @SerialName("LONG_CLICK") LONG_CLICK,
    @SerialName("SWIPE") SWIPE,
    @SerialName("PRESS_BACK") PRESS_BACK,
    @SerialName("PRESS_HOME") PRESS_HOME,
}

@Serializable
data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Schema 1: Accessibility action produced by the LLM.
 * The [reasoning] field is mandatory (max 200 characters).
 */
@Serializable
data class LlmAction(
    val type: LlmOutputType = LlmOutputType.ACTION,
    @SerialName("action_type") val actionType: AccessibilityActionType,
    @SerialName("target_node_id") val targetNodeId: String,
    @SerialName("target_bounds") val targetBounds: NodeBounds,
    val value: String = "",
    val reasoning: String,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
)

/**
 * Schema 2: Tool call produced by the LLM.
 * The [toolId] MUST match a registered AgentSkill; validated by [LlmOutputValidator].
 * The [reasoning] field is mandatory (max 200 characters).
 */
@Serializable
data class LlmToolCall(
    val type: LlmOutputType = LlmOutputType.TOOL_CALL,
    @SerialName("tool_id") val toolId: String,
    val parameters: JsonElement,
    val reasoning: String,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
)

@Serializable
enum class HitlEscalationReason {
    @SerialName("INJECTION_SUSPECTED") INJECTION_SUSPECTED,
    @SerialName("AMBIGUOUS_TASK") AMBIGUOUS_TASK,
    @SerialName("UNSAFE_ACTION_REQUIRED") UNSAFE_ACTION_REQUIRED,
}

/**
 * Schema 3: The LLM self-escalates to Human-in-the-Loop when it detects
 * injection, ambiguity, or an unsafe required action.
 */
@Serializable
data class LlmHitlEscalation(
    val type: LlmOutputType = LlmOutputType.HITL_ESCALATION,
    val reason: HitlEscalationReason,
    val detail: String = "",
)

/** Discriminated union of all valid parsed LLM outputs. */
sealed class ParsedLlmOutput {
    data class Action(val action: LlmAction) : ParsedLlmOutput()
    data class ToolCall(val toolCall: LlmToolCall) : ParsedLlmOutput()
    data class HitlEscalation(val escalation: LlmHitlEscalation) : ParsedLlmOutput()
}

/** Validation errors produced by [LlmOutputValidator]. */
sealed class LlmValidationError {
    data class SchemaViolation(val detail: String) : LlmValidationError()
    data class UnknownTool(val toolId: String) : LlmValidationError()
    data class MissingReasoning(val outputType: LlmOutputType) : LlmValidationError()
    data class ReasoningTooLong(val length: Int, val max: Int) : LlmValidationError()
    data class UnknownType(val type: String) : LlmValidationError()
}

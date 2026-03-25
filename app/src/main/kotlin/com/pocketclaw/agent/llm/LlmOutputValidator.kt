package com.pocketclaw.agent.llm

import com.pocketclaw.agent.llm.schema.HitlEscalationReason
import com.pocketclaw.agent.llm.schema.LlmAction
import com.pocketclaw.agent.llm.schema.LlmHitlEscalation
import com.pocketclaw.agent.llm.schema.LlmOutputType
import com.pocketclaw.agent.llm.schema.LlmToolCall
import com.pocketclaw.agent.llm.schema.LlmValidationError
import com.pocketclaw.agent.llm.schema.ParsedLlmOutput
import com.pocketclaw.agent.skill.AgentSkill
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * Validates and parses raw LLM JSON output against the three defined schemas.
 *
 * Validation error decision tree:
 * 1. Not valid JSON → [LlmValidationError.SchemaViolation]
 * 2. "type" unknown → [LlmValidationError.UnknownType]
 * 3. Missing "reasoning" → [LlmValidationError.MissingReasoning]
 * 4. "reasoning" > 200 chars → [LlmValidationError.ReasoningTooLong]
 * 5. "skill_id" not in registry → [LlmValidationError.UnknownSkill]
 *
 * On any error: the orchestrator retries the LLM call (max 3 times), then escalates to HITL.
 */
class LlmOutputValidator @Inject constructor(
    private val json: Json,
) {
    companion object {
        private const val MAX_REASONING_LENGTH = 200
    }

    private val registeredSkills = mutableMapOf<String, AgentSkill>()

    fun registerSkill(skill: AgentSkill) {
        registeredSkills[skill.skillId] = skill
    }

    fun validate(rawContent: String): Result<ParsedLlmOutput> {
        val jsonObject = try {
            json.parseToJsonElement(rawContent) as? JsonObject
                ?: return Result.failure(
                    toException(LlmValidationError.SchemaViolation("Root element is not a JSON object.")),
                )
        } catch (e: Exception) {
            return Result.failure(
                toException(LlmValidationError.SchemaViolation("Invalid JSON: ${e.message}")),
            )
        }

        val typeString = jsonObject["type"]?.jsonPrimitive?.content
            ?: return Result.failure(
                toException(LlmValidationError.SchemaViolation("Missing 'type' field.")),
            )

        val outputType = LlmOutputType.entries.firstOrNull {
            it.name.equals(typeString.replace("_", "").uppercase(), ignoreCase = true) ||
                typeString == it.name.lowercase().replace("_", "")
        } ?: LlmOutputType.entries.firstOrNull {
            // Match serialized name (e.g. "action", "tool_call", "hitl_escalation")
            typeString == when (it) {
                LlmOutputType.ACTION -> "action"
                LlmOutputType.TOOL_CALL -> "tool_call"
                LlmOutputType.HITL_ESCALATION -> "hitl_escalation"
            }
        } ?: return Result.failure(
            toException(LlmValidationError.UnknownType(typeString)),
        )

        return when (outputType) {
            LlmOutputType.ACTION -> validateAction(rawContent)
            LlmOutputType.TOOL_CALL -> validateToolCall(rawContent)
            LlmOutputType.HITL_ESCALATION -> validateHitlEscalation(rawContent)
        }
    }

    private fun validateAction(raw: String): Result<ParsedLlmOutput> {
        return try {
            val action = json.decodeFromString<LlmAction>(raw)
            validateReasoning(action.reasoning, LlmOutputType.ACTION)?.let {
                return Result.failure(toException(it))
            }
            Result.success(ParsedLlmOutput.Action(action))
        } catch (e: Exception) {
            Result.failure(toException(LlmValidationError.SchemaViolation("Action schema mismatch: ${e.message}")))
        }
    }

    private fun validateToolCall(raw: String): Result<ParsedLlmOutput> {
        return try {
            val toolCall = json.decodeFromString<LlmToolCall>(raw)
            validateReasoning(toolCall.reasoning, LlmOutputType.TOOL_CALL)?.let {
                return Result.failure(toException(it))
            }
            if (toolCall.skillId !in registeredSkills) {
                return Result.failure(toException(LlmValidationError.UnknownSkill(toolCall.skillId)))
            }
            Result.success(ParsedLlmOutput.ToolCall(toolCall))
        } catch (e: Exception) {
            Result.failure(toException(LlmValidationError.SchemaViolation("ToolCall schema mismatch: ${e.message}")))
        }
    }

    private fun validateHitlEscalation(raw: String): Result<ParsedLlmOutput> {
        return try {
            val escalation = json.decodeFromString<LlmHitlEscalation>(raw)
            Result.success(ParsedLlmOutput.HitlEscalation(escalation))
        } catch (e: Exception) {
            // If the schema is broken but type is hitl_escalation, force HITL anyway
            val fallback = LlmHitlEscalation(
                reason = HitlEscalationReason.AMBIGUOUS_TASK,
                detail = "Malformed hitl_escalation schema: ${e.message?.take(200)}",
            )
            Result.success(ParsedLlmOutput.HitlEscalation(fallback))
        }
    }

    private fun validateReasoning(reasoning: String, type: LlmOutputType): LlmValidationError? {
        if (reasoning.isBlank()) return LlmValidationError.MissingReasoning(type)
        if (reasoning.length > MAX_REASONING_LENGTH) {
            return LlmValidationError.ReasoningTooLong(reasoning.length, MAX_REASONING_LENGTH)
        }
        return null
    }

    private fun toException(error: LlmValidationError): Exception =
        IllegalArgumentException("LLM validation error: $error")
}

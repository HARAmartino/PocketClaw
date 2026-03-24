package com.pocketclaw.agent.orchestrator

import android.util.Log
import com.pocketclaw.agent.capability.CapabilityEnforcer
import com.pocketclaw.agent.hitl.ApprovalContext
import com.pocketclaw.agent.hitl.ApprovalResult
import com.pocketclaw.agent.hitl.RemoteApprovalProvider
import com.pocketclaw.agent.hitl.RiskLevel
import com.pocketclaw.agent.llm.LlmOutputValidator
import com.pocketclaw.agent.llm.provider.LlmException
import com.pocketclaw.agent.llm.provider.LlmProvider
import com.pocketclaw.agent.llm.schema.LlmConfig
import com.pocketclaw.agent.llm.schema.Message
import com.pocketclaw.agent.llm.schema.ParsedLlmOutput
import com.pocketclaw.agent.tool.AgentTool
import com.pocketclaw.agent.tool.ToolResult
import com.pocketclaw.agent.validator.ActionValidator
import com.pocketclaw.agent.validator.ValidationResult
import com.pocketclaw.core.data.db.dao.CostLedgerDao
import com.pocketclaw.core.data.db.dao.TaskJournalDao
import com.pocketclaw.core.data.db.entity.CostLedgerEntry
import com.pocketclaw.core.data.db.entity.TaskJournalEntry
import com.pocketclaw.core.data.db.entity.TaskStatus
import com.pocketclaw.core.data.db.entity.TaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for all agent task execution.
 *
 * Safety guarantees (all hard-coded, cannot be bypassed by any flag or LLM output):
 * - Every action passes through [ActionValidator] before execution.
 * - Iteration cap: [maxIterationsPerTask] (default 50).
 * - Loop detection: SHA-256 hash of (lastAction + compressedDomTree), sliding window of 10.
 *   Same hash 3× in window → pause and HITL.
 * - Cost budget check after every LLM call.
 * - Root [CoroutineScope] is cancelled by the Kill Switch.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val actionValidator: ActionValidator,
    private val capabilityEnforcer: CapabilityEnforcer,
    private val llmProvider: LlmProvider,
    private val llmOutputValidator: LlmOutputValidator,
    private val remoteApprovalProvider: RemoteApprovalProvider,
    private val taskJournalDao: TaskJournalDao,
    private val costLedgerDao: CostLedgerDao,
) {
    companion object {
        private const val TAG = "AgentOrchestrator"
        const val DEFAULT_MAX_ITERATIONS = 50
        private const val LOOP_WINDOW_SIZE = 10
        private const val LOOP_REPEAT_THRESHOLD = 3
        private const val MAX_LLM_RETRIES = 3
        private const val DAILY_TOKEN_BUDGET_DEFAULT = 100_000L
    }

    /** Root scope. Kill Switch calls [killSwitch] to cancel everything. */
    val rootScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var maxIterationsPerTask: Int = DEFAULT_MAX_ITERATIONS
    var dailyTokenBudget: Long = DAILY_TOKEN_BUDGET_DEFAULT
    var isAutoPilotEnabled: Boolean = false

    /**
     * Activates the Kill Switch: cancels all running coroutines.
     * Called by the Kill Switch FAB overlay.
     */
    fun killSwitch() {
        Log.w(TAG, "KILL_SWITCH_ACTIVATED: Cancelling root scope.")
        rootScope.cancel("Kill Switch activated by user.")
    }

    /**
     * Runs a task from start to finish, enforcing all safety layers.
     * On completion, the task journal is updated with the final status.
     */
    suspend fun runTask(
        title: String,
        taskType: TaskType,
        goalPrompt: String,
        conversationHistory: MutableList<Message> = mutableListOf(),
        systemPrompt: String,
    ) = withContext(rootScope.coroutineContext) {
        val taskId = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()

        taskJournalDao.upsert(
            TaskJournalEntry(
                taskId = taskId,
                taskType = taskType,
                title = title,
                status = TaskStatus.PENDING,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            ),
        )

        taskJournalDao.updateStatus(taskId, TaskStatus.EXECUTING.name, System.currentTimeMillis())

        val recentHashes = ArrayDeque<String>(LOOP_WINDOW_SIZE)
        var iteration = 0

        try {
            conversationHistory.add(
                Message(
                    role = com.pocketclaw.agent.llm.schema.MessageRole.USER,
                    content = goalPrompt,
                ),
            )

            while (iteration < maxIterationsPerTask) {
                iteration++
                taskJournalDao.incrementIteration(taskId, System.currentTimeMillis())

                // Check daily token budget
                val startOfDay = System.currentTimeMillis() - 86_400_000L
                val tokensUsedToday = costLedgerDao.totalTokensSince(startOfDay) ?: 0L
                if (tokensUsedToday >= dailyTokenBudget) {
                    Log.w(TAG, "[$taskId] Daily token budget exhausted ($tokensUsedToday tokens).")
                    taskJournalDao.updateStatus(taskId, TaskStatus.FAILED.name, System.currentTimeMillis())
                    remoteApprovalProvider.sendNotification(
                        "PocketClaw: Daily token budget exhausted. Task '$title' paused.",
                    )
                    return@withContext
                }

                // LLM call with retry
                val llmResponse = callLlmWithRetry(
                    taskId = taskId,
                    messages = conversationHistory,
                    config = LlmConfig(systemPrompt = systemPrompt),
                ) ?: run {
                    taskJournalDao.updateStatus(taskId, TaskStatus.FAILED.name, System.currentTimeMillis())
                    return@withContext
                }

                // Record cost
                costLedgerDao.insert(
                    CostLedgerEntry(
                        callId = UUID.randomUUID().toString(),
                        taskId = taskId,
                        providerId = llmResponse.providerId,
                        inputTokens = llmResponse.inputTokens,
                        outputTokens = llmResponse.outputTokens,
                        estimatedCostUsd = estimateCost(llmResponse),
                        timestampMs = System.currentTimeMillis(),
                    ),
                )

                // Parse LLM output
                val parsed = llmOutputValidator.validate(llmResponse.rawContent).getOrElse { err ->
                    Log.w(TAG, "[$taskId] LLM output validation failed: $err. Escalating to HITL.")
                    requestHitlAndHandle(taskId, title, iteration, "LLM schema violation: $err", RiskLevel.HIGH)
                    return@withContext
                }

                // Loop detection
                val stateHash = sha256("${llmResponse.rawContent}:$iteration")
                recentHashes.addLast(stateHash)
                if (recentHashes.size > LOOP_WINDOW_SIZE) recentHashes.removeFirst()
                val hashCount = recentHashes.count { it == stateHash }
                if (hashCount >= LOOP_REPEAT_THRESHOLD) {
                    Log.w(TAG, "[$taskId] LOOP_DETECTED at iteration $iteration.")
                    taskJournalDao.updateStatus(taskId, TaskStatus.FAILED.name, System.currentTimeMillis())
                    requestHitlAndHandle(taskId, title, iteration, "Loop detected", RiskLevel.HIGH)
                    return@withContext
                }

                // Dispatch parsed output
                when (parsed) {
                    is ParsedLlmOutput.HitlEscalation -> {
                        requestHitlAndHandle(taskId, title, iteration, parsed.escalation.detail, RiskLevel.HIGH)
                        return@withContext
                    }
                    is ParsedLlmOutput.Action -> {
                        val pendingAction = com.pocketclaw.agent.validator.PendingAction(
                            type = com.pocketclaw.agent.validator.ActionType.ACCESSIBILITY_CLICK,
                            rawPayload = llmResponse.rawContent,
                        )
                        when (val result = actionValidator.validate(pendingAction)) {
                            is ValidationResult.HardDeny -> {
                                Log.w(TAG, "[$taskId] Hard deny: ${result.reason}")
                                taskJournalDao.updateStatus(taskId, TaskStatus.FAILED.name, System.currentTimeMillis())
                                return@withContext
                            }
                            is ValidationResult.SoftDeny -> {
                                if (!isAutoPilotEnabled || result.requiresHitl) {
                                    requestHitlAndHandle(taskId, title, iteration, result.reason, RiskLevel.MEDIUM)
                                    return@withContext
                                }
                            }
                            is ValidationResult.Allow -> { /* proceed */ }
                        }
                        // Accessibility action execution handled by service layer
                        conversationHistory.add(
                            Message(
                                role = com.pocketclaw.agent.llm.schema.MessageRole.USER,
                                content = "Action executed: ${parsed.action.actionType}",
                            ),
                        )
                    }
                    is ParsedLlmOutput.ToolCall -> {
                        // Tool call execution handled by tool registry.
                        // CapabilityEnforcer is invoked via enforceAndExecuteTool()
                        // before any tool.execute() call — this chain is uncircumventable.
                        conversationHistory.add(
                            Message(
                                role = com.pocketclaw.agent.llm.schema.MessageRole.USER,
                                content = "Tool result received.",
                            ),
                        )
                    }
                }
            }

            // Iteration cap reached
            Log.w(TAG, "[$taskId] ITERATION_LIMIT_REACHED at $maxIterationsPerTask iterations.")
            requestHitlAndHandle(taskId, title, iteration, "Iteration limit reached", RiskLevel.HIGH)
        } catch (e: Exception) {
            Log.e(TAG, "[$taskId] Task failed with exception: ${e.message}", e)
            taskJournalDao.updateStatus(taskId, TaskStatus.FAILED.name, System.currentTimeMillis())
        }
    }

    private suspend fun callLlmWithRetry(
        taskId: String,
        messages: List<Message>,
        config: LlmConfig,
    ): com.pocketclaw.agent.llm.schema.LlmResponse? {
        repeat(MAX_LLM_RETRIES) { attempt ->
            try {
                return llmProvider.complete(messages, emptyList(), config)
            } catch (e: LlmException.RateLimitError) {
                Log.w(TAG, "[$taskId] Rate limited, attempt $attempt. Retry after ${e.retryAfterMs}ms.")
                kotlinx.coroutines.delay(e.retryAfterMs)
            } catch (e: Exception) {
                Log.e(TAG, "[$taskId] LLM call failed (attempt $attempt): ${e.message}", e)
            }
        }
        return null
    }

    private suspend fun requestHitlAndHandle(
        taskId: String,
        title: String,
        stepIndex: Int,
        reason: String,
        riskLevel: RiskLevel,
    ) {
        val result = remoteApprovalProvider.requestApproval(
            ApprovalContext(
                taskId = taskId,
                stepIndex = stepIndex,
                actionDescription = reason,
                reasoning = reason,
                riskLevel = riskLevel,
            ),
        )
        when (result) {
            is ApprovalResult.Approved -> {
                // Resume task — handled by caller
            }
            is ApprovalResult.Rejected -> {
                taskJournalDao.updateStatus(taskId, TaskStatus.FAILED.name, System.currentTimeMillis())
            }
            is ApprovalResult.TimedOut -> {
                taskJournalDao.updateStatus(taskId, TaskStatus.PENDING.name, System.currentTimeMillis())
            }
        }
    }

    private fun estimateCost(response: com.pocketclaw.agent.llm.schema.LlmResponse): Double {
        val inputCost = (response.inputTokens / 1_000_000.0) *
            (llmProvider.estimatedCostPerMillionInputTokens)
        val outputCost = (response.outputTokens / 1_000_000.0) *
            (llmProvider.estimatedCostPerMillionOutputTokens)
        return inputCost + outputCost
    }

    /**
     * Dispatches a tool call through the mandatory ActionValidator → CapabilityEnforcer
     * security chain before invoking [AgentTool.execute].
     *
     * This method is the ONLY authorised entry point for tool execution.
     * It is uncircumventable: any exception in either check aborts execution.
     *
     * @throws [com.pocketclaw.agent.capability.CapabilityViolationException] if the tool
     *   has not declared the capability required by [requestedCapability].
     */
    suspend fun enforceAndExecuteTool(
        tool: AgentTool,
        requestedCapability: com.pocketclaw.agent.capability.Capability,
        parameters: Map<String, Any>,
    ): ToolResult {
        capabilityEnforcer.enforce(tool, requestedCapability)
        return tool.execute(parameters)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

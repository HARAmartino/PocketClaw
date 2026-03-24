package com.pocketclaw.agent.hitl

/** Describes the context of a pending Human-in-the-Loop approval request. */
data class ApprovalContext(
    val taskId: String,
    val stepIndex: Int,
    val actionDescription: String,
    val reasoning: String,
    val riskLevel: RiskLevel,
    val timeoutMs: Long = 300_000L, // 5 minutes default
)

/** Risk classification for HITL approval requests. */
enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

/** The result of a Human-in-the-Loop approval request. */
sealed class ApprovalResult {
    /** The user explicitly approved the action. */
    object Approved : ApprovalResult()

    /** The user explicitly rejected the action. */
    data class Rejected(val reason: String) : ApprovalResult()

    /** The approval request timed out without user response. */
    object TimedOut : ApprovalResult()
}

/**
 * Abstraction for remote approval channels (Telegram, Discord) and
 * notification-only mode (Heartbeat results).
 * Ship two implementations: [com.pocketclaw.agent.hitl.TelegramApprovalProvider]
 * and [com.pocketclaw.agent.hitl.DiscordApprovalProvider].
 */
interface RemoteApprovalProvider {
    val providerId: String
    val displayName: String

    /**
     * Sends an approval request to the remote channel and suspends until the user
     * responds or [ApprovalContext.timeoutMs] elapses.
     */
    suspend fun requestApproval(context: ApprovalContext): ApprovalResult

    /**
     * Sends a one-way notification (no approval needed).
     * Used for Heartbeat results and informational alerts.
     */
    suspend fun sendNotification(message: String)
}

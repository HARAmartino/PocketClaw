package com.pocketclaw.agent.hitl

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telegram bot-based HITL approval provider.
 * Sends approval requests and notifications via the Telegram Bot API.
 *
 * Bot token stored in [com.pocketclaw.core.data.secret.SecretStore] under
 * providerId = "telegram".
 */
@Singleton
class TelegramApprovalProvider @Inject constructor() : RemoteApprovalProvider {

    override val providerId = "telegram"
    override val displayName = "Telegram Bot"

    companion object {
        private const val TAG = "TelegramApproval"
    }

    override suspend fun requestApproval(context: ApprovalContext): ApprovalResult {
        Log.i(TAG, "Sending Telegram approval request for task ${context.taskId}, step ${context.stepIndex}.")
        // Full implementation: POST to https://api.telegram.org/bot{token}/sendMessage
        // Poll for user reply (inline keyboard: Approve / Reject)
        // Respect context.timeoutMs
        // Return ApprovalResult.TimedOut if no response
        return ApprovalResult.TimedOut // Stub: replace with real implementation
    }

    override suspend fun sendNotification(message: String) {
        Log.i(TAG, "Sending Telegram notification: $message")
        // Full implementation: POST to https://api.telegram.org/bot{token}/sendMessage
    }
}

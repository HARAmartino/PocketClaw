package com.pocketclaw.agent.hitl

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discord webhook-based HITL approval provider.
 * Sends approval requests via Discord bot webhook messages with interactive buttons.
 *
 * Bot token stored in [com.pocketclaw.core.data.secret.SecretStore] under
 * providerId = "discord".
 */
@Singleton
class DiscordApprovalProvider @Inject constructor() : RemoteApprovalProvider {

    override val providerId = "discord"
    override val displayName = "Discord Bot"

    companion object {
        private const val TAG = "DiscordApproval"
    }

    override suspend fun requestApproval(context: ApprovalContext): ApprovalResult {
        Log.i(TAG, "Sending Discord approval request for task ${context.taskId}, step ${context.stepIndex}.")
        // Full implementation: POST to Discord webhook URL
        // Use Discord interaction components (buttons: Approve / Reject)
        // Respect context.timeoutMs
        return ApprovalResult.TimedOut // Stub: replace with real implementation
    }

    override suspend fun sendNotification(message: String) {
        Log.i(TAG, "Sending Discord notification: $message")
        // Full implementation: POST to Discord webhook URL
    }
}

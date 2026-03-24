package com.pocketclaw.agent.hitl

import android.util.Log
import com.pocketclaw.core.data.secret.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discord webhook-based HITL approval provider.
 * Sends approval requests via Discord webhook messages with interactive button components.
 * Falls back to plain-text embed when components are unavailable.
 *
 * Webhook URL stored in [SecretStore] under providerId = "discord".
 *
 * NOTE: Discord webhooks are one-way — there is no built-in callback mechanism.
 * Approval is delivered as a plain-text embed with instructions for the user to
 * reply via another channel. A future phase may integrate a Discord bot with
 * interaction handling to support true button-based approval.
 */
@Singleton
class DiscordApprovalProvider @Inject constructor(
    private val secretStore: SecretStore,
    private val httpClient: HttpClient,
) : RemoteApprovalProvider {

    override val providerId = "discord"
    override val displayName = "Discord Bot"

    companion object {
        private const val TAG = "DiscordApproval"
        private const val MAX_NETWORK_RETRIES = 3
        private const val RETRY_DELAY_MS = 3_000L
    }

    // ── Serialization models ──────────────────────────────────────────────────

    @Serializable
    private data class DiscordEmbed(
        val title: String,
        val description: String,
        val color: Int,
    )

    @Serializable
    private data class DiscordWebhookPayload(
        val username: String = "PocketClaw",
        val embeds: List<DiscordEmbed>,
    )

    // ── RemoteApprovalProvider ────────────────────────────────────────────────

    override suspend fun requestApproval(context: ApprovalContext): ApprovalResult {
        val webhookUrl = secretStore.getWebhookUrl(providerId)
            ?: run {
                Log.w(TAG, "No Discord webhook URL configured.")
                return ApprovalResult.TimedOut
            }

        val color = when (context.riskLevel) {
            RiskLevel.LOW -> 0x2ECC71      // green
            RiskLevel.MEDIUM -> 0xF39C12   // orange
            RiskLevel.HIGH -> 0xE74C3C     // red
            RiskLevel.CRITICAL -> 0x992D22 // dark red
        }

        val description = buildString {
            appendLine("**Task:** `${context.taskId}`  |  **Step:** ${context.stepIndex}")
            appendLine("**Risk:** ${context.riskLevel}")
            appendLine()
            appendLine("**Action:** ${context.actionDescription}")
            appendLine()
            appendLine("**Reasoning:** ${context.reasoning}")
            appendLine()
            appendLine("⚠️ This notification is informational. " +
                "Button-based approval requires a Discord bot with interaction handling.")
        }

        val payload = DiscordWebhookPayload(
            embeds = listOf(
                DiscordEmbed(
                    title = "🤖 PocketClaw HITL Approval Request",
                    description = description,
                    color = color,
                ),
            ),
        )

        val success = retryNetworkOp {
            httpClient.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        }

        if (success == null) {
            Log.w(TAG, "Failed to post approval request to Discord after $MAX_NETWORK_RETRIES attempts.")
        } else {
            Log.i(TAG, "Approval request posted to Discord for task ${context.taskId}.")
        }

        // Discord webhooks are one-way: return TimedOut immediately since we cannot
        // receive interactive button callbacks via a plain webhook.
        return ApprovalResult.TimedOut
    }

    override suspend fun sendNotification(message: String) {
        val webhookUrl = secretStore.getWebhookUrl(providerId) ?: run {
            Log.w(TAG, "No Discord webhook URL configured — skipping notification.")
            return
        }
        val payload = DiscordWebhookPayload(
            embeds = listOf(
                DiscordEmbed(
                    title = "PocketClaw Notification",
                    description = message,
                    color = 0x5865F2, // Discord blurple
                ),
            ),
        )
        retryNetworkOp {
            httpClient.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private suspend fun <T> retryNetworkOp(block: suspend () -> T): T? {
        repeat(MAX_NETWORK_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w(TAG, "Network attempt ${attempt + 1}/$MAX_NETWORK_RETRIES failed: ${e.message}")
                if (attempt < MAX_NETWORK_RETRIES - 1) delay(RETRY_DELAY_MS)
            }
        }
        return null
    }
}

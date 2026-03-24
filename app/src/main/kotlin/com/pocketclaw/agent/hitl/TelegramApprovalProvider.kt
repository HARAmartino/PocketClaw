package com.pocketclaw.agent.hitl

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pocketclaw.core.data.secret.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telegram bot-based HITL approval provider.
 * Sends approval requests and notifications via the Telegram Bot API.
 *
 * Bot token stored in [SecretStore] under providerId = "telegram".
 * Chat ID stored in DataStore under key [PREF_CHAT_ID].
 */
@Singleton
class TelegramApprovalProvider @Inject constructor(
    private val secretStore: SecretStore,
    private val dataStore: DataStore<Preferences>,
    private val httpClient: HttpClient,
) : RemoteApprovalProvider {

    override val providerId = "telegram"
    override val displayName = "Telegram Bot"

    companion object {
        private const val TAG = "TelegramApproval"
        private const val BASE_URL = "https://api.telegram.org"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val MAX_NETWORK_RETRIES = 3

        internal val PREF_CHAT_ID = stringPreferencesKey("telegram_chat_id")
    }

    // ── Serialization models ──────────────────────────────────────────────────

    @Serializable
    private data class InlineKeyboardButton(val text: String, val callback_data: String)

    @Serializable
    private data class InlineKeyboardMarkup(val inline_keyboard: List<List<InlineKeyboardButton>>)

    @Serializable
    private data class SendMessageRequest(
        val chat_id: String,
        val text: String,
        val reply_markup: InlineKeyboardMarkup,
    )

    @Serializable
    private data class SendMessageResponse(val ok: Boolean, val result: MessageResult? = null)

    @Serializable
    private data class MessageResult(val message_id: Long)

    @Serializable
    private data class GetUpdatesResponse(val ok: Boolean, val result: List<Update> = emptyList())

    @Serializable
    private data class Update(val update_id: Long, val callback_query: CallbackQuery? = null)

    @Serializable
    private data class CallbackQuery(val id: String, val data: String)

    @Serializable
    private data class AnswerCallbackRequest(val callback_query_id: String)

    @Serializable
    private data class SendTextRequest(val chat_id: String, val text: String)

    // ── RemoteApprovalProvider ────────────────────────────────────────────────

    override suspend fun requestApproval(context: ApprovalContext): ApprovalResult {
        val token = secretStore.getBotToken(providerId)
            ?: run {
                Log.w(TAG, "No Telegram bot token configured.")
                return ApprovalResult.TimedOut
            }
        val chatId = dataStore.data.first()[PREF_CHAT_ID]
            ?: run {
                Log.w(TAG, "No Telegram chat ID configured.")
                return ApprovalResult.TimedOut
            }

        val text = buildApprovalText(context)
        val keyboard = InlineKeyboardMarkup(
            inline_keyboard = listOf(
                listOf(
                    InlineKeyboardButton("Approve ✅", "approve"),
                    InlineKeyboardButton("Reject ❌", "reject"),
                ),
            ),
        )

        val sent = retryNetworkOp {
            httpClient.post("$BASE_URL/bot$token/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(chatId, text, keyboard))
            }.body<SendMessageResponse>()
        } ?: return ApprovalResult.TimedOut

        if (!sent.ok) {
            Log.w(TAG, "sendMessage returned ok=false.")
            return ApprovalResult.TimedOut
        }

        return pollForDecision(token, context.timeoutMs)
    }

    override suspend fun sendNotification(message: String) {
        val token = secretStore.getBotToken(providerId) ?: run {
            Log.w(TAG, "No Telegram bot token configured — skipping notification.")
            return
        }
        val chatId = dataStore.data.first()[PREF_CHAT_ID] ?: run {
            Log.w(TAG, "No Telegram chat ID configured — skipping notification.")
            return
        }
        retryNetworkOp {
            httpClient.post("$BASE_URL/bot$token/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(SendTextRequest(chatId, message))
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private suspend fun pollForDecision(token: String, timeoutMs: Long): ApprovalResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        var offset = 0L

        while (System.currentTimeMillis() < deadline) {
            val updates = retryNetworkOp {
                httpClient.get("$BASE_URL/bot$token/getUpdates") {
                    url {
                        parameters.append("offset", offset.toString())
                        parameters.append("allowed_updates", """["callback_query"]""")
                    }
                }.body<GetUpdatesResponse>()
            }

            if (updates == null) {
                delay(POLL_INTERVAL_MS)
                continue
            }

            for (update in updates.result) {
                offset = update.update_id + 1
                val cbq = update.callback_query ?: continue
                retryNetworkOp {
                    httpClient.post("$BASE_URL/bot$token/answerCallbackQuery") {
                        contentType(ContentType.Application.Json)
                        setBody(AnswerCallbackRequest(cbq.id))
                    }
                }
                return when (cbq.data) {
                    "approve" -> ApprovalResult.Approved
                    "reject" -> ApprovalResult.Rejected("User rejected via Telegram.")
                    else -> continue
                }
            }

            delay(POLL_INTERVAL_MS)
        }

        return ApprovalResult.TimedOut
    }

    /**
     * Executes [block] up to [MAX_NETWORK_RETRIES] times.
     * Returns null if all attempts fail.
     */
    private suspend fun <T> retryNetworkOp(block: suspend () -> T): T? {
        repeat(MAX_NETWORK_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w(TAG, "Network attempt ${attempt + 1}/$MAX_NETWORK_RETRIES failed: ${e.message}")
                if (attempt < MAX_NETWORK_RETRIES - 1) delay(POLL_INTERVAL_MS)
            }
        }
        return null
    }

    private fun buildApprovalText(context: ApprovalContext): String = buildString {
        appendLine("🤖 *PocketClaw HITL Approval Request*")
        appendLine()
        appendLine("Task: `${context.taskId}`  Step: ${context.stepIndex}")
        appendLine("Risk: ${context.riskLevel}")
        appendLine()
        appendLine("*Action:* ${context.actionDescription}")
        appendLine()
        appendLine("*Reasoning:* ${context.reasoning}")
    }
}

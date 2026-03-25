package com.pocketclaw.agent.skill

import android.util.Log
import com.pocketclaw.agent.capability.Capability
import com.pocketclaw.core.data.secret.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent skill that integrates with the Gmail API via OAuth 2.0.
 *
 * **Supported actions (via [execute] `action` parameter):**
 * - `"read"` — searches the user's mailbox. Requires `query` parameter (Gmail search syntax).
 * - `"send"` — sends an email. Requires `to`, `subject`, and `body` parameters.
 *
 * **OAuth token management:**
 * - Access token stored under key `"gmail"` via [SecretStore.saveOAuthToken].
 * - Refresh token stored under key `"gmail_refresh"` via [SecretStore.saveOAuthToken].
 * - Client credentials stored under `"gmail_client_id"` / `"gmail_client_secret"` via [SecretStore.saveApiKey].
 * - On 401 the skill automatically refreshes the access token and retries once.
 *
 * **Security:** declared capability is [Capability.EMAIL_API]; any other capability request
 * is rejected by [com.pocketclaw.agent.capability.CapabilityEnforcer] before [execute] is called.
 */
@Singleton
class GmailSkill @Inject constructor(
    private val secretStore: SecretStore,
    private val httpClient: HttpClient,
    private val json: Json,
) : AgentSkill {

    companion object {
        private const val TAG = "GmailSkill"

        const val SKILL_ID = "gmail"

        private const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1"
        private const val OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"

        private const val SECRET_ACCESS_TOKEN = "gmail"
        private const val SECRET_REFRESH_TOKEN = "gmail_refresh"
        private const val SECRET_CLIENT_ID = "gmail_client_id"
        private const val SECRET_CLIENT_SECRET = "gmail_client_secret"

        private const val MAX_RESULTS_DEFAULT = 10
    }

    override val skillId: String = SKILL_ID

    override val manifest = SkillManifest(
        skillId = SKILL_ID,
        integrationMode = IntegrationMode.API,
        requiredCapabilities = setOf(Capability.EMAIL_API),
        description = "Read and send Gmail messages via the Gmail REST API with OAuth 2.0.",
        author = "PocketClaw",
        version = "1.0.0",
    )

    @Volatile private var isCancelled = false
    private val refreshMutex = Mutex()

    override suspend fun execute(parameters: Map<String, Any>): SkillResult {
        isCancelled = false
        return when (val action = parameters["action"] as? String) {
            "read" -> handleRead(parameters)
            "send" -> handleSend(parameters)
            null -> SkillResult.Failure(
                error = "Missing required parameter 'action'. Expected \"read\" or \"send\".",
                isRecoverable = false,
            )
            else -> SkillResult.Failure(
                error = "Unknown action '$action'. Expected \"read\" or \"send\".",
                isRecoverable = false,
            )
        }
    }

    override fun cancel() {
        isCancelled = true
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    private suspend fun handleRead(parameters: Map<String, Any>): SkillResult {
        val query = parameters["query"] as? String ?: ""
        val maxResults = (parameters["maxResults"] as? Number)?.toInt() ?: MAX_RESULTS_DEFAULT

        val accessToken = getValidAccessToken()
            ?: return SkillResult.Failure(
                error = "Gmail OAuth access token not available. Please configure Gmail OAuth credentials.",
                isRecoverable = false,
            )

        return try {
            val listResponse = httpClient.get("$GMAIL_API_BASE/users/me/messages") {
                bearerAuth(accessToken)
                parameter("q", query)
                parameter("maxResults", maxResults)
            }

            if (listResponse.status == HttpStatusCode.Unauthorized) {
                val refreshed = refreshAccessToken() ?: return SkillResult.Failure(
                    error = "Token refresh failed. Re-authorise Gmail.",
                    isRecoverable = false,
                )
                return handleReadWithToken(refreshed, query, maxResults)
            }

            if (listResponse.status != HttpStatusCode.OK) {
                return SkillResult.Failure(
                    error = "Gmail API error ${listResponse.status.value}: ${listResponse.bodyAsText()}",
                    isRecoverable = true,
                )
            }

            parseMessageListResponse(listResponse.bodyAsText(), accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "handleRead failed: ${e.message}", e)
            SkillResult.Failure(error = "Network error: ${e.message}", isRecoverable = true)
        }
    }

    private suspend fun handleReadWithToken(
        accessToken: String,
        query: String,
        maxResults: Int,
    ): SkillResult {
        return try {
            val listResponse = httpClient.get("$GMAIL_API_BASE/users/me/messages") {
                bearerAuth(accessToken)
                parameter("q", query)
                parameter("maxResults", maxResults)
            }
            if (listResponse.status != HttpStatusCode.OK) {
                return SkillResult.Failure(
                    error = "Gmail API error ${listResponse.status.value} after token refresh.",
                    isRecoverable = false,
                )
            }
            parseMessageListResponse(listResponse.bodyAsText(), accessToken)
        } catch (e: Exception) {
            SkillResult.Failure(error = "Network error after token refresh: ${e.message}", isRecoverable = true)
        }
    }

    private suspend fun parseMessageListResponse(
        responseBody: String,
        accessToken: String,
    ): SkillResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val messages = root["messages"]?.jsonArray ?: run {
                return SkillResult.Success("No messages found.")
            }

            val summaries = messages.take(MAX_RESULTS_DEFAULT).mapNotNull { element ->
                if (isCancelled) return SkillResult.Failure("Cancelled", isRecoverable = false)
                val msgId = element.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                fetchMessageSummary(msgId, accessToken)
            }
            SkillResult.Success(summaries.joinToString("\n---\n"))
        } catch (e: Exception) {
            SkillResult.Failure(error = "Failed to parse Gmail response: ${e.message}", isRecoverable = true)
        }
    }

    private suspend fun fetchMessageSummary(messageId: String, accessToken: String): String? {
        return try {
            val msgResponse = httpClient.get(
                "$GMAIL_API_BASE/users/me/messages/$messageId",
            ) {
                bearerAuth(accessToken)
                parameter("format", "metadata")
                parameter("metadataHeaders", "From")
                parameter("metadataHeaders", "Subject")
                parameter("metadataHeaders", "Date")
            }
            if (msgResponse.status != HttpStatusCode.OK) return null
            val msgJson = json.parseToJsonElement(msgResponse.bodyAsText()).jsonObject
            val headers = msgJson["payload"]?.jsonObject
                ?.get("headers")?.jsonArray
                ?: return null
            val headerMap = headers.associate { h ->
                val obj = h.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val value = obj["value"]?.jsonPrimitive?.content ?: ""
                name to value
            }
            val snippet = msgJson["snippet"]?.jsonPrimitive?.content ?: ""
            "From: ${headerMap["From"]}\nSubject: ${headerMap["Subject"]}\nDate: ${headerMap["Date"]}\nSnippet: $snippet"
        } catch (e: Exception) {
            Log.w(TAG, "fetchMessageSummary($messageId) failed: ${e.message}")
            null
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private suspend fun handleSend(parameters: Map<String, Any>): SkillResult {
        val to = parameters["to"] as? String
            ?: return SkillResult.Failure("Missing 'to' parameter.", isRecoverable = false)
        val subject = parameters["subject"] as? String ?: "(no subject)"
        val body = parameters["body"] as? String ?: ""

        val accessToken = getValidAccessToken()
            ?: return SkillResult.Failure(
                error = "Gmail OAuth access token not available. Please configure Gmail OAuth credentials.",
                isRecoverable = false,
            )

        return trySend(to, subject, body, accessToken, retryOnUnauthorized = true)
    }

    private suspend fun trySend(
        to: String,
        subject: String,
        body: String,
        accessToken: String,
        retryOnUnauthorized: Boolean,
    ): SkillResult {
        return try {
            val rawMessage = buildRawMimeMessage(to, subject, body)
            val requestBody = """{"raw":"$rawMessage"}"""

            val sendResponse = httpClient.post("$GMAIL_API_BASE/users/me/messages/send") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            when {
                sendResponse.status == HttpStatusCode.Unauthorized && retryOnUnauthorized -> {
                    val refreshed = refreshAccessToken()
                        ?: return SkillResult.Failure(
                            "Token refresh failed. Re-authorise Gmail.",
                            isRecoverable = false,
                        )
                    trySend(to, subject, body, refreshed, retryOnUnauthorized = false)
                }
                sendResponse.status.value in 200..299 -> {
                    Log.i(TAG, "Email sent to '$to'.")
                    SkillResult.Success("Email sent successfully to $to.")
                }
                else -> SkillResult.Failure(
                    error = "Gmail send failed (${sendResponse.status.value}): ${sendResponse.bodyAsText()}",
                    isRecoverable = sendResponse.status.value >= 500,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "trySend failed: ${e.message}", e)
            SkillResult.Failure(error = "Network error while sending email: ${e.message}", isRecoverable = true)
        }
    }

    // ── OAuth helpers ─────────────────────────────────────────────────────────

    private fun getValidAccessToken(): String? =
        secretStore.getOAuthToken(SECRET_ACCESS_TOKEN)

    /**
     * Attempts to refresh the OAuth access token using the stored refresh token.
     * Serialised with [refreshMutex] to avoid concurrent refresh races.
     * Returns the new access token on success, or null on failure.
     */
    private suspend fun refreshAccessToken(): String? = refreshMutex.withLock {
        val refreshToken = secretStore.getOAuthToken(SECRET_REFRESH_TOKEN) ?: run {
            Log.w(TAG, "No Gmail refresh token stored — cannot refresh.")
            return@withLock null
        }
        val clientId = secretStore.getApiKey(SECRET_CLIENT_ID) ?: run {
            Log.w(TAG, "No Gmail client_id stored — cannot refresh.")
            return@withLock null
        }
        val clientSecret = secretStore.getApiKey(SECRET_CLIENT_SECRET) ?: run {
            Log.w(TAG, "No Gmail client_secret stored — cannot refresh.")
            return@withLock null
        }

        return@withLock try {
            val body =
                "grant_type=refresh_token" +
                    "&refresh_token=${refreshToken}" +
                    "&client_id=${clientId}" +
                    "&client_secret=${clientSecret}"

            val response = httpClient.post(OAUTH_TOKEN_URL) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
            }

            if (response.status != HttpStatusCode.OK) {
                Log.w(TAG, "Token refresh HTTP ${response.status.value}.")
                null
            } else {
                val refreshJson = json.parseToJsonElement(response.bodyAsText())
                    .jsonObject
                val newAccessToken = refreshJson["access_token"]?.jsonPrimitive?.content
                if (newAccessToken != null) {
                    secretStore.saveOAuthToken(SECRET_ACCESS_TOKEN, newAccessToken)
                    Log.i(TAG, "Gmail access token refreshed.")
                }
                newAccessToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAccessToken failed: ${e.message}", e)
            null
        }
    }

    // ── MIME helpers ──────────────────────────────────────────────────────────

    private fun buildRawMimeMessage(to: String, subject: String, body: String): String {
        val mime = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$body"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mime.toByteArray(Charsets.UTF_8))
    }
}

package com.pocketclaw.agent.hitl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TelegramApprovalProvider] covering:
 * - Approve path: getUpdates returns callback_query with data="approve"
 * - Reject path: getUpdates returns callback_query with data="reject"
 * - Timeout path: no updates arrive before timeout
 * - Network error retry: first N-1 calls fail, last succeeds
 * - Missing credentials: returns TimedOut immediately
 */
class TelegramApprovalProviderTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeSecretStore(
        private val botToken: String? = "123:TESTTOKEN",
    ) : com.pocketclaw.core.data.secret.SecretStore {
        override fun saveApiKey(providerId: String, key: String) = Unit
        override fun getApiKey(providerId: String): String? = null
        override fun deleteApiKey(providerId: String) = Unit
        override fun saveBotToken(providerId: String, token: String) = Unit
        override fun getBotToken(providerId: String): String? = botToken
        override fun deleteBotToken(providerId: String) = Unit
        override fun saveWebhookUrl(providerId: String, url: String) = Unit
        override fun getWebhookUrl(providerId: String): String? = null
        override fun deleteWebhookUrl(providerId: String) = Unit
        override fun saveOAuthToken(providerId: String, token: String) = Unit
        override fun getOAuthToken(providerId: String): String? = null
        override fun deleteOAuthToken(providerId: String) = Unit
        override fun clearAll() = Unit
    }

    private fun fakeDataStore(chatId: String? = "999"): DataStore<Preferences> =
        object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(
                emptyPreferences().toMutablePreferences().apply {
                    if (chatId != null) set(TelegramApprovalProvider.PREF_CHAT_ID, chatId)
                },
            )
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(emptyPreferences())
        }

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    private val approvalContext = ApprovalContext(
        taskId = "task-1",
        stepIndex = 0,
        actionDescription = "Click button",
        reasoning = "Required to proceed",
        riskLevel = RiskLevel.LOW,
        timeoutMs = 10_000L,
    )

    // ── Approve path ──────────────────────────────────────────────────────────

    @Test
    fun requestApproval_approvePath_returnsApproved() = runTest {
        var callCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("sendMessage") ->
                    jsonResponse("""{"ok":true,"result":{"message_id":1}}""")
                request.url.encodedPath.contains("getUpdates") -> {
                    callCount++
                    jsonResponse(
                        """{"ok":true,"result":[{"update_id":1,"callback_query":{"id":"cq1","data":"approve"}}]}""",
                    )
                }
                request.url.encodedPath.contains("answerCallbackQuery") ->
                    jsonResponse("""{"ok":true}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val provider = TelegramApprovalProvider(FakeSecretStore(), fakeDataStore(), buildClient(engine))
        val result = provider.requestApproval(approvalContext)
        assertTrue(result is ApprovalResult.Approved)
    }

    // ── Reject path ───────────────────────────────────────────────────────────

    @Test
    fun requestApproval_rejectPath_returnsRejected() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("sendMessage") ->
                    jsonResponse("""{"ok":true,"result":{"message_id":1}}""")
                request.url.encodedPath.contains("getUpdates") ->
                    jsonResponse(
                        """{"ok":true,"result":[{"update_id":2,"callback_query":{"id":"cq2","data":"reject"}}]}""",
                    )
                request.url.encodedPath.contains("answerCallbackQuery") ->
                    jsonResponse("""{"ok":true}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val provider = TelegramApprovalProvider(FakeSecretStore(), fakeDataStore(), buildClient(engine))
        val result = provider.requestApproval(approvalContext)
        assertTrue(result is ApprovalResult.Rejected)
    }

    // ── Timeout path ──────────────────────────────────────────────────────────

    @Test
    fun requestApproval_noUpdates_returnsTimedOut() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("sendMessage") ->
                    jsonResponse("""{"ok":true,"result":{"message_id":1}}""")
                request.url.encodedPath.contains("getUpdates") ->
                    jsonResponse("""{"ok":true,"result":[]}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        // Use a very short timeout so the test completes quickly
        val shortContext = approvalContext.copy(timeoutMs = 1L)
        val provider = TelegramApprovalProvider(FakeSecretStore(), fakeDataStore(), buildClient(engine))
        val result = provider.requestApproval(shortContext)
        assertEquals(ApprovalResult.TimedOut, result)
    }

    // ── Network error retry ───────────────────────────────────────────────────

    @Test
    fun requestApproval_networkErrorOnSendMessage_retriesAndTimesOut() = runTest {
        var sendAttempts = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("sendMessage") -> {
                    sendAttempts++
                    // Always fail sendMessage
                    throw RuntimeException("Simulated network error")
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val shortContext = approvalContext.copy(timeoutMs = 1L)
        val provider = TelegramApprovalProvider(FakeSecretStore(), fakeDataStore(), buildClient(engine))
        val result = provider.requestApproval(shortContext)
        // After MAX_NETWORK_RETRIES (3) failures, should return TimedOut
        assertEquals(ApprovalResult.TimedOut, result)
        assertEquals(3, sendAttempts)
    }

    // ── Missing bot token ─────────────────────────────────────────────────────

    @Test
    fun requestApproval_missingBotToken_returnsTimedOut() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val provider = TelegramApprovalProvider(
            FakeSecretStore(botToken = null),
            fakeDataStore(),
            buildClient(engine),
        )
        val result = provider.requestApproval(approvalContext)
        assertEquals(ApprovalResult.TimedOut, result)
    }

    // ── Missing chat ID ───────────────────────────────────────────────────────

    @Test
    fun requestApproval_missingChatId_returnsTimedOut() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val provider = TelegramApprovalProvider(
            FakeSecretStore(),
            fakeDataStore(chatId = null),
            buildClient(engine),
        )
        val result = provider.requestApproval(approvalContext)
        assertEquals(ApprovalResult.TimedOut, result)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}

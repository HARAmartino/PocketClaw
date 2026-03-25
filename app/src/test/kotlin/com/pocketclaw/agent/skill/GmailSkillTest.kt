package com.pocketclaw.agent.skill

import com.pocketclaw.core.data.secret.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GmailSkill] covering:
 *
 * - Read: successful message list → returns [SkillResult.Success].
 * - Read: missing access token → returns [SkillResult.Failure] (not recoverable).
 * - Read: 401 response → refreshes token and retries.
 * - Read: refresh also fails → [SkillResult.Failure] not recoverable.
 * - Send: successful send → [SkillResult.Success].
 * - Send: missing `to` parameter → [SkillResult.Failure].
 * - Unknown action → [SkillResult.Failure].
 */
class GmailSkillTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeSecretStore(
        private val accessToken: String? = "ACCESS_TOKEN",
        private val refreshToken: String? = "REFRESH_TOKEN",
        private val clientId: String? = "CLIENT_ID",
        private val clientSecret: String? = "CLIENT_SECRET",
    ) : SecretStore {
        override fun saveApiKey(providerId: String, key: String) = Unit
        override fun getApiKey(providerId: String): String? = when (providerId) {
            "gmail_client_id" -> clientId
            "gmail_client_secret" -> clientSecret
            else -> null
        }
        override fun deleteApiKey(providerId: String) = Unit
        override fun saveBotToken(providerId: String, token: String) = Unit
        override fun getBotToken(providerId: String): String? = null
        override fun deleteBotToken(providerId: String) = Unit
        override fun saveWebhookUrl(providerId: String, url: String) = Unit
        override fun getWebhookUrl(providerId: String): String? = null
        override fun deleteWebhookUrl(providerId: String) = Unit
        override fun saveOAuthToken(providerId: String, token: String) = Unit
        override fun getOAuthToken(providerId: String): String? = when (providerId) {
            "gmail" -> accessToken
            "gmail_refresh" -> refreshToken
            else -> null
        }
        override fun deleteOAuthToken(providerId: String) = Unit
        override fun clearAll() = Unit
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    private fun jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    // Minimal successful message list response
    private val messageListJson = """
        {
          "messages": [
            {"id": "msg1", "threadId": "t1"},
            {"id": "msg2", "threadId": "t2"}
          ]
        }
    """.trimIndent()

    private val messageDetailJson = """
        {
          "id": "msg1",
          "snippet": "Hello world",
          "payload": {
            "headers": [
              {"name":"From","value":"sender@example.com"},
              {"name":"Subject","value":"Test Subject"},
              {"name":"Date","value":"2026-03-25"}
            ]
          }
        }
    """.trimIndent()

    // ── Manifest / metadata ───────────────────────────────────────────────────

    @Test
    fun manifest_hasEmailApiCapability() {
        val skill = GmailSkill(FakeSecretStore(), buildClient(MockEngine { respond("", HttpStatusCode.OK) }), json)
        assertTrue(skill.manifest.requiredCapabilities.contains(com.pocketclaw.agent.capability.Capability.EMAIL_API))
    }

    @Test
    fun skillId_isGmail() {
        val skill = GmailSkill(FakeSecretStore(), buildClient(MockEngine { respond("", HttpStatusCode.OK) }), json)
        assertEquals("gmail", skill.skillId)
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Test
    fun execute_read_success_returnsSuccessWithSummaries() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/messages/msg") ->
                    jsonResponse(messageDetailJson)
                request.url.encodedPath.contains("/messages") ->
                    jsonResponse(messageListJson)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val skill = GmailSkill(FakeSecretStore(), buildClient(engine), json)
        val result = skill.execute(mapOf("action" to "read", "query" to "is:unread"))
        assertTrue("Expected Success, got $result", result is SkillResult.Success)
        val output = (result as SkillResult.Success).output
        assertTrue(output.contains("sender@example.com") || output.contains("Test Subject"))
    }

    @Test
    fun execute_read_noAccessToken_returnsFailureNotRecoverable() = runTest {
        val skill = GmailSkill(
            FakeSecretStore(accessToken = null),
            buildClient(MockEngine { respond("", HttpStatusCode.OK) }),
            json,
        )
        val result = skill.execute(mapOf("action" to "read"))
        assertTrue(result is SkillResult.Failure)
        assertEquals(false, (result as SkillResult.Failure).isRecoverable)
    }

    @Test
    fun execute_read_401_refreshesTokenAndRetries() = runTest {
        var callCount = 0
        val newAccessToken = "NEW_ACCESS_TOKEN"
        val engine = MockEngine { request ->
            when {
                request.url.host.contains("oauth2") ->
                    jsonResponse("""{"access_token":"$newAccessToken","expires_in":3600}""")
                request.url.encodedPath.contains("/messages/msg") ->
                    jsonResponse(messageDetailJson)
                request.url.encodedPath.contains("/messages") -> {
                    callCount++
                    if (callCount == 1) {
                        jsonResponse("{}", HttpStatusCode.Unauthorized)
                    } else {
                        jsonResponse(messageListJson)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val skill = GmailSkill(FakeSecretStore(), buildClient(engine), json)
        val result = skill.execute(mapOf("action" to "read"))
        assertTrue("Expected Success after refresh, got $result", result is SkillResult.Success)
    }

    @Test
    fun execute_read_401_noRefreshToken_returnsFailure() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/messages") ->
                    jsonResponse("{}", HttpStatusCode.Unauthorized)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val skill = GmailSkill(
            FakeSecretStore(refreshToken = null),
            buildClient(engine),
            json,
        )
        val result = skill.execute(mapOf("action" to "read"))
        assertTrue(result is SkillResult.Failure)
        assertEquals(false, (result as SkillResult.Failure).isRecoverable)
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    @Test
    fun execute_send_success_returnsSuccess() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("/messages/send") ->
                    jsonResponse("""{"id":"sent1","labelIds":["SENT"]}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val skill = GmailSkill(FakeSecretStore(), buildClient(engine), json)
        val result = skill.execute(
            mapOf(
                "action" to "send",
                "to" to "recipient@example.com",
                "subject" to "Hello",
                "body" to "Test body",
            ),
        )
        assertTrue("Expected Success, got $result", result is SkillResult.Success)
    }

    @Test
    fun execute_send_missingTo_returnsFailureNotRecoverable() = runTest {
        val skill = GmailSkill(
            FakeSecretStore(),
            buildClient(MockEngine { respond("", HttpStatusCode.OK) }),
            json,
        )
        val result = skill.execute(mapOf("action" to "send", "subject" to "Hi"))
        assertTrue(result is SkillResult.Failure)
        assertEquals(false, (result as SkillResult.Failure).isRecoverable)
    }

    @Test
    fun execute_send_noAccessToken_returnsFailureNotRecoverable() = runTest {
        val skill = GmailSkill(
            FakeSecretStore(accessToken = null),
            buildClient(MockEngine { respond("", HttpStatusCode.OK) }),
            json,
        )
        val result = skill.execute(
            mapOf("action" to "send", "to" to "x@y.com", "subject" to "Hi"),
        )
        assertTrue(result is SkillResult.Failure)
        assertEquals(false, (result as SkillResult.Failure).isRecoverable)
    }

    // ── Unknown action ────────────────────────────────────────────────────────

    @Test
    fun execute_unknownAction_returnsFailureNotRecoverable() = runTest {
        val skill = GmailSkill(
            FakeSecretStore(),
            buildClient(MockEngine { respond("", HttpStatusCode.OK) }),
            json,
        )
        val result = skill.execute(mapOf("action" to "delete_everything"))
        assertTrue(result is SkillResult.Failure)
        assertEquals(false, (result as SkillResult.Failure).isRecoverable)
    }

    @Test
    fun execute_missingAction_returnsFailureNotRecoverable() = runTest {
        val skill = GmailSkill(
            FakeSecretStore(),
            buildClient(MockEngine { respond("", HttpStatusCode.OK) }),
            json,
        )
        val result = skill.execute(emptyMap())
        assertTrue(result is SkillResult.Failure)
        assertEquals(false, (result as SkillResult.Failure).isRecoverable)
    }
}

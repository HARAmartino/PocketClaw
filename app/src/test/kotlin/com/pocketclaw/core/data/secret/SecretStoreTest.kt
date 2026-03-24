package com.pocketclaw.core.data.secret

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the [SecretStore] interface contract using an in-memory fake.
 * Covers all typed accessors: API keys, bot tokens, and webhook URLs.
 */
class SecretStoreTest {

    /** In-memory fake that mirrors [SecretStoreImpl] key naming conventions. */
    private class FakeSecretStore : SecretStore {
        private val store = mutableMapOf<String, String>()

        override fun saveApiKey(providerId: String, key: String) {
            store["api_key_$providerId"] = key
        }

        override fun getApiKey(providerId: String): String? = store["api_key_$providerId"]

        override fun deleteApiKey(providerId: String) {
            store.remove("api_key_$providerId")
        }

        override fun saveBotToken(providerId: String, token: String) {
            store["bot_token_$providerId"] = token
        }

        override fun getBotToken(providerId: String): String? = store["bot_token_$providerId"]

        override fun deleteBotToken(providerId: String) {
            store.remove("bot_token_$providerId")
        }

        override fun saveWebhookUrl(providerId: String, url: String) {
            store["webhook_url_$providerId"] = url
        }

        override fun getWebhookUrl(providerId: String): String? = store["webhook_url_$providerId"]

        override fun deleteWebhookUrl(providerId: String) {
            store.remove("webhook_url_$providerId")
        }

        override fun clearAll() {
            store.clear()
        }
    }

    private val store: SecretStore = FakeSecretStore()

    // ── API key tests ────────────────────────────────────────────────────────

    @Test
    fun saveAndGetApiKey_roundTrips() {
        store.saveApiKey("openai", "sk-test-123")
        assertEquals("sk-test-123", store.getApiKey("openai"))
    }

    @Test
    fun deleteApiKey_returnsNull() {
        store.saveApiKey("openai", "sk-test-123")
        store.deleteApiKey("openai")
        assertNull(store.getApiKey("openai"))
    }

    @Test
    fun getApiKey_unknownProvider_returnsNull() {
        assertNull(store.getApiKey("nonexistent"))
    }

    // ── Bot token tests ──────────────────────────────────────────────────────

    @Test
    fun saveAndGetBotToken_roundTrips() {
        store.saveBotToken("telegram", "1234567890:AABBCC")
        assertEquals("1234567890:AABBCC", store.getBotToken("telegram"))
    }

    @Test
    fun deleteBotToken_returnsNull() {
        store.saveBotToken("telegram", "1234567890:AABBCC")
        store.deleteBotToken("telegram")
        assertNull(store.getBotToken("telegram"))
    }

    @Test
    fun getBotToken_unknownProvider_returnsNull() {
        assertNull(store.getBotToken("nonexistent"))
    }

    // ── Webhook URL tests ────────────────────────────────────────────────────

    @Test
    fun saveAndGetWebhookUrl_roundTrips() {
        store.saveWebhookUrl("discord", "https://discord.com/api/webhooks/123/abc")
        assertEquals("https://discord.com/api/webhooks/123/abc", store.getWebhookUrl("discord"))
    }

    @Test
    fun deleteWebhookUrl_returnsNull() {
        store.saveWebhookUrl("discord", "https://discord.com/api/webhooks/123/abc")
        store.deleteWebhookUrl("discord")
        assertNull(store.getWebhookUrl("discord"))
    }

    @Test
    fun getWebhookUrl_unknownProvider_returnsNull() {
        assertNull(store.getWebhookUrl("nonexistent"))
    }

    @Test
    fun webhookUrl_doesNotConflictWithBotToken_sameProviderId() {
        store.saveBotToken("discord", "bot-token-value")
        store.saveWebhookUrl("discord", "https://discord.com/api/webhooks/123/abc")
        assertEquals("bot-token-value", store.getBotToken("discord"))
        assertEquals("https://discord.com/api/webhooks/123/abc", store.getWebhookUrl("discord"))
    }

    // ── clearAll tests ───────────────────────────────────────────────────────

    @Test
    fun clearAll_removesAllSecrets() {
        store.saveApiKey("openai", "sk-test")
        store.saveBotToken("telegram", "bot-token")
        store.saveWebhookUrl("discord", "https://webhook.url")
        store.clearAll()
        assertNull(store.getApiKey("openai"))
        assertNull(store.getBotToken("telegram"))
        assertNull(store.getWebhookUrl("discord"))
    }
}

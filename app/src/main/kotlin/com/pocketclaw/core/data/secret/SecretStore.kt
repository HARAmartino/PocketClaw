package com.pocketclaw.core.data.secret

/**
 * Typed wrapper around [androidx.security.crypto.EncryptedSharedPreferences].
 *
 * ALL API keys and tokens MUST be stored here.
 * DataStore MUST NOT be used for secrets — it is not encrypted.
 *
 * The underlying master key uses AES-256-GCM stored in the Android Keystore
 * (hardware-backed on API 23+ devices).
 */
interface SecretStore {
    fun saveApiKey(providerId: String, key: String)
    fun getApiKey(providerId: String): String?
    fun deleteApiKey(providerId: String)

    fun saveBotToken(providerId: String, token: String)
    fun getBotToken(providerId: String): String?
    fun deleteBotToken(providerId: String)

    fun saveWebhookUrl(providerId: String, url: String)
    fun getWebhookUrl(providerId: String): String?
    fun deleteWebhookUrl(providerId: String)

    /** Stores an OAuth token (access or refresh) for the given [providerId]. */
    fun saveOAuthToken(providerId: String, token: String)

    /** Returns the stored OAuth token for [providerId], or null if not set. */
    fun getOAuthToken(providerId: String): String?

    /** Removes the OAuth token for [providerId]. */
    fun deleteOAuthToken(providerId: String)

    fun clearAll()
}

package com.pocketclaw.core.data.secret

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [SecretStore] backed by [EncryptedSharedPreferences].
 *
 * Keys:
 * - API keys: "api_key_{providerId}"
 * - Bot tokens: "bot_token_{providerId}"
 * - Webhook URLs: "webhook_url_{providerId}"
 *
 * The master key uses [MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SPEC] (AES-256-GCM),
 * stored in the Android Keystore system (hardware-backed on API 23+ devices).
 */
@Singleton
class SecretStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "pocketclaw_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun saveApiKey(providerId: String, key: String) {
        prefs.edit().putString("api_key_$providerId", key).apply()
    }

    override fun getApiKey(providerId: String): String? =
        prefs.getString("api_key_$providerId", null)

    override fun deleteApiKey(providerId: String) {
        prefs.edit().remove("api_key_$providerId").apply()
    }

    override fun saveBotToken(providerId: String, token: String) {
        prefs.edit().putString("bot_token_$providerId", token).apply()
    }

    override fun getBotToken(providerId: String): String? =
        prefs.getString("bot_token_$providerId", null)

    override fun deleteBotToken(providerId: String) {
        prefs.edit().remove("bot_token_$providerId").apply()
    }

    override fun saveWebhookUrl(providerId: String, url: String) {
        prefs.edit().putString("webhook_url_$providerId", url).apply()
    }

    override fun getWebhookUrl(providerId: String): String? =
        prefs.getString("webhook_url_$providerId", null)

    override fun deleteWebhookUrl(providerId: String) {
        prefs.edit().remove("webhook_url_$providerId").apply()
    }

    override fun saveOAuthToken(providerId: String, token: String) {
        prefs.edit().putString("oauth_token_$providerId", token).apply()
    }

    override fun getOAuthToken(providerId: String): String? =
        prefs.getString("oauth_token_$providerId", null)

    override fun deleteOAuthToken(providerId: String) {
        prefs.edit().remove("oauth_token_$providerId").apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }
}

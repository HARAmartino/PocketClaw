package com.pocketclaw.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketclaw.agent.hitl.TelegramApprovalProvider
import com.pocketclaw.agent.llm.provider.LlmException
import com.pocketclaw.agent.llm.provider.LlmProvider
import com.pocketclaw.agent.llm.schema.LlmConfig
import com.pocketclaw.core.data.secret.SecretStore
import com.pocketclaw.ui.dashboard.DashboardViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Preference keys ────────────────────────────────────────────────────────────

internal object SettingsKeys {
    val LLM_PROVIDER_ID = stringPreferencesKey("llm_provider_id")
    val HITL_PROVIDER_ID = stringPreferencesKey("hitl_provider_id")
    val TELEGRAM_CHAT_ID = TelegramApprovalProvider.PREF_CHAT_ID
    val DAILY_TOKEN_BUDGET = intPreferencesKey("daily_token_budget")
    val MAX_ITERATIONS = intPreferencesKey("max_iterations")
    val AUTO_PILOT = DashboardViewModel.AUTO_PILOT_KEY
}

// ── Constants ──────────────────────────────────────────────────────────────────

internal object SettingsDefaults {
    const val LLM_PROVIDER_ID = "openai"
    const val HITL_PROVIDER_ID = "telegram"
    const val DAILY_TOKEN_BUDGET = 100_000
    const val MAX_ITERATIONS = 50
    const val BUDGET_MIN = 1_000
    const val BUDGET_MAX = 500_000
    const val ITERATIONS_MIN = 1
    const val ITERATIONS_MAX = 200
}

// ── LLM provider descriptors ──────────────────────────────────────────────────

data class LlmProviderOption(val id: String, val displayName: String)

val LLM_PROVIDER_OPTIONS = listOf(
    LlmProviderOption("openai", "OpenAI (GPT-4o)"),
    LlmProviderOption("anthropic", "Anthropic (Claude)"),
    LlmProviderOption("custom", "Custom Endpoint"),
)

// ── HITL provider descriptors ─────────────────────────────────────────────────

data class HitlProviderOption(val id: String, val displayName: String)

val HITL_PROVIDER_OPTIONS = listOf(
    HitlProviderOption("telegram", "Telegram Bot"),
    HitlProviderOption("discord", "Discord Webhook"),
)

// ── UI state ──────────────────────────────────────────────────────────────────

/**
 * Snapshot of all settings UI state.
 */
data class SettingsUiState(
    val llmProviderId: String = SettingsDefaults.LLM_PROVIDER_ID,
    val apiKey: String = "",
    val customEndpointUrl: String = "",
    val hitlProviderId: String = SettingsDefaults.HITL_PROVIDER_ID,
    val botToken: String = "",
    val discordWebhookUrl: String = "",
    val telegramChatId: String = "",
    val dailyTokenBudget: Int = SettingsDefaults.DAILY_TOKEN_BUDGET,
    val maxIterations: Int = SettingsDefaults.MAX_ITERATIONS,
    val isAutoPilotEnabled: Boolean = false,
    val apiKeyError: String? = null,
    val isSaving: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secretStore: SecretStore,
    private val dataStore: DataStore<Preferences>,
    private val llmProvider: LlmProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()

            val llmId = prefs[SettingsKeys.LLM_PROVIDER_ID] ?: SettingsDefaults.LLM_PROVIDER_ID
            val hitlId = prefs[SettingsKeys.HITL_PROVIDER_ID] ?: SettingsDefaults.HITL_PROVIDER_ID

            _uiState.update {
                it.copy(
                    llmProviderId = llmId,
                    apiKey = secretStore.getApiKey(llmId)?.mask() ?: "",
                    customEndpointUrl = secretStore.getApiKey("custom_endpoint") ?: "",
                    hitlProviderId = hitlId,
                    botToken = secretStore.getBotToken("telegram")?.mask() ?: "",
                    discordWebhookUrl = secretStore.getWebhookUrl("discord")?.mask() ?: "",
                    telegramChatId = prefs[SettingsKeys.TELEGRAM_CHAT_ID] ?: "",
                    dailyTokenBudget = prefs[SettingsKeys.DAILY_TOKEN_BUDGET]
                        ?: SettingsDefaults.DAILY_TOKEN_BUDGET,
                    maxIterations = prefs[SettingsKeys.MAX_ITERATIONS]
                        ?: SettingsDefaults.MAX_ITERATIONS,
                    isAutoPilotEnabled = prefs[SettingsKeys.AUTO_PILOT] ?: false,
                )
            }
        }
        // Observe auto-pilot in real time (shared with Dashboard)
        viewModelScope.launch {
            dataStore.data.map { it[SettingsKeys.AUTO_PILOT] ?: false }.collect { enabled ->
                _uiState.update { it.copy(isAutoPilotEnabled = enabled) }
            }
        }
    }

    // ── LLM provider ─────────────────────────────────────────────────────────

    fun setLlmProvider(id: String) {
        _uiState.update { it.copy(llmProviderId = id, apiKey = "", apiKeyError = null) }
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.LLM_PROVIDER_ID] = id }
        }
    }

    fun setApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key, apiKeyError = null, connectionTestResult = null) }
    }

    fun setCustomEndpointUrl(url: String) {
        _uiState.update { it.copy(customEndpointUrl = url) }
    }

    fun saveApiKey() {
        val state = _uiState.value
        val trimmed = state.apiKey.trim()
        val error = validateApiKey(state.llmProviderId, trimmed)
        if (error != null) {
            _uiState.update { it.copy(apiKeyError = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            secretStore.saveApiKey(state.llmProviderId, trimmed)
            if (state.llmProviderId == "custom") {
                secretStore.saveApiKey("custom_endpoint", state.customEndpointUrl.trim())
            }
            _uiState.update { it.copy(isSaving = false, apiKeyError = null) }
        }
    }

    // ── HITL provider ─────────────────────────────────────────────────────────

    fun setHitlProvider(id: String) {
        _uiState.update { it.copy(hitlProviderId = id) }
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.HITL_PROVIDER_ID] = id }
        }
    }

    fun setBotToken(token: String) {
        _uiState.update { it.copy(botToken = token) }
    }

    fun saveBotToken() {
        val token = _uiState.value.botToken.trim()
        if (token.isEmpty()) return
        viewModelScope.launch {
            secretStore.saveBotToken("telegram", token)
        }
    }

    fun setDiscordWebhookUrl(url: String) {
        _uiState.update { it.copy(discordWebhookUrl = url) }
    }

    fun saveDiscordWebhookUrl() {
        val url = _uiState.value.discordWebhookUrl.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            secretStore.saveWebhookUrl("discord", url)
        }
    }

    fun setTelegramChatId(chatId: String) {
        _uiState.update { it.copy(telegramChatId = chatId) }
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.TELEGRAM_CHAT_ID] = chatId }
        }
    }

    // ── Budget & iterations ───────────────────────────────────────────────────

    fun setDailyTokenBudget(budget: Int) {
        val clamped = budget.coerceIn(SettingsDefaults.BUDGET_MIN, SettingsDefaults.BUDGET_MAX)
        _uiState.update { it.copy(dailyTokenBudget = clamped) }
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.DAILY_TOKEN_BUDGET] = clamped }
        }
    }

    fun setMaxIterations(iterations: Int) {
        val clamped = iterations.coerceIn(
            SettingsDefaults.ITERATIONS_MIN,
            SettingsDefaults.ITERATIONS_MAX,
        )
        _uiState.update { it.copy(maxIterations = clamped) }
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.MAX_ITERATIONS] = clamped }
        }
    }

    fun setAutoPilot(enabled: Boolean) {
        _uiState.update { it.copy(isAutoPilotEnabled = enabled) }
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.AUTO_PILOT] = enabled }
        }
    }

    // ── Test connection ───────────────────────────────────────────────────────

    fun testConnection() {
        val state = _uiState.value
        val trimmed = state.apiKey.trim()
        val error = validateApiKey(state.llmProviderId, trimmed)
        if (error != null) {
            _uiState.update { it.copy(apiKeyError = error) }
            return
        }

        _uiState.update { it.copy(isTestingConnection = true, connectionTestResult = null) }
        viewModelScope.launch {
            val result = try {
                llmProvider.complete(
                    messages = listOf(
                        com.pocketclaw.agent.llm.schema.Message(
                            role = com.pocketclaw.agent.llm.schema.MessageRole.USER,
                            content = "ping",
                        ),
                    ),
                    tools = emptyList(),
                    config = LlmConfig(maxOutputTokens = 1, systemPrompt = "test"),
                )
                "✅ Connection successful"
            } catch (e: LlmException.AuthError) {
                "❌ Authentication failed — check your API key"
            } catch (e: LlmException.NetworkError) {
                "❌ Network error: ${e.detail}"
            } catch (e: Exception) {
                "❌ Error: ${e.message}"
            }
            _uiState.update { it.copy(isTestingConnection = false, connectionTestResult = result) }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable error string if [key] is invalid for [providerId],
     * or null if the key is acceptable.
     */
    internal fun validateApiKey(providerId: String, key: String): String? {
        if (key.isBlank()) return "API key must not be empty."
        return when (providerId) {
            "openai" -> if (key.startsWith("sk-")) null
            else "OpenAI API keys must start with \"sk-\"."
            "anthropic" -> if (key.startsWith("sk-ant-")) null
            else "Anthropic API keys must start with \"sk-ant-\"."
            else -> null // Custom endpoints: no format restriction
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Masks all but the last 4 characters of a secret string. */
    private fun String.mask(): String = if (length <= 4) "****" else "****${takeLast(4)}"
}

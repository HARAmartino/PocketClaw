package com.pocketclaw.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.pocketclaw.agent.llm.provider.LlmException
import com.pocketclaw.agent.llm.provider.LlmProvider
import com.pocketclaw.agent.llm.schema.LlmConfig
import com.pocketclaw.agent.llm.schema.LlmResponse
import com.pocketclaw.agent.llm.schema.Message
import com.pocketclaw.agent.llm.schema.ToolDefinition
import com.pocketclaw.agent.scheduler.HeartbeatManager
import com.pocketclaw.core.data.secret.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel].
 *
 * Covers:
 * - Save API key validation (empty, wrong format, valid)
 * - Daily token budget bounds clamping
 * - Max iterations bounds clamping
 * - Auto-pilot toggle persisted to DataStore
 * - Test connection: success and auth failure
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeSecretStore : SecretStore {
        private val data = mutableMapOf<String, String>()

        override fun saveApiKey(providerId: String, key: String) {
            data["api_key_$providerId"] = key
        }
        override fun getApiKey(providerId: String): String? = data["api_key_$providerId"]
        override fun deleteApiKey(providerId: String) { data.remove("api_key_$providerId") }
        override fun saveBotToken(providerId: String, token: String) {
            data["bot_token_$providerId"] = token
        }
        override fun getBotToken(providerId: String): String? = data["bot_token_$providerId"]
        override fun deleteBotToken(providerId: String) { data.remove("bot_token_$providerId") }
        override fun saveWebhookUrl(providerId: String, url: String) {
            data["webhook_url_$providerId"] = url
        }
        override fun getWebhookUrl(providerId: String): String? = data["webhook_url_$providerId"]
        override fun deleteWebhookUrl(providerId: String) { data.remove("webhook_url_$providerId") }
        override fun clearAll() { data.clear() }
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val _prefs = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = _prefs.asStateFlow()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(_prefs.value)
            _prefs.value = updated
            return updated
        }
    }

    private class FakeLlmProvider(
        private val shouldThrow: Exception? = null,
    ) : LlmProvider {
        override val providerId = "openai"
        override val displayName = "OpenAI (GPT-4o)"
        override val modelId = "gpt-4o"
        override val maxContextTokens = 128_000
        override val estimatedCostPerMillionInputTokens = 2.50
        override val estimatedCostPerMillionOutputTokens = 10.00

        override suspend fun complete(
            messages: List<Message>,
            tools: List<ToolDefinition>,
            config: LlmConfig,
        ): LlmResponse {
            if (shouldThrow != null) throw shouldThrow
            return LlmResponse(
                rawContent = "pong",
                inputTokens = 1,
                outputTokens = 1,
                providerId = providerId,
                modelId = modelId,
            )
        }
    }

    private class FakeHeartbeatManager : HeartbeatManager {
        var enabled = false
        var intervalMinutes = HeartbeatManager.DEFAULT_INTERVAL_MINUTES
        var prompt = HeartbeatManager.DEFAULT_PROMPT

        override suspend fun enable() { enabled = true }
        override suspend fun disable() { enabled = false }
        override suspend fun setIntervalMinutes(minutes: Int) {
            intervalMinutes = minutes.coerceIn(
                HeartbeatManager.MIN_INTERVAL_MINUTES,
                HeartbeatManager.MAX_INTERVAL_MINUTES,
            )
        }
        override suspend fun setPrompt(prompt: String) { this.prompt = prompt }
        override suspend fun rescheduleAfterExecution() {}
        override suspend fun readPrompt() = prompt
        override suspend fun isEnabled() = enabled
    }

    private lateinit var secretStore: FakeSecretStore
    private lateinit var dataStore: FakeDataStore
    private lateinit var heartbeatManager: FakeHeartbeatManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        secretStore = FakeSecretStore()
        dataStore = FakeDataStore()
        heartbeatManager = FakeHeartbeatManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    private fun buildViewModel(llmProvider: LlmProvider = FakeLlmProvider()): SettingsViewModel =
        SettingsViewModel(secretStore, dataStore, llmProvider, heartbeatManager)

    // ── validateApiKey ────────────────────────────────────────────────────────

    @Test
    fun validateApiKey_emptyKey_returnsError() {
        val vm = buildViewModel()
        assertNotNull(vm.validateApiKey("openai", ""))
    }

    @Test
    fun validateApiKey_openAi_invalidPrefix_returnsError() {
        val vm = buildViewModel()
        assertNotNull(vm.validateApiKey("openai", "invalid-key"))
    }

    @Test
    fun validateApiKey_openAi_validPrefix_returnsNull() {
        val vm = buildViewModel()
        assertNull(vm.validateApiKey("openai", "sk-test-abc123"))
    }

    @Test
    fun validateApiKey_anthropic_invalidPrefix_returnsError() {
        val vm = buildViewModel()
        assertNotNull(vm.validateApiKey("anthropic", "sk-test-123"))
    }

    @Test
    fun validateApiKey_anthropic_validPrefix_returnsNull() {
        val vm = buildViewModel()
        assertNull(vm.validateApiKey("anthropic", "sk-ant-abc123"))
    }

    @Test
    fun validateApiKey_custom_anyNonEmptyValue_returnsNull() {
        val vm = buildViewModel()
        assertNull(vm.validateApiKey("custom", "anything"))
    }

    // ── Save API key ──────────────────────────────────────────────────────────

    @Test
    fun saveApiKey_validOpenAiKey_savedToSecretStore() = runTest {
        val vm = buildViewModel()
        vm.setApiKey("sk-test-saved")
        vm.saveApiKey()
        assertEquals("sk-test-saved", secretStore.getApiKey("openai"))
    }

    @Test
    fun saveApiKey_emptyKey_setsErrorAndDoesNotSave() = runTest {
        val vm = buildViewModel()
        vm.setApiKey("")
        vm.saveApiKey()
        assertNotNull(vm.uiState.value.apiKeyError)
        assertNull(secretStore.getApiKey("openai"))
    }

    @Test
    fun saveApiKey_invalidOpenAiFormat_setsError() = runTest {
        val vm = buildViewModel()
        vm.setApiKey("bad-key")
        vm.saveApiKey()
        assertNotNull(vm.uiState.value.apiKeyError)
    }

    // ── Daily token budget ────────────────────────────────────────────────────

    @Test
    fun setDailyTokenBudget_withinBounds_setsValue() = runTest {
        val vm = buildViewModel()
        vm.setDailyTokenBudget(50_000)
        assertEquals(50_000, vm.uiState.value.dailyTokenBudget)
    }

    @Test
    fun setDailyTokenBudget_belowMin_clampsToMin() = runTest {
        val vm = buildViewModel()
        vm.setDailyTokenBudget(0)
        assertEquals(SettingsDefaults.BUDGET_MIN, vm.uiState.value.dailyTokenBudget)
    }

    @Test
    fun setDailyTokenBudget_aboveMax_clampsToMax() = runTest {
        val vm = buildViewModel()
        vm.setDailyTokenBudget(999_999)
        assertEquals(SettingsDefaults.BUDGET_MAX, vm.uiState.value.dailyTokenBudget)
    }

    // ── Max iterations ────────────────────────────────────────────────────────

    @Test
    fun setMaxIterations_withinBounds_setsValue() = runTest {
        val vm = buildViewModel()
        vm.setMaxIterations(100)
        assertEquals(100, vm.uiState.value.maxIterations)
    }

    @Test
    fun setMaxIterations_belowMin_clampsToMin() = runTest {
        val vm = buildViewModel()
        vm.setMaxIterations(0)
        assertEquals(SettingsDefaults.ITERATIONS_MIN, vm.uiState.value.maxIterations)
    }

    @Test
    fun setMaxIterations_aboveMax_clampsToMax() = runTest {
        val vm = buildViewModel()
        vm.setMaxIterations(9_999)
        assertEquals(SettingsDefaults.ITERATIONS_MAX, vm.uiState.value.maxIterations)
    }

    // ── Auto-pilot ────────────────────────────────────────────────────────────

    @Test
    fun setAutoPilot_true_updatesUiState() = runTest {
        val vm = buildViewModel()
        vm.setAutoPilot(true)
        assertTrue(vm.uiState.value.isAutoPilotEnabled)
    }

    @Test
    fun setAutoPilot_false_updatesUiState() = runTest {
        val vm = buildViewModel()
        vm.setAutoPilot(true)
        vm.setAutoPilot(false)
        assertEquals(false, vm.uiState.value.isAutoPilotEnabled)
    }

    // ── Test connection ───────────────────────────────────────────────────────

    @Test
    fun testConnection_success_setsSuccessResult() = runTest {
        val vm = buildViewModel(FakeLlmProvider())
        vm.setApiKey("sk-test-key")
        vm.testConnection()
        val result = vm.uiState.value.connectionTestResult
        assertNotNull(result)
        assertTrue(result!!.contains("✅"))
    }

    @Test
    fun testConnection_authError_setsFailureResult() = runTest {
        val vm = buildViewModel(FakeLlmProvider(shouldThrow = LlmException.AuthError("openai")))
        vm.setApiKey("sk-bad-key")
        vm.testConnection()
        val result = vm.uiState.value.connectionTestResult
        assertNotNull(result)
        assertTrue(result!!.contains("❌"))
    }

    @Test
    fun testConnection_emptyApiKey_setsApiKeyError() = runTest {
        val vm = buildViewModel()
        vm.setApiKey("")
        vm.testConnection()
        assertNotNull(vm.uiState.value.apiKeyError)
        assertNull(vm.uiState.value.connectionTestResult)
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    @Test
    fun setHeartbeatEnabled_true_updatesState() = runTest {
        val vm = buildViewModel()
        vm.setHeartbeatEnabled(true)
        assertTrue(vm.uiState.value.isHeartbeatEnabled)
        assertTrue(heartbeatManager.enabled)
    }

    @Test
    fun setHeartbeatEnabled_false_updatesState() = runTest {
        val vm = buildViewModel()
        vm.setHeartbeatEnabled(true)
        vm.setHeartbeatEnabled(false)
        assertTrue(!vm.uiState.value.isHeartbeatEnabled)
        assertTrue(!heartbeatManager.enabled)
    }

    @Test
    fun setHeartbeatIntervalMinutes_clampedToMin() = runTest {
        val vm = buildViewModel()
        vm.setHeartbeatIntervalMinutes(1)
        assertEquals(HeartbeatManager.MIN_INTERVAL_MINUTES, vm.uiState.value.heartbeatIntervalMinutes)
    }

    @Test
    fun setHeartbeatIntervalMinutes_clampedToMax() = runTest {
        val vm = buildViewModel()
        vm.setHeartbeatIntervalMinutes(999)
        assertEquals(HeartbeatManager.MAX_INTERVAL_MINUTES, vm.uiState.value.heartbeatIntervalMinutes)
    }

    @Test
    fun setHeartbeatPrompt_updatesState() = runTest {
        val vm = buildViewModel()
        vm.setHeartbeatPrompt("Check everything")
        assertEquals("Check everything", vm.uiState.value.heartbeatPrompt)
        assertEquals("Check everything", heartbeatManager.prompt)
    }
}

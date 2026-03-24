package com.pocketclaw.agent.llm.provider

import com.pocketclaw.agent.llm.schema.LlmConfig
import com.pocketclaw.agent.llm.schema.LlmResponse
import com.pocketclaw.agent.llm.schema.Message
import com.pocketclaw.agent.llm.schema.ToolDefinition

/**
 * Abstraction for LLM inference backends.
 * Two implementations ship: [OpenAiCompatibleProvider] (covers OpenAI, Ollama, LM Studio)
 * and [AnthropicProvider]. Both are Hilt-injectable and selectable from the Settings UI.
 *
 * Provider ID is stored in DataStore (non-sensitive).
 * API key is stored in [com.pocketclaw.core.data.secret.SecretStore] (EncryptedSharedPreferences).
 */
interface LlmProvider {
    val providerId: String
    val displayName: String
    val modelId: String
    val maxContextTokens: Int
    val estimatedCostPerMillionInputTokens: Double
    val estimatedCostPerMillionOutputTokens: Double

    /**
     * Send a completion request to the LLM.
     * @return [LlmResponse] containing the raw JSON string and token usage.
     * @throws [com.pocketclaw.agent.llm.provider.LlmException] on network or API errors.
     */
    suspend fun complete(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): LlmResponse
}

/** Errors thrown by LLM provider implementations. */
sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class NetworkError(val detail: String, val cause0: Throwable? = null) :
        LlmException("LLM network error: $detail", cause0)
    data class RateLimitError(val retryAfterMs: Long) :
        LlmException("LLM rate limited; retry after ${retryAfterMs}ms")
    data class AuthError(val providerId: String) :
        LlmException("LLM authentication failed for provider '$providerId'. Check API key.")
    data class ContextLengthExceeded(val maxTokens: Int) :
        LlmException("LLM context length exceeded ($maxTokens tokens max).")
    data class UnexpectedResponse(val body: String) :
        LlmException("Unexpected LLM response: $body")
}

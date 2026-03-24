package com.pocketclaw.agent.llm.provider

import com.pocketclaw.agent.llm.schema.LlmConfig
import com.pocketclaw.agent.llm.schema.LlmResponse
import com.pocketclaw.agent.llm.schema.Message
import com.pocketclaw.agent.llm.schema.MessageRole
import com.pocketclaw.agent.llm.schema.ToolDefinitionimport com.pocketclaw.core.data.secret.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * LLM provider for the Anthropic Messages API (Claude models).
 * API key stored in [SecretStore] under providerId.
 */
class AnthropicProvider(
    override val providerId: String,
    override val displayName: String,
    override val modelId: String,
    override val maxContextTokens: Int,
    override val estimatedCostPerMillionInputTokens: Double,
    override val estimatedCostPerMillionOutputTokens: Double,
    private val secretStore: SecretStore,
    private val httpClient: HttpClient,
) : LlmProvider {

    companion object {
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_API_VERSION = "2023-06-01"
        private const val ANTHROPIC_HEADER_KEY = "x-api-key"
        private const val ANTHROPIC_HEADER_VERSION = "anthropic-version"
    }

    @Serializable
    private data class AnthropicMessage(val role: String, val content: String)

    @Serializable
    private data class AnthropicRequest(
        val model: String,
        val max_tokens: Int,
        val system: String,
        val messages: List<AnthropicMessage>,
    )

    @Serializable
    private data class AnthropicResponse(
        val content: List<ContentBlock>,
        val usage: AnthropicUsage,
    )

    @Serializable
    private data class ContentBlock(val type: String, val text: String = "")

    @Serializable
    private data class AnthropicUsage(val input_tokens: Int, val output_tokens: Int)

    override suspend fun complete(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): LlmResponse {
        val apiKey = secretStore.getApiKey(providerId)
            ?: throw LlmException.AuthError(providerId)

        val anthropicMessages = messages
            .filter { it.role != MessageRole.SYSTEM }
            .map { AnthropicMessage(it.role.name.lowercase(), it.content) }

        val response: HttpResponse = httpClient.post(ANTHROPIC_API_URL) {
            contentType(ContentType.Application.Json)
            header(ANTHROPIC_HEADER_KEY, apiKey)
            header(ANTHROPIC_HEADER_VERSION, ANTHROPIC_API_VERSION)
            setBody(
                AnthropicRequest(
                    model = modelId,
                    max_tokens = config.maxOutputTokens,
                    system = config.systemPrompt,
                    messages = anthropicMessages,
                ),
            )
        }

        when (response.status) {
            HttpStatusCode.Unauthorized -> throw LlmException.AuthError(providerId)
            HttpStatusCode.TooManyRequests -> throw LlmException.RateLimitError(retryAfterMs = 60_000L)
            else -> if (!response.status.value.toString().startsWith("2")) {
                throw LlmException.UnexpectedResponse("HTTP ${response.status}")
            }
        }

        val parsed = response.body<AnthropicResponse>()
        val text = parsed.content.firstOrNull { it.type == "text" }?.text
            ?: throw LlmException.UnexpectedResponse("No text block in Anthropic response")

        return LlmResponse(
            rawContent = text,
            inputTokens = parsed.usage.input_tokens,
            outputTokens = parsed.usage.output_tokens,
            providerId = providerId,
            modelId = modelId,
        )
    }
}

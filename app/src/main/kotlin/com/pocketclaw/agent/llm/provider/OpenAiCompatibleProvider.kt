package com.pocketclaw.agent.llm.provider

import com.pocketclaw.agent.llm.schema.LlmConfig
import com.pocketclaw.agent.llm.schema.LlmResponse
import com.pocketclaw.agent.llm.schema.Message
import com.pocketclaw.agent.llm.schema.ToolDefinition
import com.pocketclaw.core.data.secret.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * LLM provider for OpenAI-compatible endpoints.
 * Covers OpenAI, Ollama (local), LM Studio, and any endpoint following the
 * OpenAI Chat Completions API format.
 *
 * [baseUrl] defaults to the OpenAI API but can be overridden for local endpoints.
 */
class OpenAiCompatibleProvider(
    override val providerId: String,
    override val displayName: String,
    override val modelId: String,
    override val maxContextTokens: Int,
    override val estimatedCostPerMillionInputTokens: Double,
    override val estimatedCostPerMillionOutputTokens: Double,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val secretStore: SecretStore,
    private val httpClient: HttpClient,
) : LlmProvider {

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val max_tokens: Int,
        val temperature: Double,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice>,
        val usage: Usage,
    )

    @Serializable
    private data class Choice(val message: ChatMessage)

    @Serializable
    private data class Usage(val prompt_tokens: Int, val completion_tokens: Int)

    override suspend fun complete(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): LlmResponse {
        val apiKey = secretStore.getApiKey(providerId)
            ?: throw LlmException.AuthError(providerId)

        val allMessages = buildList {
            add(ChatMessage("system", config.systemPrompt))
            addAll(messages.map { ChatMessage(it.role.name.lowercase(), it.content) })
        }

        val response: HttpResponse = httpClient.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            bearerAuth(apiKey)
            setBody(
                ChatCompletionRequest(
                    model = modelId,
                    messages = allMessages,
                    max_tokens = config.maxOutputTokens,
                    temperature = config.temperature,
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

        val parsed = response.body<ChatCompletionResponse>()
        val content = parsed.choices.firstOrNull()?.message?.content
            ?: throw LlmException.UnexpectedResponse("No choices in response")

        return LlmResponse(
            rawContent = content,
            inputTokens = parsed.usage.prompt_tokens,
            outputTokens = parsed.usage.completion_tokens,
            providerId = providerId,
            modelId = modelId,
        )
    }
}

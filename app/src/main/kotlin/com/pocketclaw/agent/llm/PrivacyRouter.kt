package com.pocketclaw.agent.llm

import com.pocketclaw.agent.llm.provider.LlmProvider
import com.pocketclaw.core.data.db.entity.TaskType
import javax.inject.Inject

/**
 * Decides which [LlmProvider] to use for a given request.
 *
 * Inspired by NemoClaw's privacy router concept:
 * route sensitive tasks to local models (Ollama/LM Studio),
 * non-sensitive tasks to cloud models (OpenAI/Anthropic).
 *
 * Default implementation is [PassthroughPrivacyRouter] (no routing).
 * To swap: change 1 line in AppModule.kt Hilt binding.
 */
interface PrivacyRouter {
    fun route(context: RoutingContext): LlmProvider
}

/** Context provided to [PrivacyRouter.route] when selecting a provider. */
data class RoutingContext(
    val taskType: TaskType,
    val containsPersonalData: Boolean,
    val preferLocal: Boolean,
)

/** Default implementation: always delegates to the configured [LlmProvider]. */
class PassthroughPrivacyRouter @Inject constructor(
    private val provider: LlmProvider,
) : PrivacyRouter {
    override fun route(context: RoutingContext): LlmProvider = provider
}

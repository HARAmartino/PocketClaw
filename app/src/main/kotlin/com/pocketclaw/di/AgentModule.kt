package com.pocketclaw.di

import com.pocketclaw.agent.hitl.RemoteApprovalProvider
import com.pocketclaw.agent.hitl.TelegramApprovalProvider
import com.pocketclaw.agent.llm.provider.LlmProvider
import com.pocketclaw.agent.llm.provider.OpenAiCompatibleProvider
import com.pocketclaw.core.data.secret.SecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * Binds the default [LlmProvider] (OpenAI-compatible) and [RemoteApprovalProvider] (Telegram).
 * In a future phase, these will be selectable from the Settings UI with the active choice
 * persisted in DataStore.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun provideLlmProvider(
        secretStore: SecretStore,
        httpClient: HttpClient,
    ): LlmProvider = OpenAiCompatibleProvider(
        providerId = "openai",
        displayName = "OpenAI (GPT-4o)",
        modelId = "gpt-4o",
        maxContextTokens = 128_000,
        estimatedCostPerMillionInputTokens = 2.50,
        estimatedCostPerMillionOutputTokens = 10.00,
        baseUrl = "https://api.openai.com/v1",
        secretStore = secretStore,
        httpClient = httpClient,
    )

    @Provides
    @Singleton
    fun provideRemoteApprovalProvider(
        telegramProvider: TelegramApprovalProvider,
    ): RemoteApprovalProvider = telegramProvider
}

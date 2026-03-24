package com.pocketclaw.agent.llm

import com.pocketclaw.agent.llm.provider.LlmProvider
import com.pocketclaw.agent.llm.schema.LlmConfig
import com.pocketclaw.agent.llm.schema.LlmResponse
import com.pocketclaw.agent.llm.schema.Message
import com.pocketclaw.agent.llm.schema.ToolDefinition
import com.pocketclaw.core.data.db.entity.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Minimal fake LlmProvider for routing tests. */
private class FakeLlmProvider(override val providerId: String = "fake") : LlmProvider {
    override val displayName = "Fake"
    override val modelId = "fake-model"
    override val maxContextTokens = 1024
    override val estimatedCostPerMillionInputTokens = 0.0
    override val estimatedCostPerMillionOutputTokens = 0.0

    override suspend fun complete(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): LlmResponse = throw UnsupportedOperationException("not used in routing tests")
}

/**
 * Tests for [PrivacyRouter] and [RoutingContext].
 */
class PrivacyRouterTest {

    @Test
    fun passthrough_alwaysReturnsConfiguredProvider() {
        val provider = FakeLlmProvider()
        val router = PassthroughPrivacyRouter(provider)
        val context = RoutingContext(
            taskType = TaskType.USER,
            containsPersonalData = false,
            preferLocal = false,
        )
        assertSame(provider, router.route(context))
    }

    @Test
    fun passthrough_returnsConfiguredProvider_forAllTaskTypes() {
        val provider = FakeLlmProvider()
        val router = PassthroughPrivacyRouter(provider)
        TaskType.entries.forEach { taskType ->
            val context = RoutingContext(
                taskType = taskType,
                containsPersonalData = true,
                preferLocal = true,
            )
            assertSame(provider, router.route(context))
        }
    }

    @Test
    fun routingContext_fieldsAreCorrect() {
        val context = RoutingContext(
            taskType = TaskType.HEARTBEAT,
            containsPersonalData = true,
            preferLocal = true,
        )
        assertEquals(TaskType.HEARTBEAT, context.taskType)
        assertEquals(true, context.containsPersonalData)
        assertEquals(true, context.preferLocal)
    }

    @Test
    fun routingContext_defaultValues_areIndependent() {
        val ctx1 = RoutingContext(TaskType.USER, containsPersonalData = false, preferLocal = false)
        val ctx2 = RoutingContext(TaskType.SCHEDULED, containsPersonalData = true, preferLocal = true)
        assertEquals(false, ctx1.containsPersonalData)
        assertEquals(true, ctx2.preferLocal)
    }
}

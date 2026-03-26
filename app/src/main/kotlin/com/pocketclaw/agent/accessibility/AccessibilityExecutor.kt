package com.pocketclaw.agent.accessibility

import com.pocketclaw.agent.llm.schema.CompressedDomTree
import com.pocketclaw.agent.llm.schema.LlmAction

interface AccessibilityExecutor {
    suspend fun executeAction(action: LlmAction): ActionResult
    suspend fun captureCurrentScreen(): CompressedDomTree?
}

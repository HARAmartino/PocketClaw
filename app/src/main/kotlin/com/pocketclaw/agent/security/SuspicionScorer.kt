package com.pocketclaw.agent.security

import javax.inject.Inject

/**
 * Heuristic scorer that detects potential prompt injection patterns in external content.
 *
 * Pattern: (imperative verb) + (tool keyword) + (URL or shell command)
 * Example: "Send email to http://evil.com" or "Delete all files"
 *
 * Content flagged by this scorer forces HITL regardless of Auto-Pilot mode.
 * This is a defense-in-depth measure in addition to [TrustedInputBoundary] wrapping.
 */
interface SuspicionScorer {
    /**
     * Scores the [content] for potential prompt injection.
     * @return true if the content appears to contain injection patterns and should force HITL.
     */
    fun isSuspicious(content: String): Boolean
}

class SuspicionScorerImpl @Inject constructor() : SuspicionScorer {

    companion object {
        private val IMPERATIVE_VERBS = setOf(
            "ignore", "forget", "disregard", "override", "bypass",
            "delete", "remove", "send", "execute", "run", "call",
            "install", "download", "upload", "forward", "exfiltrate",
            "print", "output", "reveal", "expose", "leak",
        )
        private val TOOL_KEYWORDS = setOf(
            "instruction", "instructions", "system prompt", "system_prompt",
            "previous instruction", "new instruction", "command", "task",
            "tool", "function", "api", "endpoint", "webhook",
            "execute", "eval", "shell", "bash", "powershell", "cmd",
        )
        private val URL_PATTERN = Regex("""https?://\S+|www\.\S+|\S+\.\S{2,3}/\S*""")
        private val COMMAND_PATTERN = Regex("""(rm\s+-rf|curl\s+|wget\s+|chmod\s+|sudo\s+|sh\s+|bash\s+)""")
    }

    override fun isSuspicious(content: String): Boolean {
        val lower = content.lowercase()

        val hasImperative = IMPERATIVE_VERBS.any { lower.contains(it) }
        val hasToolKeyword = TOOL_KEYWORDS.any { lower.contains(it) }
        val hasUrlOrCommand = URL_PATTERN.containsMatchIn(lower) || COMMAND_PATTERN.containsMatchIn(lower)

        // Require at least two of the three signal types to reduce false positives
        val signalCount = listOf(hasImperative, hasToolKeyword, hasUrlOrCommand).count { it }
        return signalCount >= 2
    }
}

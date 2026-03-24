package com.pocketclaw.agent.security

/** Identifies the source of untrusted external content. */
enum class InputSource { NOTIFICATION, WEB, FILE, API }

/**
 * Wraps all external content in an XML envelope before it is included in LLM prompts.
 * This is a mandatory Layer 4 security control — no external content may reach
 * the LLM without passing through this boundary.
 *
 * Format:
 * ```xml
 * <untrusted_data source="{source}" timestamp="{iso8601}">
 * {raw_external_content}
 * </untrusted_data>
 * ```
 *
 * The system prompt instructs the LLM to treat everything inside
 * `<untrusted_data>` as data only, never as instructions.
 */
interface TrustedInputBoundary {
    /**
     * Wraps [rawContent] in the untrusted data envelope.
     * @param rawContent The raw external content (notification text, web page, file content, etc.)
     * @param source The origin of the content.
     * @param timestamp ISO-8601 timestamp of when the content was received.
     */
    fun wrap(rawContent: String, source: InputSource, timestamp: String): String

    /**
     * Returns the raw content for logging purposes (strips the XML envelope).
     * MUST NOT be used to feed content back to the LLM.
     */
    fun unwrapForLogging(wrapped: String): String
}

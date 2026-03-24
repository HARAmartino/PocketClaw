package com.pocketclaw.agent.security

import javax.inject.Inject

/** Production implementation of [TrustedInputBoundary]. */
class TrustedInputBoundaryImpl @Inject constructor() : TrustedInputBoundary {

    override fun wrap(rawContent: String, source: InputSource, timestamp: String): String =
        """<untrusted_data source="${source.name.lowercase()}" timestamp="$timestamp">
$rawContent
</untrusted_data>"""

    override fun unwrapForLogging(wrapped: String): String {
        val startTag = Regex("<untrusted_data[^>]*>")
        val endTag = "</untrusted_data>"
        val withoutStart = startTag.replace(wrapped, "").trim()
        return withoutStart.removeSuffix(endTag).trim()
    }
}

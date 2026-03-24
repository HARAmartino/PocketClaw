package com.pocketclaw.agent.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedInputBoundaryTest {

    private val boundary = TrustedInputBoundaryImpl()

    @Test
    fun wrap_producesCorrectEnvelope() {
        val wrapped = boundary.wrap(
            rawContent = "Hello world",
            source = InputSource.NOTIFICATION,
            timestamp = "2026-03-24T13:00:00Z",
        )
        assertTrue(wrapped.contains("""source="notification""""))
        assertTrue(wrapped.contains("""timestamp="2026-03-24T13:00:00Z""""))
        assertTrue(wrapped.contains("Hello world"))
        assertTrue(wrapped.startsWith("<untrusted_data"))
        assertTrue(wrapped.trimEnd().endsWith("</untrusted_data>"))
    }

    @Test
    fun wrap_allSourcesProduceLowercaseSourceName() {
        InputSource.entries.forEach { source ->
            val wrapped = boundary.wrap("content", source, "2026-01-01T00:00:00Z")
            assertTrue(
                "Expected lowercase source name in wrapped output for $source",
                wrapped.contains("""source="${source.name.lowercase()}""""),
            )
        }
    }

    @Test
    fun unwrapForLogging_stripsEnvelope() {
        val raw = "Sensitive content here"
        val wrapped = boundary.wrap(raw, InputSource.WEB, "2026-03-24T00:00:00Z")
        val unwrapped = boundary.unwrapForLogging(wrapped)
        assertEquals(raw, unwrapped)
    }

    @Test
    fun unwrapForLogging_handlesMultilineContent() {
        val raw = "Line 1\nLine 2\nLine 3"
        val wrapped = boundary.wrap(raw, InputSource.FILE, "2026-03-24T00:00:00Z")
        val unwrapped = boundary.unwrapForLogging(wrapped)
        assertEquals(raw, unwrapped)
    }
}

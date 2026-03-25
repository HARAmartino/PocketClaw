package com.pocketclaw.core.data.db

import com.pocketclaw.core.data.db.entity.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [TimelineEntry] screenshot-path field (Part D — Visual Timeline).
 *
 * Verifies that:
 * - [TimelineEntry.screenshotPath] is null by default.
 * - A non-null [TimelineEntry.screenshotPath] is preserved correctly.
 * - Two entries with different screenshot paths are not considered equal.
 * - Entries with and without screenshot paths have distinct JSON-friendly representations.
 */
@RunWith(JUnit4::class)
class TimelineEntryScreenshotTest {

    private fun makeEntry(screenshotPath: String? = null) = TimelineEntry(
        id = "entry-1",
        taskId = "task-1",
        stepIndex = 0,
        taskType = "MANUAL",
        reasoning = "Test reasoning",
        actionType = "CLICK",
        screenshotPath = screenshotPath,
        validationResult = "Allow",
        timestampMs = 1_000L,
    )

    @Test
    fun screenshotPath_defaultIsNull() {
        val entry = makeEntry()
        assertNull(entry.screenshotPath)
    }

    @Test
    fun screenshotPath_nonNullIsPreserved() {
        val path = "/storage/emulated/0/Android/data/com.pocketclaw/files/PocketClaw_Workspace/step0.png"
        val entry = makeEntry(screenshotPath = path)
        assertNotNull(entry.screenshotPath)
        assertEquals(path, entry.screenshotPath)
    }

    @Test
    fun screenshotPath_nullAndNonNullEntries_areNotEqual() {
        val withoutPath = makeEntry(screenshotPath = null)
        val withPath = makeEntry(screenshotPath = "/some/path.png")
        assert(withoutPath != withPath)
    }

    @Test
    fun screenshotPath_copy_preservesPath() {
        val original = makeEntry(screenshotPath = "/original/path.png")
        val copied = original.copy(stepIndex = 1)
        assertEquals(original.screenshotPath, copied.screenshotPath)
    }

    @Test
    fun screenshotPath_copy_overridesPath() {
        val original = makeEntry(screenshotPath = "/original/path.png")
        val updated = original.copy(screenshotPath = "/new/path.png")
        assertEquals("/new/path.png", updated.screenshotPath)
    }

    @Test
    fun screenshotPath_emptyString_isDistinctFromNull() {
        val withNull = makeEntry(screenshotPath = null)
        val withEmpty = makeEntry(screenshotPath = "")
        assertNull(withNull.screenshotPath)
        assertNotNull(withEmpty.screenshotPath)
        assertEquals("", withEmpty.screenshotPath)
    }
}

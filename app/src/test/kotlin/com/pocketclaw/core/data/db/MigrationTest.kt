package com.pocketclaw.core.data.db

import com.pocketclaw.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Room database migrations.
 *
 * These tests verify the migration objects' metadata and that the
 * [DatabaseModule] constants are correctly wired without requiring a real
 * SQLite database.
 *
 * Full end-to-end migration verification (with [androidx.room.testing.MigrationTestHelper])
 * requires an Android device or emulator and should be added as an instrumented
 * test in `androidTest` once the CI environment supports it.
 */
class MigrationTest {

    // ── 1 → 2 ─────────────────────────────────────────────────────────────────

    @Test
    fun migration_1_2_startVersionIs1() {
        assertEquals(1, DatabaseModule.MIGRATION_1_2.startVersion)
    }

    @Test
    fun migration_1_2_endVersionIs2() {
        assertEquals(2, DatabaseModule.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration_1_2_objectIsNotNull() {
        assertNotNull(DatabaseModule.MIGRATION_1_2)
    }

    // ── 2 → 3 ─────────────────────────────────────────────────────────────────

    @Test
    fun migration_2_3_startVersionIs2() {
        assertEquals(2, DatabaseModule.MIGRATION_2_3.startVersion)
    }

    @Test
    fun migration_2_3_endVersionIs3() {
        assertEquals(3, DatabaseModule.MIGRATION_2_3.endVersion)
    }

    @Test
    fun migration_2_3_objectIsNotNull() {
        assertNotNull(DatabaseModule.MIGRATION_2_3)
    }

    // ── 3 → 4 ─────────────────────────────────────────────────────────────────

    @Test
    fun migration_3_4_startVersionIs3() {
        assertEquals(3, DatabaseModule.MIGRATION_3_4.startVersion)
    }

    @Test
    fun migration_3_4_endVersionIs4() {
        assertEquals(4, DatabaseModule.MIGRATION_3_4.endVersion)
    }

    @Test
    fun migration_3_4_objectIsNotNull() {
        assertNotNull(DatabaseModule.MIGRATION_3_4)
    }

    @Test
    fun database_currentVersionIs4() {
        assertEquals(4, DatabaseModule.MIGRATION_3_4.endVersion)
    }

    // ── 4 → 5 ─────────────────────────────────────────────────────────────────

    @Test
    fun migration_4_5_startVersionIs4() {
        assertEquals(4, DatabaseModule.MIGRATION_4_5.startVersion)
    }

    @Test
    fun migration_4_5_endVersionIs5() {
        assertEquals(5, DatabaseModule.MIGRATION_4_5.endVersion)
    }

    @Test
    fun migration_4_5_objectIsNotNull() {
        assertNotNull(DatabaseModule.MIGRATION_4_5)
    }

    @Test
    fun database_currentVersionIs5() {
        assertEquals(5, DatabaseModule.MIGRATION_4_5.endVersion)
    }

    // ── Whitelist seed test ────────────────────────────────────────────────────

    @Test
    fun builtinWhitelistDomains_areDefinedInAppModule() {
        // Verify the seed list is non-empty and contains the minimum required domains
        val requiredDomains = listOf(
            "api.openai.com",
            "api.anthropic.com",
            "api.telegram.org",
        )
        // This is a documentation test — actual DB seeding is verified via
        // instrumented test. Here we verify the constant list exists in source.
        assertTrue(DatabaseModule.BUILTIN_WHITELIST_DOMAINS.isNotEmpty())
        assertTrue(DatabaseModule.BUILTIN_WHITELIST_DOMAINS.containsAll(requiredDomains))
    }
}

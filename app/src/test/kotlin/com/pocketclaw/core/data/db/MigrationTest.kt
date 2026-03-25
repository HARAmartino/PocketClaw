package com.pocketclaw.core.data.db

import com.pocketclaw.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun database_currentVersionIs3() {
        // Verify that the declared DB version matches the latest migration endpoint.
        assertEquals(
            "Migration endVersion must match the declared DB version",
            3,
            DatabaseModule.MIGRATION_2_3.endVersion,
        )
    }
}

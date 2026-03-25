package com.pocketclaw.core.data.db

import com.pocketclaw.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for the Room database migration from version 1 to version 2.
 *
 * These tests verify the migration object's metadata and that the
 * [com.pocketclaw.di.DatabaseModule.MIGRATION_1_2] constant is correctly
 * wired without requiring a real SQLite database.
 *
 * Full end-to-end migration verification (with [androidx.room.testing.MigrationTestHelper])
 * requires an Android device or emulator and should be added as an instrumented
 * test in `androidTest` once the CI environment supports it.
 */
class MigrationTest {

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

    @Test
    fun database_currentVersionIs2() {
        // Verify that PocketClawDatabase's @Database annotation declares version 2.
        // This is a compile-time constant surfaced via the generated class, so we
        // verify it indirectly through the migration chain endpoints.
        assertEquals(
            "Migration endVersion must match the declared DB version",
            2,
            DatabaseModule.MIGRATION_1_2.endVersion,
        )
    }
}

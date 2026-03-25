package com.pocketclaw

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies that [app/proguard-rules.pro] contains keep rules for all
 * critical classes that must survive R8 minification in release builds.
 */
class ProGuardRulesTest {

    private val proguardRules: String by lazy {
        // Resolve relative to the project root so the test works from any working directory.
        val candidates = listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro"),
            File("../app/proguard-rules.pro"),
        )
        candidates.firstOrNull { it.exists() }?.readText()
            ?: error("proguard-rules.pro not found in any of: $candidates")
    }

    // ── Hilt ─────────────────────────────────────────────────────────────────

    @Test
    fun proguardRules_containsHiltKeepRule() {
        assertTrue(
            "Expected keep rule for dagger.hilt",
            proguardRules.contains("dagger.hilt"),
        )
    }

    // ── Room ──────────────────────────────────────────────────────────────────

    @Test
    fun proguardRules_containsRoomEntityKeepRule() {
        assertTrue(
            "Expected keep rule for androidx.room.Entity",
            proguardRules.contains("androidx.room.Entity"),
        )
    }

    @Test
    fun proguardRules_containsRoomDaoKeepRule() {
        assertTrue(
            "Expected keep rule for androidx.room.Dao",
            proguardRules.contains("androidx.room.Dao"),
        )
    }

    // ── kotlinx.serialization ─────────────────────────────────────────────────

    @Test
    fun proguardRules_containsKotlinxSerializationKeepRule() {
        assertTrue(
            "Expected keep rule for kotlinx.serialization",
            proguardRules.contains("kotlinx.serialization"),
        )
    }

    @Test
    fun proguardRules_containsSerializableAnnotationKeepRule() {
        assertTrue(
            "Expected keep rule for @Serializable annotated classes",
            proguardRules.contains("kotlinx.serialization.Serializable"),
        )
    }

    // ── Ktor ──────────────────────────────────────────────────────────────────

    @Test
    fun proguardRules_containsKtorKeepRule() {
        assertTrue(
            "Expected keep rule for io.ktor",
            proguardRules.contains("io.ktor"),
        )
    }

    @Test
    fun proguardRules_containsKtorOkHttpEngineKeepRule() {
        assertTrue(
            "Expected keep rule for io.ktor.client.engine.okhttp",
            proguardRules.contains("io.ktor.client.engine.okhttp"),
        )
    }

    // ── PocketClaw LLM schema classes ─────────────────────────────────────────

    @Test
    fun proguardRules_containsLlmActionKeepRule() {
        assertTrue(
            "Expected keep rule for LlmAction",
            proguardRules.contains("LlmAction"),
        )
    }

    @Test
    fun proguardRules_containsLlmToolCallKeepRule() {
        assertTrue(
            "Expected keep rule for LlmToolCall",
            proguardRules.contains("LlmToolCall"),
        )
    }

    @Test
    fun proguardRules_containsLlmSchemaPackageKeepRule() {
        assertTrue(
            "Expected keep rule for com.pocketclaw.agent.llm.schema",
            proguardRules.contains("com.pocketclaw.agent.llm.schema"),
        )
    }

    // ── EncryptedSharedPreferences / MasterKey ────────────────────────────────

    @Test
    fun proguardRules_containsEncryptedSharedPreferencesKeepRule() {
        assertTrue(
            "Expected keep rule for androidx.security.crypto.EncryptedSharedPreferences",
            proguardRules.contains("EncryptedSharedPreferences"),
        )
    }

    @Test
    fun proguardRules_containsMasterKeyKeepRule() {
        assertTrue(
            "Expected keep rule for androidx.security.crypto.MasterKey",
            proguardRules.contains("MasterKey"),
        )
    }
}

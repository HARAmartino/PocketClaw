package com.pocketclaw.agent.validator

import com.pocketclaw.core.data.db.entity.WhitelistEntry
import com.pocketclaw.core.data.db.dao.WhitelistStoreDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal in-memory stub used in unit tests. */
private class FakeWhitelistStoreDao(private val allowedDomains: Set<String> = emptySet()) : WhitelistStoreDao {
    override suspend fun add(entry: WhitelistEntry) = Unit
    override suspend fun remove(entry: WhitelistEntry) = Unit
    override suspend fun isDomainAllowed(domain: String): Int =
        if (domain in allowedDomains) 1 else 0
    override fun observeAll(): Flow<List<WhitelistEntry>> = flowOf(emptyList())
}

class ActionValidatorTest {

    private fun validator(allowedDomains: Set<String> = emptySet()) =
        ActionValidatorImpl(FakeWhitelistStoreDao(allowedDomains))

    // ── Hard Deny tests ─────────────────────────────────────────────────────

    @Test
    fun packageInstall_isHardDenied() {
        val result = validator().validate(
            PendingAction(type = ActionType.PACKAGE_INSTALL, rawPayload = "install.apk"),
        )
        assertTrue(result is ValidationResult.HardDeny)
    }

    @Test
    fun packageUninstall_isHardDenied() {
        val result = validator().validate(
            PendingAction(type = ActionType.PACKAGE_UNINSTALL, rawPayload = "com.example.app"),
        )
        assertTrue(result is ValidationResult.HardDeny)
    }

    @Test
    fun settingsChange_isHardDenied() {
        val result = validator().validate(
            PendingAction(type = ActionType.SETTINGS_CHANGE, rawPayload = "brightness=50"),
        )
        assertTrue(result is ValidationResult.HardDeny)
    }

    @Test
    fun selfModification_isHardDenied() {
        val result = validator().validate(
            PendingAction(
                type = ActionType.ACCESSIBILITY_CLICK,
                targetPackage = "com.pocketclaw",
                rawPayload = "click own UI",
            ),
        )
        assertTrue(result is ValidationResult.HardDeny)
    }

    @Test
    fun accessibilitySettingsComponent_isHardDenied() {
        val result = validator().validate(
            PendingAction(
                type = ActionType.ACCESSIBILITY_CLICK,
                targetComponent = "com.android.settings.accessibility.AccessibilitySettings",
                rawPayload = "open a11y",
            ),
        )
        assertTrue(result is ValidationResult.HardDeny)
    }

    @Test
    fun androidSettingsInPayload_isHardDenied() {
        val result = validator().validate(
            PendingAction(
                type = ActionType.ACCESSIBILITY_CLICK,
                rawPayload = "android.settings.WIFI_SETTINGS",
            ),
        )
        assertTrue(result is ValidationResult.HardDeny)
    }

    // ── Soft Deny tests ─────────────────────────────────────────────────────

    @Test
    fun fileWrite_isSoftDeniedWithHitl() {
        val result = validator().validate(
            PendingAction(type = ActionType.FILE_WRITE, rawPayload = "write file"),
        )
        assertTrue(result is ValidationResult.SoftDeny)
        assertEquals(true, (result as ValidationResult.SoftDeny).requiresHitl)
    }

    // ── Allow tests ─────────────────────────────────────────────────────────

    @Test
    fun accessibilityClick_onSafeTarget_isAllowed() {
        val result = validator().validate(
            PendingAction(
                type = ActionType.ACCESSIBILITY_CLICK,
                targetPackage = "com.example.app",
                rawPayload = "click button",
            ),
        )
        assertTrue(result is ValidationResult.Allow)
    }

    @Test
    fun fileRead_isAllowed() {
        val result = validator().validate(
            PendingAction(type = ActionType.FILE_READ, rawPayload = "read config"),
        )
        assertTrue(result is ValidationResult.Allow)
    }
}

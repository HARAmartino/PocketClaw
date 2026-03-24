package com.pocketclaw.agent.security

import com.pocketclaw.agent.validator.ActionType
import com.pocketclaw.agent.validator.ActionValidatorImpl
import com.pocketclaw.agent.validator.PendingAction
import com.pocketclaw.core.data.db.dao.WhitelistStoreDao
import com.pocketclaw.core.data.db.entity.WhitelistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal in-memory stub for whitelist lookups in security policy tests. */
private class FakeWhitelistStoreDao : WhitelistStoreDao {
    override suspend fun add(entry: WhitelistEntry) = Unit
    override suspend fun remove(entry: WhitelistEntry) = Unit
    override suspend fun isDomainAllowed(domain: String): Int = 0
    override fun observeAll(): Flow<List<WhitelistEntry>> = flowOf(emptyList())
}

/**
 * Tests for [SecurityPolicy] and [HardcodedSecurityPolicy].
 */
class SecurityPolicyTest {

    private val policy = HardcodedSecurityPolicy(ActionValidatorImpl(FakeWhitelistStoreDao()))

    // ── Hard deny tests ──────────────────────────────────────────────────────

    @Test
    fun isHardDenied_packageInstall_returnsTrue() {
        assertTrue(
            policy.isHardDenied(
                PendingAction(type = ActionType.PACKAGE_INSTALL, rawPayload = "install.apk"),
            ),
        )
    }

    @Test
    fun isHardDenied_settingsChange_returnsTrue() {
        assertTrue(
            policy.isHardDenied(
                PendingAction(type = ActionType.SETTINGS_CHANGE, rawPayload = "brightness=50"),
            ),
        )
    }

    @Test
    fun isHardDenied_selfModification_returnsTrue() {
        assertTrue(
            policy.isHardDenied(
                PendingAction(
                    type = ActionType.ACCESSIBILITY_CLICK,
                    targetPackage = "com.pocketclaw",
                    rawPayload = "click own UI",
                ),
            ),
        )
    }

    @Test
    fun isHardDenied_normalClick_returnsFalse() {
        assertFalse(
            policy.isHardDenied(
                PendingAction(
                    type = ActionType.ACCESSIBILITY_CLICK,
                    targetPackage = "com.example.app",
                    rawPayload = "click button",
                ),
            ),
        )
    }

    // ── Soft deny tests ──────────────────────────────────────────────────────

    @Test
    fun isSoftDenied_fileWrite_returnsTrue() {
        assertTrue(
            policy.isSoftDenied(
                PendingAction(type = ActionType.FILE_WRITE, rawPayload = "write file"),
            ),
        )
    }

    @Test
    fun isSoftDenied_hardDeniedAction_returnsFalse() {
        // PACKAGE_INSTALL is HardDeny, not SoftDeny
        assertFalse(
            policy.isSoftDenied(
                PendingAction(type = ActionType.PACKAGE_INSTALL, rawPayload = "install.apk"),
            ),
        )
    }

    // ── Policy version test ──────────────────────────────────────────────────

    @Test
    fun policyVersion_isHardcodedV1() {
        assertEquals("hardcoded-v1", policy.policyVersion)
    }
}

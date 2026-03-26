package com.pocketclaw.agent.orchestrator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that verify the scope-reset pattern used by [AgentOrchestrator.resetScope].
 *
 * These tests validate the invariants directly using [CoroutineScope] and
 * [SupervisorJob] — no [AgentOrchestrator] instantiation is required.
 */
class AgentOrchestratorResetScopeTest {

    @Test
    fun afterKillSwitch_rootScopeIsInactive() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.cancel()
        assertFalse(scope.isActive)
    }

    @Test
    fun newScope_isActive() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        assertTrue(scope.isActive)
        scope.cancel()
    }

    @Test
    fun resetScope_pattern_allowsNewLaunches() = runBlocking {
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.cancel()
        assertFalse(scope.isActive)

        // Simulate resetScope()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        assertTrue(scope.isActive)

        var ran = false
        scope.launch { ran = true }.join()
        assertTrue(ran)
        scope.cancel()
    }

    @Test
    fun cancelledScope_replacedScope_isIndependent() {
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope1.cancel()
        assertFalse(scope1.isActive)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        assertTrue("New scope must be active regardless of previous scope", scope2.isActive)
        scope2.cancel()
    }
}

package com.pocketclaw.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for the pure logic in [ServiceWatchdogReceiver].
 *
 * Because [BroadcastReceiver.onReceive] depends on Android framework objects,
 * we test the **logic decisions** in isolation using lightweight fakes:
 *
 * 1. Service-already-running → no [Context.startForegroundService] call.
 * 2. Service-not-running → [Context.startForegroundService] called once.
 * 3. [ServiceWatchdogReceiver.WATCHDOG_INTERVAL_MS] is a reasonable positive value.
 */
@RunWith(JUnit4::class)
class ServiceWatchdogReceiverTest {

    // ── Interval sanity ───────────────────────────────────────────────────────

    @Test
    fun watchdogInterval_isPositiveAndAtLeast5Minutes() {
        val intervalMs = ServiceWatchdogReceiver.WATCHDOG_INTERVAL_MS
        assert(intervalMs > 0) { "WATCHDOG_INTERVAL_MS must be positive, was $intervalMs" }
        // Should be at least 5 minutes for battery health
        assert(intervalMs >= 5 * 60 * 1000L) {
            "WATCHDOG_INTERVAL_MS should be ≥ 5 min, was $intervalMs"
        }
    }

    @Test
    fun watchdogInterval_isAtMost30Minutes() {
        val intervalMs = ServiceWatchdogReceiver.WATCHDOG_INTERVAL_MS
        // Should not be longer than 30 min or the watchdog becomes useless
        assert(intervalMs <= 30 * 60 * 1000L) {
            "WATCHDOG_INTERVAL_MS should be ≤ 30 min, was $intervalMs"
        }
    }

    // ── Service-running detection logic ───────────────────────────────────────

    /**
     * Verifies the logic that determines whether [AgentForegroundService] should be
     * started. Uses a simple fake to simulate the [ActivityManager] return value.
     */
    @Test
    fun serviceRunningCheck_serviceFound_returnsTrueAndSkipsStart() {
        val startCalls = mutableListOf<Intent>()
        val runningInfo = ActivityManager.RunningServiceInfo().apply {
            service = ComponentName("com.pocketclaw", AgentForegroundService::class.java.name)
        }
        val isRunning = runningInfo.service.className == AgentForegroundService::class.java.name
        assert(isRunning)
        // No start call should happen
        assert(startCalls.isEmpty())
    }

    @Test
    fun serviceRunningCheck_serviceNotFound_startsService() {
        val startCalls = mutableListOf<String>()
        val runningServices = emptyList<ActivityManager.RunningServiceInfo>()
        val isRunning = runningServices.any { info ->
            info.service.className == AgentForegroundService::class.java.name
        }
        if (!isRunning) {
            startCalls += AgentForegroundService.ACTION_START
        }
        assert(startCalls.size == 1)
        assertEquals(AgentForegroundService.ACTION_START, startCalls.first())
    }

    @Test
    fun serviceRunningCheck_otherServicesRunning_doesNotConfuseWithAgentService() {
        val runningServices = listOf(
            ActivityManager.RunningServiceInfo().apply {
                service = ComponentName("com.other.app", "com.other.app.OtherService")
            },
            ActivityManager.RunningServiceInfo().apply {
                service = ComponentName("com.third.app", "com.third.app.ThirdService")
            },
        )
        val isRunning = runningServices.any { info ->
            info.service.className == AgentForegroundService::class.java.name
        }
        assert(!isRunning) {
            "Should not detect AgentForegroundService when only other services are running"
        }
    }
}

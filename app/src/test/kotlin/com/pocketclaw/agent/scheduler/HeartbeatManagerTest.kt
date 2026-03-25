package com.pocketclaw.agent.scheduler

import com.pocketclaw.agent.scheduler.HeartbeatManager.Companion.DEFAULT_INTERVAL_MINUTES
import com.pocketclaw.agent.scheduler.HeartbeatManager.Companion.DEFAULT_PROMPT
import com.pocketclaw.agent.scheduler.HeartbeatManager.Companion.MAX_INTERVAL_MINUTES
import com.pocketclaw.agent.scheduler.HeartbeatManager.Companion.MIN_INTERVAL_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HeartbeatManager] interface constants and a fake implementation
 * that exercises the scheduling logic without requiring Android dependencies.
 */
class HeartbeatManagerTest {

    /** Minimal in-memory implementation for unit testing. */
    private class InMemoryHeartbeatManager : HeartbeatManager {
        var enabled = false
        var intervalMinutes = DEFAULT_INTERVAL_MINUTES
        var prompt = DEFAULT_PROMPT
        var rescheduleCount = 0

        override suspend fun enable() { enabled = true }
        override suspend fun disable() { enabled = false }
        override suspend fun setIntervalMinutes(minutes: Int) {
            intervalMinutes = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        }
        override suspend fun setPrompt(prompt: String) { this.prompt = prompt }
        override suspend fun rescheduleAfterExecution() { rescheduleCount++ }
        override suspend fun readPrompt() = prompt
        override suspend fun isEnabled() = enabled
    }

    private lateinit var manager: InMemoryHeartbeatManager

    @Before
    fun setUp() {
        manager = InMemoryHeartbeatManager()
    }

    @Test
    fun companionConstants_valuesAreReasonable() {
        assertTrue(MIN_INTERVAL_MINUTES > 0)
        assertTrue(MAX_INTERVAL_MINUTES > MIN_INTERVAL_MINUTES)
        assertTrue(DEFAULT_INTERVAL_MINUTES in MIN_INTERVAL_MINUTES..MAX_INTERVAL_MINUTES)
        assertTrue(DEFAULT_PROMPT.isNotBlank())
    }

    @Test
    fun dataStoreKeys_areNotNull() {
        assertNotNull(HeartbeatManager.HEARTBEAT_ENABLED_KEY)
        assertNotNull(HeartbeatManager.HEARTBEAT_INTERVAL_MINUTES_KEY)
        assertNotNull(HeartbeatManager.HEARTBEAT_PROMPT_KEY)
    }

    @Test
    fun enable_setsEnabledTrue() = kotlinx.coroutines.runBlocking {
        manager.enable()
        assertTrue(manager.isEnabled())
    }

    @Test
    fun disable_setsEnabledFalse() = kotlinx.coroutines.runBlocking {
        manager.enable()
        manager.disable()
        assertFalse(manager.isEnabled())
    }

    @Test
    fun setIntervalMinutes_clampedToMin() = kotlinx.coroutines.runBlocking {
        manager.setIntervalMinutes(1)
        assertEquals(MIN_INTERVAL_MINUTES, manager.intervalMinutes)
    }

    @Test
    fun setIntervalMinutes_clampedToMax() = kotlinx.coroutines.runBlocking {
        manager.setIntervalMinutes(10_000)
        assertEquals(MAX_INTERVAL_MINUTES, manager.intervalMinutes)
    }

    @Test
    fun setIntervalMinutes_validValue_stored() = kotlinx.coroutines.runBlocking {
        manager.setIntervalMinutes(60)
        assertEquals(60, manager.intervalMinutes)
    }

    @Test
    fun setPrompt_updatesPrompt() = kotlinx.coroutines.runBlocking {
        manager.setPrompt("Check system status")
        assertEquals("Check system status", manager.readPrompt())
    }

    @Test
    fun rescheduleAfterExecution_incrementsCount() = kotlinx.coroutines.runBlocking {
        manager.rescheduleAfterExecution()
        manager.rescheduleAfterExecution()
        assertEquals(2, manager.rescheduleCount)
    }
}

package com.pocketclaw.agent.scheduler

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Manages the periodic heartbeat that runs a user-configured prompt on a schedule
 * and delivers the result as a remote notification (no HITL approval).
 *
 * Default interval: [DEFAULT_INTERVAL_MINUTES] minutes.
 * Interval and enabled flag are persisted in DataStore.
 */
interface HeartbeatManager {

    companion object {
        /** Default heartbeat interval in minutes. */
        const val DEFAULT_INTERVAL_MINUTES = 30
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_INTERVAL_MINUTES = 480

        /** Default heartbeat prompt sent to the LLM. */
        const val DEFAULT_PROMPT =
            "Summarise any important recent events or system state and report back."

        /** DataStore key for the heartbeat enabled flag. */
        val HEARTBEAT_ENABLED_KEY = booleanPreferencesKey("heartbeat_enabled")

        /** DataStore key for the interval in minutes. */
        val HEARTBEAT_INTERVAL_MINUTES_KEY = intPreferencesKey("heartbeat_interval_minutes")

        /** DataStore key for the prompt text sent to the LLM. */
        val HEARTBEAT_PROMPT_KEY = stringPreferencesKey("heartbeat_prompt")
    }

    /** Enables the heartbeat and schedules the first alarm. */
    suspend fun enable()

    /** Disables the heartbeat and cancels any pending alarm. */
    suspend fun disable()

    /**
     * Updates the interval (in minutes, clamped to [[MIN_INTERVAL_MINUTES],
     * [MAX_INTERVAL_MINUTES]]) and reschedules if the heartbeat is currently enabled.
     */
    suspend fun setIntervalMinutes(minutes: Int)

    /** Stores the heartbeat prompt text. */
    suspend fun setPrompt(prompt: String)

    /**
     * Re-schedules the next heartbeat after one fires.
     * Called by [com.pocketclaw.agent.orchestrator.AgentOrchestrator] after each
     * heartbeat task completes (since [android.app.AlarmManager.setExactAndAllowWhileIdle]
     * is one-shot).
     */
    suspend fun rescheduleAfterExecution()

    /** Returns the current heartbeat prompt from DataStore. */
    suspend fun readPrompt(): String

    /** Returns whether the heartbeat is currently enabled. */
    suspend fun isEnabled(): Boolean
}

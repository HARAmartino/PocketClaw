package com.pocketclaw.agent.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [HeartbeatManager].
 *
 * Uses [AlarmManager.setExactAndAllowWhileIdle] for Doze-safe delivery.
 * Falls back to a [WorkManager] expedited one-time job if the device does not
 * grant exact-alarm permission (OEM-restricted or user-revoked on API 31+).
 */
@Singleton
class HeartbeatManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : HeartbeatManager {

    companion object {
        private const val TAG = "HeartbeatManager"
        private const val HEARTBEAT_WORK_NAME = "pocketclaw_heartbeat"
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun enable() {
        dataStore.edit { it[HeartbeatManager.HEARTBEAT_ENABLED_KEY] = true }
        val intervalMs = readIntervalMs()
        scheduleNext(intervalMs)
        Log.i(TAG, "Heartbeat enabled with interval ${intervalMs}ms.")
    }

    override suspend fun disable() {
        dataStore.edit { it[HeartbeatManager.HEARTBEAT_ENABLED_KEY] = false }
        alarmManager.cancel(buildPendingIntent())
        WorkManager.getInstance(context).cancelUniqueWork(HEARTBEAT_WORK_NAME)
        Log.i(TAG, "Heartbeat disabled.")
    }

    override suspend fun setIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(
            HeartbeatManager.MIN_INTERVAL_MINUTES,
            HeartbeatManager.MAX_INTERVAL_MINUTES,
        )
        dataStore.edit { it[HeartbeatManager.HEARTBEAT_INTERVAL_MINUTES_KEY] = clamped }
        if (isEnabled()) {
            scheduleNext(clamped * 60_000L)
            Log.i(TAG, "Heartbeat interval updated to ${clamped}min.")
        }
    }

    override suspend fun setPrompt(prompt: String) {
        dataStore.edit { it[HeartbeatManager.HEARTBEAT_PROMPT_KEY] = prompt }
    }

    override suspend fun rescheduleAfterExecution() {
        if (isEnabled()) {
            val intervalMs = readIntervalMs()
            scheduleNext(intervalMs)
            Log.i(TAG, "Heartbeat re-scheduled in ${intervalMs}ms.")
        }
    }

    override suspend fun readPrompt(): String {
        val prefs = dataStore.data.first()
        return prefs[HeartbeatManager.HEARTBEAT_PROMPT_KEY] ?: HeartbeatManager.DEFAULT_PROMPT
    }

    override suspend fun isEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[HeartbeatManager.HEARTBEAT_ENABLED_KEY] ?: false
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun readIntervalMs(): Long {
        val prefs = dataStore.data.first()
        val minutes = prefs[HeartbeatManager.HEARTBEAT_INTERVAL_MINUTES_KEY]
            ?: HeartbeatManager.DEFAULT_INTERVAL_MINUTES
        return minutes * 60_000L
    }

    private fun scheduleNext(intervalMs: Long) {
        val triggerAtMs = System.currentTimeMillis() + intervalMs
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                buildPendingIntent(),
            )
            Log.i(TAG, "Exact heartbeat alarm set at $triggerAtMs.")
        } else {
            scheduleWorkManagerFallback(intervalMs)
        }
    }

    private fun scheduleWorkManagerFallback(intervalMs: Long) {
        val request = OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInitialDelay(intervalMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            HEARTBEAT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(TAG, "WorkManager heartbeat fallback enqueued with delay ${intervalMs}ms.")
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, HeartbeatReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

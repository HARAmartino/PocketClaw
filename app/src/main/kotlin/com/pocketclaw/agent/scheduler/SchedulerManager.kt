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
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * Manages exact-alarm scheduling for periodic agent tasks.
 *
 * Uses [AlarmManager.setExactAndAllowWhileIdle] for Doze-safe delivery.
 * Falls back to a [WorkManager] expedited one-time job if the device does not
 * grant exact-alarm permission (OEM-restricted or user-revoked on API 31+).
 *
 * Task configurations (title, prompt, interval) are persisted in [DataStore]
 * so they survive process death between alarm fires.
 *
 * **Design note:** [setExactAndAllowWhileIdle] fires only once; callers must
 * invoke [rescheduleAfterExecution] after each task completes to re-register
 * the next alarm.
 */
@Singleton
class SchedulerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private const val TAG = "SchedulerManager"
        const val EXTRA_TASK_ID = "task_id"
        private const val WORK_TAG_PREFIX = "scheduled_task_"

        private fun titleKey(taskId: String) =
            stringPreferencesKey("scheduled_task_${taskId}_title")

        private fun promptKey(taskId: String) =
            stringPreferencesKey("scheduled_task_${taskId}_prompt")

        private fun intervalKey(taskId: String) =
            stringPreferencesKey("scheduled_task_${taskId}_interval_ms")
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Persists the task configuration and schedules the first alarm.
     */
    suspend fun scheduleTask(
        taskId: String,
        title: String,
        prompt: String,
        cronIntervalMs: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[titleKey(taskId)] = title
            prefs[promptKey(taskId)] = prompt
            prefs[intervalKey(taskId)] = cronIntervalMs.toString()
        }
        scheduleAlarm(taskId, cronIntervalMs)
    }

    /**
     * Loads the stored configuration for [taskId] from DataStore.
     * Returns null if no config exists for this task.
     */
    suspend fun loadTaskConfig(taskId: String): ScheduledTaskConfig? {
        val prefs = dataStore.data.first()
        val title = prefs[titleKey(taskId)] ?: return null
        val prompt = prefs[promptKey(taskId)] ?: return null
        val intervalMs = prefs[intervalKey(taskId)]?.toLongOrNull() ?: return null
        return ScheduledTaskConfig(taskId = taskId, title = title, prompt = prompt, cronIntervalMs = intervalMs)
    }

    /**
     * Cancels any pending alarm or WorkManager job for [taskId] and removes its
     * stored configuration from DataStore.
     */
    suspend fun cancelTask(taskId: String) {
        alarmManager.cancel(buildPendingIntent(taskId))
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG_PREFIX + taskId)
        dataStore.edit { prefs ->
            prefs.remove(titleKey(taskId))
            prefs.remove(promptKey(taskId))
            prefs.remove(intervalKey(taskId))
        }
        Log.i(TAG, "Cancelled and removed config for task '$taskId'.")
    }

    /**
     * Re-schedules [taskId] to fire again using its stored interval.
     *
     * Must be called by [com.pocketclaw.agent.orchestrator.AgentOrchestrator] after
     * each task completion because [setExactAndAllowWhileIdle] is a one-shot alarm.
     */
    suspend fun rescheduleAfterExecution(taskId: String) {
        val prefs = dataStore.data.first()
        val cronIntervalMs = prefs[intervalKey(taskId)]?.toLongOrNull() ?: run {
            Log.w(TAG, "No interval stored for task '$taskId' — cannot reschedule.")
            return
        }
        Log.i(TAG, "Re-scheduling task '$taskId' in ${cronIntervalMs}ms.")
        scheduleAlarm(taskId, cronIntervalMs)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun scheduleAlarm(taskId: String, cronIntervalMs: Long) {
        val triggerAtMs = System.currentTimeMillis() + cronIntervalMs
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                buildPendingIntent(taskId),
            )
            Log.i(TAG, "Exact alarm set for task '$taskId' at $triggerAtMs.")
        } else {
            scheduleWorkManagerFallback(taskId, cronIntervalMs)
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun scheduleWorkManagerFallback(taskId: String, cronIntervalMs: Long) {
        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInitialDelay(cronIntervalMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(EXTRA_TASK_ID to taskId))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG_PREFIX + taskId)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_TAG_PREFIX + taskId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(TAG, "WorkManager fallback enqueued for task '$taskId' with delay ${cronIntervalMs}ms.")
    }

    private fun buildPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * Configuration for a persisted scheduled task.
 */
data class ScheduledTaskConfig(
    val taskId: String,
    val title: String,
    val prompt: String,
    val cronIntervalMs: Long,
)

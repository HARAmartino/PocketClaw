package com.pocketclaw.agent.scheduler

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.pocketclaw.agent.orchestrator.AgentOrchestrator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager [CoroutineWorker] used as a fallback for [SchedulerManager] when
 * the device does not grant exact-alarm permission.
 *
 * Runs as an expedited job to minimize latency. Calls
 * [AgentOrchestrator.enqueueScheduledTask] to execute the scheduled task.
 */
@HiltWorker
class ScheduledTaskWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: AgentOrchestrator,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        private const val NOTIFICATION_ID = 9001
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        // Minimal foreground info — the AgentForegroundService handles the main notification.
        val notification = android.app.Notification.Builder(
            applicationContext,
            "pocketclaw_scheduler",
        )
            .setContentTitle("PocketClaw Scheduler")
            .setContentText("Running scheduled task…")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(SchedulerManager.EXTRA_TASK_ID) ?: run {
            Log.e(TAG, "Worker started without task_id — aborting.")
            return Result.failure()
        }
        Log.i(TAG, "WorkManager running scheduled task '$taskId'.")

        return try {
            orchestrator.enqueueScheduledTask(taskId)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Scheduled task '$taskId' failed: ${e.message}", e)
            Result.retry()
        }
    }
}

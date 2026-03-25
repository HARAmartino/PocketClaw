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
 * WorkManager [CoroutineWorker] used as a Doze-safe fallback for [HeartbeatManager]
 * when the device does not grant exact-alarm permission.
 *
 * Calls [AgentOrchestrator.enqueueHeartbeatTask] to run the configured heartbeat prompt.
 */
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: AgentOrchestrator,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val NOTIFICATION_ID = 9002
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = android.app.Notification.Builder(
            applicationContext,
            "pocketclaw_scheduler",
        )
            .setContentTitle("PocketClaw Heartbeat")
            .setContentText("Running heartbeat task…")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "WorkManager running heartbeat task.")
        return try {
            orchestrator.enqueueHeartbeatTask()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat task failed: ${e.message}", e)
            Result.retry()
        }
    }
}

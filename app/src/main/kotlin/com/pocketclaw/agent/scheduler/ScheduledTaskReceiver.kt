package com.pocketclaw.agent.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pocketclaw.agent.orchestrator.AgentOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [BroadcastReceiver] that fires when an [android.app.AlarmManager] alarm for a
 * scheduled task is triggered.
 *
 * Delegates to [AgentOrchestrator.enqueueScheduledTask] which loads the task
 * configuration from DataStore and begins execution.
 *
 * Registered with [android.app.PendingIntent.FLAG_IMMUTABLE] and declared
 * `android:exported="false"` in the manifest so that only this application's
 * own [android.app.AlarmManager] can fire it.
 */
@AndroidEntryPoint
class ScheduledTaskReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledTaskReceiver"
    }

    @Inject
    lateinit var orchestrator: AgentOrchestrator

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(SchedulerManager.EXTRA_TASK_ID) ?: run {
            Log.e(TAG, "Received alarm without task_id — ignoring.")
            return
        }
        Log.i(TAG, "AlarmManager fired for task '$taskId'.")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                orchestrator.enqueueScheduledTask(taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

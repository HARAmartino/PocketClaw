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
 * [BroadcastReceiver] that fires when the heartbeat [android.app.AlarmManager] alarm
 * triggers.
 *
 * Delegates to [AgentOrchestrator.enqueueHeartbeatTask] which reads the configured
 * prompt from DataStore, runs an LLM task, and delivers the result via
 * [com.pocketclaw.agent.hitl.RemoteApprovalProvider.sendNotification] (no HITL needed).
 *
 * Declared `android:exported="false"` in the manifest so that only this application's
 * own [android.app.AlarmManager] can fire it.
 */
@AndroidEntryPoint
class HeartbeatReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeartbeatReceiver"
    }

    @Inject
    lateinit var orchestrator: AgentOrchestrator

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Heartbeat alarm fired.")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                orchestrator.enqueueHeartbeatTask()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

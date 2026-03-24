package com.pocketclaw.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

/**
 * Watchdog receiver that re-schedules the [AgentForegroundService] if it is not running.
 * Also triggered on BOOT_COMPLETED to restart the service after device reboot.
 *
 * Scheduled via [android.app.AlarmManager.setExactAndAllowWhileIdle] for Doze compatibility.
 */
@AndroidEntryPoint
class ServiceWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceWatchdog"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "ServiceWatchdogReceiver triggered: ${intent.action}")
        val serviceIntent = Intent(context, AgentForegroundService::class.java).apply {
            action = AgentForegroundService.ACTION_START
        }
        context.startForegroundService(serviceIntent)
    }
}

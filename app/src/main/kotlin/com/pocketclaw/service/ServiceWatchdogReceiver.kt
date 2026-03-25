package com.pocketclaw.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

/**
 * Watchdog receiver that re-schedules the [AgentForegroundService] if it is not running.
 * Also triggered on BOOT_COMPLETED to restart the service after device reboot.
 *
 * Scheduled via [android.app.AlarmManager.setExactAndAllowWhileIdle] for Doze compatibility.
 * After each fire the receiver re-schedules itself for the next [WATCHDOG_INTERVAL_MS].
 */
@AndroidEntryPoint
class ServiceWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceWatchdog"

        /** Watchdog fires every 15 minutes. */
        const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L

        /** PendingIntent request code to avoid collisions with other alarms. */
        private const val REQUEST_CODE = 0xCA7_CA_7

        /**
         * Schedules (or re-schedules) the watchdog alarm using
         * [AlarmManager.setExactAndAllowWhileIdle] so it fires even in Doze mode.
         */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = buildPendingIntent(context)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
                pendingIntent,
            )
            Log.d(TAG, "Watchdog alarm scheduled in ${WATCHDOG_INTERVAL_MS / 1000}s.")
        }

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ServiceWatchdogReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "ServiceWatchdogReceiver triggered: ${intent.action}")

        if (!isAgentServiceRunning(context)) {
            Log.i(TAG, "AgentForegroundService not running — restarting.")
            val serviceIntent = Intent(context, AgentForegroundService::class.java).apply {
                action = AgentForegroundService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
        } else {
            Log.d(TAG, "AgentForegroundService is already running — no action needed.")
        }

        // Re-schedule for next check (setExactAndAllowWhileIdle is one-shot).
        schedule(context)
    }

    /**
     * Returns true if [AgentForegroundService] has an active process entry in
     * [ActivityManager.getRunningServices].
     */
    @Suppress("DEPRECATION") // getRunningServices is still the correct API for own services
    private fun isAgentServiceRunning(context: Context): Boolean {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.getRunningServices(Int.MAX_VALUE).any { info ->
            info.service.className == AgentForegroundService::class.java.name
        }
    }
}

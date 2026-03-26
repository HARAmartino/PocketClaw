package com.pocketclaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pocketclaw.R
import com.pocketclaw.agent.orchestrator.AgentOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Persistent foreground service that keeps the agent running 24/7.
 *
 * Responsibilities:
 * - Holds a [PowerManager.PARTIAL_WAKE_LOCK] during active task execution only.
 * - Manages a single long-lived [MediaProjection] + [VirtualDisplay] session.
 * - Monitors battery temperature and level via [BroadcastReceiver].
 * - Tracks continuous charging time and shows advisory banner after 2 hours.
 *
 * Android 14+: declares foregroundServiceType="mediaProjection|dataSync".
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    companion object {
        private const val TAG = "AgentForegroundService"
        const val NOTIFICATION_CHANNEL_ID = "pocketclaw_agent"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.pocketclaw.ACTION_START"
        const val ACTION_STOP = "com.pocketclaw.ACTION_STOP"
        const val ACTION_MEDIA_PROJECTION_GRANTED = "com.pocketclaw.ACTION_MEDIA_PROJECTION_GRANTED"
        const val EXTRA_MEDIA_PROJECTION_RESULT_CODE = "extra_result_code"
        const val EXTRA_MEDIA_PROJECTION_DATA = "extra_projection_data"

        private const val TEMP_PAUSE_THRESHOLD_CELSIUS_TENTHS = 450 // 45.0°C * 10
        private const val BATTERY_PAUSE_THRESHOLD_PERCENT = 20
        private const val CHARGING_ADVISORY_THRESHOLD_MS = 2 * 60 * 60 * 1000L // 2 hours

        // Screenshot pipeline settings (PRD §5-C)
        private const val VIRTUAL_DISPLAY_WIDTH = 1280
        private const val VIRTUAL_DISPLAY_HEIGHT = 720
        private const val VIRTUAL_DISPLAY_DPI = 240
        private const val IMAGE_READER_BUFFER_COUNT = 2
    }

    @Inject
    lateinit var orchestrator: AgentOrchestrator

    @Inject
    lateinit var serviceState: AgentServiceState

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var chargingStartMs: Long? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

            val tempTenths = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL

            // Publish charging state to shared service state
            serviceState.setCharging(isCharging)

            // Continuous charging advisory
            if (isCharging) {
                if (chargingStartMs == null) {
                    chargingStartMs = System.currentTimeMillis()
                    serviceState.setChargingStartMs(chargingStartMs)
                }
                val chargingDurationMs = System.currentTimeMillis() - (chargingStartMs ?: 0L)
                if (chargingDurationMs > CHARGING_ADVISORY_THRESHOLD_MS) {
                    Log.i(TAG, "Continuous charging advisory: device has been charging for >2 hours.")
                }
            } else {
                chargingStartMs = null
                serviceState.setChargingStartMs(null)
            }

            val batteryPercent = if (scale > 0) (level * 100) / scale else -1
            serviceState.setBattery(batteryPercent)
            serviceState.setThermal(tempTenths)

            // Auto-pause conditions (Layer 5)
            if (tempTenths > TEMP_PAUSE_THRESHOLD_CELSIUS_TENTHS) {
                Log.w(TAG, "THERMAL_PAUSE: Temperature ${tempTenths / 10.0}°C exceeds threshold. Pausing agent.")
                pauseAgent(reason = "Device temperature too high (${tempTenths / 10.0}°C). Resume requires user confirmation.")
                return
            }

            if (batteryPercent in 0..BATTERY_PAUSE_THRESHOLD_PERCENT) {
                Log.w(TAG, "BATTERY_PAUSE: Battery at $batteryPercent%. Pausing agent.")
                pauseAgent(reason = "Battery level too low ($batteryPercent%). Resume requires user confirmation.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.i(TAG, "AgentForegroundService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> acquireWakeLock()
            ACTION_STOP -> {
                releaseWakeLock()
                stopSelf()
            }
            ACTION_MEDIA_PROJECTION_GRANTED -> {
                val resultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA)
                }
                if (resultCode != -1 && data != null) {
                    val projectionManager =
                        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    initMediaProjection(projectionManager.getMediaProjection(resultCode, data))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Battery receiver not registered when onDestroy called.")
        }
        releaseMediaProjection()
        releaseWakeLock()
        Log.i(TAG, "AgentForegroundService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Called by [AgentOrchestrator] when a task starts. */
    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketClaw:AgentWakeLock")
        wakeLock?.acquire()
        serviceState.setRunning(true)
        Log.d(TAG, "WakeLock acquired.")
    }

    /** Called when a task completes, pauses, or errors. */
    fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released.")
        }
        wakeLock = null
        serviceState.setRunning(false)
    }

    /**
     * Initializes the [MediaProjection] session.
     * Must be called once per user-granted permission result.
     * On Android 14+, each [MediaProjection] instance allows only ONE [createVirtualDisplay].
     */
    fun initMediaProjection(projection: MediaProjection) {
        releaseMediaProjection()
        mediaProjection = projection
        imageReader = ImageReader.newInstance(
            VIRTUAL_DISPLAY_WIDTH, VIRTUAL_DISPLAY_HEIGHT,
            android.graphics.PixelFormat.RGBA_8888,
            IMAGE_READER_BUFFER_COUNT,
        )
        virtualDisplay = projection.createVirtualDisplay(
            "PocketClawCapture",
            VIRTUAL_DISPLAY_WIDTH, VIRTUAL_DISPLAY_HEIGHT, VIRTUAL_DISPLAY_DPI,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null,
        )
        Log.i(TAG, "MediaProjection and VirtualDisplay initialized.")
    }

    private fun releaseMediaProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(TAG, "MediaProjection released.")
    }

    private fun pauseAgent(reason: String) {
        releaseWakeLock()
        orchestrator.killSwitch()
        Log.w(TAG, "Agent paused: $reason")
        // Notify user via notification update — resume requires explicit user confirmation
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildPausedNotification(reason))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AgentForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_agent_running_title))
            .setContentText(getString(R.string.notification_agent_running_text))
            .setSmallIcon(R.drawable.ic_agent_running)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun buildPausedNotification(reason: String): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_agent_paused_title))
            .setContentText(reason)
            .setSmallIcon(R.drawable.ic_agent_paused)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
}

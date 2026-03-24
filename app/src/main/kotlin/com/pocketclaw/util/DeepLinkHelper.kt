package com.pocketclaw.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Helper for opening OEM-specific battery management screens.
 * Used on the [com.pocketclaw.ui.onboarding.PermissionOnboardingScreen] to guide
 * users on Xiaomi, Huawei, OPPO, Samsung, and Vivo devices.
 *
 * These intents are documented in the README. They are advisory only —
 * PocketClaw never forces or automates these settings changes.
 */
object DeepLinkHelper {

    private const val TAG = "DeepLinkHelper"

    /** Attempts to open the OEM battery management screen for the current device. */
    fun openOemBatterySettings(context: Context) {
        val intent = buildOemBatteryIntent(context) ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Could not open OEM battery settings: ${e.message}")
        }
    }

    private fun buildOemBatteryIntent(context: Context): Intent? {
        val pm = context.packageManager

        // Xiaomi (MIUI) — AutoStart
        tryIntent(
            pm,
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
                putExtra("package_type", 1)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )?.let { return it }

        // Huawei (EMUI / HarmonyOS) — Protected Apps
        tryIntent(
            pm,
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )?.let { return it }

        // OPPO (ColorOS) — Background Assist
        tryIntent(
            pm,
            Intent("action.coloros.permission2.screen.startupmanager.STARTUP_MANAGER_ACTIVITY_STYLE")
                .setPackage("com.coloros.oppoguardelf")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )?.let { return it }

        // Samsung (One UI) — Device Care Battery
        tryIntent(
            pm,
            Intent("com.samsung.android.lool.ACTION_POWER_SAVING")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )?.let { return it }

        // Vivo (FuntouchOS) — Background Power Management
        tryIntent(
            pm,
            Intent().setComponent(
                ComponentName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity",
                ),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )?.let { return it }

        Log.d(TAG, "No OEM-specific battery intent found for this device.")
        return null
    }

    private fun tryIntent(pm: PackageManager, intent: Intent): Intent? {
        return if (intent.resolveActivity(pm) != null) intent else null
    }
}

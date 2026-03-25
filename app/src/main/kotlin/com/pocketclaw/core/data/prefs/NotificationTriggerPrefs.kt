package com.pocketclaw.core.data.prefs

import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Shared DataStore preference keys for notification-trigger configuration.
 * Accessed by both [com.pocketclaw.service.AgentNotificationListenerService]
 * and [com.pocketclaw.ui.settings.SettingsViewModel].
 */
object NotificationTriggerPrefs {
    /** Set of package names whose notifications trigger the agent. */
    val TRIGGER_PACKAGES_KEY = stringSetPreferencesKey("notification_trigger_packages")
}

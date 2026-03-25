package com.pocketclaw.agent.plugin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.pocketclaw.core.data.db.dao.PluginTrustStoreDao
import com.pocketclaw.core.data.db.entity.PluginTrustEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers PocketClaw plugin applications installed on the device.
 *
 * **Discovery mechanism:** [discoverPlugins] queries [PackageManager] for all
 * installed apps that declare the intent action
 * `com.pocketclaw.action.PLUGIN` in their manifest. Only apps that are
 * already in the [PluginTrustStoreDao] with [PluginTrustEntry.isTrusted] = true
 * are included in the returned list.
 *
 * This implementation is **discovery-only** — no plugin code is executed.
 * Actual plugin execution will be added in a later phase once the sandboxing
 * strategy is finalised.
 */
@Singleton
class PluginLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginTrustStoreDao: PluginTrustStoreDao,
) {
    companion object {
        private const val TAG = "PluginLoader"

        /** Intent action that plugin apps must declare to be discoverable. */
        const val PLUGIN_INTENT_ACTION = "com.pocketclaw.action.PLUGIN"

        /**
         * Manifest metadata key that plugin apps may use to declare their
         * human-readable display name (optional).
         */
        const val METADATA_PLUGIN_NAME = "com.pocketclaw.plugin.name"
    }

    /**
     * Returns the list of [DiscoveredPlugin] objects for all installed apps that:
     * 1. Declare the [PLUGIN_INTENT_ACTION] intent in their manifest.
     * 2. Have a matching [PluginTrustEntry] with [PluginTrustEntry.isTrusted] = true.
     *
     * Apps that are discovered but not yet trusted are logged at INFO level so the
     * user can manually approve them in the Settings UI (future phase).
     */
    suspend fun discoverPlugins(): List<DiscoveredPlugin> {
        val intent = android.content.Intent(PLUGIN_INTENT_ACTION)
        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        }

        val trustedEntries = pluginTrustStoreDao.observeTrusted().first()
        val trustedMap = trustedEntries.associateBy { it.packageName }

        val discovered = mutableListOf<DiscoveredPlugin>()

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue

            val certSha256 = computeCertSha256(packageName)
            val trustedEntry = trustedMap[packageName]

            val displayName = appInfo.metaData?.getString(METADATA_PLUGIN_NAME)
                ?: resolveInfo.loadLabel(context.packageManager).toString()

            if (trustedEntry == null) {
                Log.i(TAG, "Discovered untrusted plugin '$packageName' — skipping until user approves.")
                continue
            }

            if (!trustedEntry.isTrusted) {
                Log.i(TAG, "Plugin '$packageName' is in trust store but not approved — skipping.")
                continue
            }

            if (trustedEntry.certSha256 != certSha256) {
                Log.w(
                    TAG,
                    "Plugin '$packageName' certificate mismatch — expected ${trustedEntry.certSha256}, " +
                        "got $certSha256. Skipping (possible tamper).",
                )
                continue
            }

            Log.i(TAG, "Trusted plugin discovered: '$packageName' ($displayName).")
            discovered += DiscoveredPlugin(
                packageName = packageName,
                displayName = displayName,
                certSha256 = certSha256,
                capabilities = trustedEntry.capabilities,
            )
        }

        return discovered
    }

    /**
     * Returns the SHA-256 fingerprint of the first signing certificate for [packageName],
     * or an empty string if the certificate cannot be retrieved.
     */
    private fun computeCertSha256(packageName: String): String {
        return try {
            val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager
                    .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
                signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures ?: emptyArray()
            }
            if (certs.isEmpty()) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(certs[0].toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute cert SHA-256 for '$packageName': ${e.message}")
            ""
        }
    }
}

/**
 * Represents a trusted, installed PocketClaw plugin.
 *
 * @param packageName  Android package name of the plugin app.
 * @param displayName  Human-readable name declared in the plugin's manifest metadata.
 * @param certSha256   SHA-256 fingerprint of the plugin's signing certificate.
 * @param capabilities JSON-serialized Set<Capability> as stored in [PluginTrustEntry].
 */
data class DiscoveredPlugin(
    val packageName: String,
    val displayName: String,
    val certSha256: String,
    val capabilities: String,
)

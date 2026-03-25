package com.pocketclaw.agent.skill

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.pocketclaw.core.data.db.dao.SkillTrustStoreDao
import com.pocketclaw.core.data.db.entity.SkillTrustEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for skill discovery. Extracted so that callers (e.g., [com.pocketclaw.ui.skill.SkillApprovalViewModel])
 * can be tested without a real [android.content.Context].
 */
interface SkillDiscoverer {
    /** Returns the list of trusted, installed PocketClaw skill apps. */
    suspend fun discoverSkills(): List<DiscoveredSkill>
}

/**
 * Discovers PocketClaw skill applications installed on the device.
 *
 * **Discovery mechanism:** [discoverSkills] queries [PackageManager] for all
 * installed apps that declare the metadata key
 * `com.pocketclaw.skill.SKILL_MANIFEST` in their manifest. Only apps that are
 * already in the [SkillTrustStoreDao] with [SkillTrustEntry.isTrusted] = true
 * are included in the returned list.
 *
 * This implementation is **discovery-only** — no skill code is executed.
 * Actual skill execution will be added in a later phase once the sandboxing
 * strategy is finalised.
 */
@Singleton
class SkillLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillTrustStoreDao: SkillTrustStoreDao,
) : SkillDiscoverer {
    companion object {
        private const val TAG = "SkillLoader"

        /**
         * Manifest metadata key that skill apps must declare to be discoverable.
         * Replaces the old `com.pocketclaw.action.PLUGIN` intent action.
         */
        const val METADATA_SKILL_MANIFEST = "com.pocketclaw.skill.SKILL_MANIFEST"

        /**
         * Manifest metadata key that skill apps may use to declare their
         * human-readable display name (optional).
         */
        const val METADATA_SKILL_NAME = "com.pocketclaw.skill.name"
    }

    /**
     * Returns the list of [DiscoveredSkill] objects for all installed apps that:
     * 1. Declare the [METADATA_SKILL_MANIFEST] metadata key in their manifest.
     * 2. Have a matching [SkillTrustEntry] with [SkillTrustEntry.isTrusted] = true.
     *
     * Apps that are discovered but not yet trusted are logged at INFO level so the
     * user can manually approve them in the Settings UI (future phase).
     */
    suspend fun discoverSkills(): List<DiscoveredSkill> {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        }

        val trustedEntries = skillTrustStoreDao.observeTrusted().first()
        val trustedMap = trustedEntries.associateBy { it.packageName }

        val discovered = mutableListOf<DiscoveredSkill>()

        for (packageInfo in packages) {
            val metadata = packageInfo.applicationInfo?.metaData ?: continue
            if (!metadata.containsKey(METADATA_SKILL_MANIFEST)) continue

            val packageName = packageInfo.packageName

            val certSha256 = computeCertSha256(packageName)
            val trustedEntry = trustedMap[packageName]

            val displayName = metadata.getString(METADATA_SKILL_NAME)
                ?: packageInfo.applicationInfo?.loadLabel(context.packageManager)?.toString()
                ?: packageName

            if (trustedEntry == null) {
                Log.i(TAG, "Discovered untrusted skill '$packageName' — skipping until user approves.")
                continue
            }

            if (!trustedEntry.isTrusted) {
                Log.i(TAG, "Skill '$packageName' is in trust store but not approved — skipping.")
                continue
            }

            if (trustedEntry.certSha256 != certSha256) {
                Log.w(
                    TAG,
                    "Skill '$packageName' certificate mismatch — expected ${trustedEntry.certSha256}, " +
                        "got $certSha256. Skipping (possible tamper).",
                )
                continue
            }

            Log.i(TAG, "Trusted skill discovered: '$packageName' ($displayName).")
            discovered += DiscoveredSkill(
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
 * Represents a trusted, installed PocketClaw skill.
 *
 * @param packageName  Android package name of the skill app.
 * @param displayName  Human-readable name declared in the skill's manifest metadata.
 * @param certSha256   SHA-256 fingerprint of the skill's signing certificate.
 * @param capabilities JSON-serialized Set<Capability> as stored in [SkillTrustEntry].
 */
data class DiscoveredSkill(
    val packageName: String,
    val displayName: String,
    val certSha256: String,
    val capabilities: String,
)

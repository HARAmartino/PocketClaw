package com.pocketclaw.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Plugin trust record. Plugins with an unknown or untrusted certificate SHA-256
 * are never loaded. User must explicitly approve each new plugin.
 */
@Entity(tableName = "plugin_trust_store")
data class PluginTrustEntry(
    @PrimaryKey val packageName: String,
    val certSha256: String,
    val approvedAtMs: Long,
    val approvedByUser: Boolean,
    /** JSON-serialized Set<Capability> declared by the plugin. */
    val capabilities: String,
    val isTrusted: Boolean,
)

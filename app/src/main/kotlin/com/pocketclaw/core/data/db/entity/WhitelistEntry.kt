package com.pocketclaw.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An allowed outbound network domain.
 * The [com.pocketclaw.agent.security.NetworkGateway] OkHttp interceptor checks
 * every outbound request against this table. Requests to unlisted domains are
 * blocked and logged immediately — no bypass possible.
 */
@Entity(tableName = "whitelist_store")
data class WhitelistEntry(
    @PrimaryKey val domain: String,
    val addedAtMs: Long,
    /** Origin of the whitelist entry: USER, BUILTIN, or PLUGIN. */
    val addedBy: String,
    val note: String = "",
)

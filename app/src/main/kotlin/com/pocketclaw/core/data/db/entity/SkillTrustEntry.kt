package com.pocketclaw.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Skill trust record. Skills with an unknown or untrusted certificate SHA-256
 * are never loaded. User must explicitly approve each new skill.
 */
@Entity(tableName = "skill_trust_store")
data class SkillTrustEntry(
    @PrimaryKey val packageName: String,
    val certSha256: String,
    val approvedAtMs: Long,
    val approvedByUser: Boolean,
    /** JSON-serialized Set<Capability> declared by the skill. */
    val capabilities: String,
    val isTrusted: Boolean,
)

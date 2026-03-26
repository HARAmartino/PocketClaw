package com.pocketclaw.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trigger_store")
data class TriggerEntry(
    @PrimaryKey val id: String,
    val taskType: String,
    val title: String,
    val goalPrompt: String,
    val scheduleIntervalMs: Long?,
    val createdAtMs: Long,
)

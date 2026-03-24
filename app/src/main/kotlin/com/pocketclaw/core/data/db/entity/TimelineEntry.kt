package com.pocketclaw.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One step in a task's execution timeline.
 * Each entry records the reasoning, action type, optional screenshot path,
 * and validation result for that step.
 */
@Entity(
    tableName = "timeline_entries",
    foreignKeys = [
        ForeignKey(
            entity = TaskJournalEntry::class,
            parentColumns = ["taskId"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class TimelineEntry(
    @PrimaryKey val id: String,
    val taskId: String,
    val stepIndex: Int,
    val taskType: String,
    val reasoning: String,
    val actionType: String,
    val screenshotPath: String? = null,
    val validationResult: String,
    val timestampMs: Long,
)

package com.pocketclaw.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Classification of task origin. */
enum class TaskType { USER, HEARTBEAT, IPC, SCHEDULED }

/** Lifecycle state of a task. Append-only — never delete records. */
enum class TaskStatus { PENDING, EXECUTING, COMMITTED, FAILED, ROLLED_BACK }

/**
 * Append-only audit log for all agent tasks.
 * On startup, any [TaskStatus.EXECUTING] entries indicate an interrupted task
 * and trigger a "Resume / Discard" dialog. Never silently resume.
 */
@Entity(tableName = "task_journal")
data class TaskJournalEntry(
    @PrimaryKey val taskId: String,
    val taskType: TaskType,
    val title: String,
    val status: TaskStatus,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val completedAtMs: Long? = null,
    val errorMessage: String? = null,
    val iterationCount: Int = 0,
    /** JSON array of last 10 SHA-256 hashes used for loop detection. */
    val loopDetectionHashes: String = "[]",
)

package com.pocketclaw.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketclaw.core.data.db.entity.TaskJournalEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskJournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TaskJournalEntry)

    @Query("SELECT * FROM task_journal WHERE status = 'EXECUTING'")
    suspend fun getExecutingTasks(): List<TaskJournalEntry>

    @Query("SELECT * FROM task_journal WHERE taskId = :taskId")
    suspend fun getById(taskId: String): TaskJournalEntry?

    @Query("SELECT * FROM task_journal ORDER BY createdAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TaskJournalEntry>>

    @Query("UPDATE task_journal SET status = :status, updatedAtMs = :nowMs WHERE taskId = :taskId")
    suspend fun updateStatus(taskId: String, status: String, nowMs: Long)

    @Query(
        "UPDATE task_journal SET " +
            "iterationCount = iterationCount + 1, " +
            "updatedAtMs = :nowMs " +
            "WHERE taskId = :taskId",
    )
    suspend fun incrementIteration(taskId: String, nowMs: Long)

    @Query(
        "UPDATE task_journal SET " +
            "loopDetectionHashes = :hashesJson, " +
            "updatedAtMs = :nowMs " +
            "WHERE taskId = :taskId",
    )
    suspend fun updateLoopHashes(taskId: String, hashesJson: String, nowMs: Long)
}

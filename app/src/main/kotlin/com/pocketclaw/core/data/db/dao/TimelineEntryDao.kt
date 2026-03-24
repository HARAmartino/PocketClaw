package com.pocketclaw.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pocketclaw.core.data.db.entity.TimelineEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEntryDao {

    @Insert
    suspend fun insert(entry: TimelineEntry)

    @Query("SELECT * FROM timeline_entries WHERE taskId = :taskId ORDER BY stepIndex ASC")
    fun observeForTask(taskId: String): Flow<List<TimelineEntry>>

    @Query("SELECT * FROM timeline_entries WHERE taskId = :taskId ORDER BY stepIndex ASC")
    suspend fun getForTask(taskId: String): List<TimelineEntry>

    @Query("DELETE FROM timeline_entries WHERE timestampMs < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)
}

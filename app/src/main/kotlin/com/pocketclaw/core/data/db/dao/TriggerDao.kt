package com.pocketclaw.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketclaw.core.data.db.entity.TriggerEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TriggerEntry)

    @Delete
    suspend fun delete(entry: TriggerEntry)

    @Query("SELECT * FROM trigger_store ORDER BY createdAtMs ASC")
    fun observeAll(): Flow<List<TriggerEntry>>
}

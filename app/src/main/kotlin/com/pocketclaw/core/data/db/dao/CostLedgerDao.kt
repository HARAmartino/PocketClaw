package com.pocketclaw.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pocketclaw.core.data.db.entity.CostLedgerEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CostLedgerDao {

    @Insert
    suspend fun insert(entry: CostLedgerEntry)

    @Query("SELECT SUM(inputTokens + outputTokens) FROM cost_ledger WHERE timestampMs >= :sinceMs")
    suspend fun totalTokensSince(sinceMs: Long): Long?

    @Query("SELECT SUM(estimatedCostUsd) FROM cost_ledger WHERE timestampMs >= :sinceMs")
    suspend fun totalCostSince(sinceMs: Long): Double?

    @Query("SELECT * FROM cost_ledger ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CostLedgerEntry>>
}

package com.pocketclaw.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketclaw.core.data.db.entity.WhitelistEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistStoreDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entry: WhitelistEntry)

    @Delete
    suspend fun remove(entry: WhitelistEntry)

    @Query("SELECT COUNT(*) FROM whitelist_store WHERE domain = :domain")
    suspend fun isDomainAllowed(domain: String): Int

    @Query("SELECT * FROM whitelist_store ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<WhitelistEntry>>
}

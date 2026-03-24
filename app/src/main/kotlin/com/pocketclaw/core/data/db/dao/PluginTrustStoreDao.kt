package com.pocketclaw.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketclaw.core.data.db.entity.PluginTrustEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginTrustStoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PluginTrustEntry)

    @Query("SELECT * FROM plugin_trust_store WHERE packageName = :packageName")
    suspend fun get(packageName: String): PluginTrustEntry?

    @Query("SELECT * FROM plugin_trust_store WHERE isTrusted = 1")
    fun observeTrusted(): Flow<List<PluginTrustEntry>>
}

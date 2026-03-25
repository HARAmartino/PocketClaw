package com.pocketclaw.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketclaw.core.data.db.entity.SkillTrustEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillTrustStoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SkillTrustEntry)

    @Query("SELECT * FROM skill_trust_store WHERE packageName = :packageName")
    suspend fun get(packageName: String): SkillTrustEntry?

    @Query("SELECT * FROM skill_trust_store WHERE isTrusted = 1")
    fun observeTrusted(): Flow<List<SkillTrustEntry>>
}

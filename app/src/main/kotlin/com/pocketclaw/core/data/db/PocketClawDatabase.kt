package com.pocketclaw.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pocketclaw.core.data.db.dao.CostLedgerDao
import com.pocketclaw.core.data.db.dao.PluginTrustStoreDao
import com.pocketclaw.core.data.db.dao.TaskJournalDao
import com.pocketclaw.core.data.db.dao.TimelineEntryDao
import com.pocketclaw.core.data.db.dao.WhitelistStoreDao
import com.pocketclaw.core.data.db.entity.CostLedgerEntry
import com.pocketclaw.core.data.db.entity.PluginTrustEntry
import com.pocketclaw.core.data.db.entity.TaskJournalEntry
import com.pocketclaw.core.data.db.entity.TimelineEntry
import com.pocketclaw.core.data.db.entity.WhitelistEntry

@Database(
    entities = [
        TaskJournalEntry::class,
        TimelineEntry::class,
        CostLedgerEntry::class,
        WhitelistEntry::class,
        PluginTrustEntry::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PocketClawDatabase : RoomDatabase() {
    abstract fun taskJournalDao(): TaskJournalDao
    abstract fun timelineEntryDao(): TimelineEntryDao
    abstract fun costLedgerDao(): CostLedgerDao
    abstract fun whitelistStoreDao(): WhitelistStoreDao
    abstract fun pluginTrustStoreDao(): PluginTrustStoreDao
}

package com.pocketclaw.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketclaw.agent.capability.CapabilityEnforcer
import com.pocketclaw.agent.capability.CapabilityEnforcerImpl
import com.pocketclaw.agent.llm.LlmOutputValidator
import com.pocketclaw.agent.llm.PassthroughPrivacyRouter
import com.pocketclaw.agent.llm.PrivacyRouter
import com.pocketclaw.agent.scheduler.HeartbeatManager
import com.pocketclaw.agent.scheduler.HeartbeatManagerImpl
import com.pocketclaw.agent.skill.SkillDiscoverer
import com.pocketclaw.agent.skill.SkillLoader
import com.pocketclaw.agent.security.HardcodedSecurityPolicy
import com.pocketclaw.agent.security.NetworkGateway
import com.pocketclaw.agent.security.SecurityPolicy
import com.pocketclaw.agent.security.SuspicionScorer
import com.pocketclaw.agent.security.SuspicionScorerImpl
import com.pocketclaw.agent.security.TrustedInputBoundary
import com.pocketclaw.agent.security.TrustedInputBoundaryImpl
import com.pocketclaw.agent.validator.ActionValidator
import com.pocketclaw.agent.validator.ActionValidatorImpl
import com.pocketclaw.core.data.db.PocketClawDatabase
import com.pocketclaw.core.data.db.dao.CostLedgerDao
import com.pocketclaw.core.data.db.dao.SkillTrustStoreDao
import com.pocketclaw.core.data.db.dao.TaskJournalDao
import com.pocketclaw.core.data.db.dao.TimelineEntryDao
import com.pocketclaw.core.data.db.dao.TriggerDao
import com.pocketclaw.core.data.db.dao.WhitelistStoreDao
import com.pocketclaw.core.data.secret.SecretStore
import com.pocketclaw.core.data.secret.SecretStoreImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSecretStore(impl: SecretStoreImpl): SecretStore

    @Binds
    @Singleton
    abstract fun bindActionValidator(impl: ActionValidatorImpl): ActionValidator

    @Binds
    @Singleton
    abstract fun bindCapabilityEnforcer(impl: CapabilityEnforcerImpl): CapabilityEnforcer

    @Binds
    @Singleton
    abstract fun bindTrustedInputBoundary(impl: TrustedInputBoundaryImpl): TrustedInputBoundary

    @Binds
    @Singleton
    abstract fun bindSuspicionScorer(impl: SuspicionScorerImpl): SuspicionScorer

    @Binds
    @Singleton
    abstract fun bindPrivacyRouter(impl: PassthroughPrivacyRouter): PrivacyRouter

    @Binds
    @Singleton
    abstract fun bindSecurityPolicy(impl: HardcodedSecurityPolicy): SecurityPolicy

    @Binds
    @Singleton
    abstract fun bindHeartbeatManager(impl: HeartbeatManagerImpl): HeartbeatManager

    @Binds
    @Singleton
    abstract fun bindSkillDiscoverer(impl: SkillLoader): SkillDiscoverer
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Built-in whitelist domains seeded on fresh install. */
    val BUILTIN_WHITELIST_DOMAINS = listOf(
        "api.openai.com",
        "api.anthropic.com",
        "api.telegram.org",
        "gmail.googleapis.com",
        "oauth2.googleapis.com",
        "discord.com",
    )

    /**
     * Placeholder migration from schema version 1 to 2.
     *
     * No schema change is introduced in this version — the migration exists to
     * establish the migration chain and prevent destructive data loss during upgrades.
     * Actual schema changes will be added here in subsequent phases.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No schema change in this version.
            // Placeholder to establish the 1 → 2 migration chain.
        }
    }

    /**
     * Migration from schema version 2 to 3.
     *
     * Adds the [TaskType.NOTIFICATION] enum value to the TaskType domain.
     * Because TaskType is stored as its [Enum.name] string in SQLite, no
     * DDL change is required — the new string value ("NOTIFICATION") is
     * backward-compatible with existing rows.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No schema change required: TaskType is stored as a String name.
            // The new NOTIFICATION value is additive and backward-compatible.
        }
    }

    /**
     * Migration from schema version 3 to 4.
     *
     * Renames the plugin trust store table to `skill_trust_store` to align with
     * the Terminology Unification (Phase 6 Part A). The table structure is unchanged;
     * only the name is updated.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE plugin_trust_store RENAME TO skill_trust_store",
            )
        }
    }

    /**
     * Migration from schema version 4 to 5.
     *
     * Adds the [com.pocketclaw.core.data.db.entity.TriggerEntry] table
     * (`trigger_store`) to persist scheduled task trigger configurations in
     * Room rather than DataStore.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trigger_store` (
                    `id` TEXT NOT NULL,
                    `taskType` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `goalPrompt` TEXT NOT NULL,
                    `scheduleIntervalMs` INTEGER,
                    `createdAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun providePocketClawDatabase(
        @ApplicationContext context: Context,
    ): PocketClawDatabase = Room.databaseBuilder(
        context,
        PocketClawDatabase::class.java,
        "pocketclaw.db",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                BUILTIN_WHITELIST_DOMAINS.forEach { domain ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO whitelist_store " +
                            "(domain, addedAtMs, addedBy, note) " +
                            "VALUES (?, ?, 'BUILTIN', 'Default seed')",
                        arrayOf(domain, now),
                    )
                }
            }
        })
        .build()

    @Provides
    fun provideTaskJournalDao(db: PocketClawDatabase): TaskJournalDao = db.taskJournalDao()

    @Provides
    fun provideTimelineEntryDao(db: PocketClawDatabase): TimelineEntryDao = db.timelineEntryDao()

    @Provides
    fun provideCostLedgerDao(db: PocketClawDatabase): CostLedgerDao = db.costLedgerDao()

    @Provides
    fun provideWhitelistStoreDao(db: PocketClawDatabase): WhitelistStoreDao = db.whitelistStoreDao()

    @Provides
    fun provideSkillTrustStoreDao(db: PocketClawDatabase): SkillTrustStoreDao = db.skillTrustStoreDao()

    @Provides
    fun provideTriggerDao(db: PocketClawDatabase): TriggerDao = db.triggerDao()
}

private val Context.prefDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pocketclaw_prefs",
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.prefDataStore
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
        coerceInputValues = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        networkGateway: NetworkGateway,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(networkGateway) // Domain whitelist enforcement (Layer 3)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideKtorHttpClient(
        okHttpClient: OkHttpClient,
        json: Json,
    ): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
        install(Logging) {
            level = LogLevel.HEADERS
        }
    }

    @Provides
    @Singleton
    fun provideLlmOutputValidator(json: Json): LlmOutputValidator = LlmOutputValidator(json)
}

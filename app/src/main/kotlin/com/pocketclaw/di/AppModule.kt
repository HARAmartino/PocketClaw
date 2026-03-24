package com.pocketclaw.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.pocketclaw.agent.capability.CapabilityEnforcer
import com.pocketclaw.agent.capability.CapabilityEnforcerImpl
import com.pocketclaw.agent.llm.LlmOutputValidator
import com.pocketclaw.agent.llm.PassthroughPrivacyRouter
import com.pocketclaw.agent.llm.PrivacyRouter
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
import com.pocketclaw.core.data.db.dao.PluginTrustStoreDao
import com.pocketclaw.core.data.db.dao.TaskJournalDao
import com.pocketclaw.core.data.db.dao.TimelineEntryDao
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
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePocketClawDatabase(
        @ApplicationContext context: Context,
    ): PocketClawDatabase = Room.databaseBuilder(
        context,
        PocketClawDatabase::class.java,
        "pocketclaw.db",
    )
        .fallbackToDestructiveMigration()
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
    fun providePluginTrustStoreDao(db: PocketClawDatabase): PluginTrustStoreDao = db.pluginTrustStoreDao()
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

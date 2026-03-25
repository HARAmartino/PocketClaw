# ─── Hilt generated components ────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}

# ─── Room entities and DAOs ───────────────────────────────────────────────────
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# ─── kotlinx.serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}

# ─── Ktor (OkHttp engine + serialization) ────────────────────────────────────
-keep class io.ktor.** { *; }
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.serialization.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── PocketClaw LLM schema classes ───────────────────────────────────────────
-keep class com.pocketclaw.agent.llm.schema.LlmAction { *; }
-keep class com.pocketclaw.agent.llm.schema.LlmToolCall { *; }
-keep class com.pocketclaw.agent.llm.schema.LlmHitlEscalation { *; }
-keep class com.pocketclaw.agent.llm.schema.LlmResponse { *; }
-keep class com.pocketclaw.agent.llm.schema.LlmConfig { *; }
-keep class com.pocketclaw.agent.llm.schema.Message { *; }
-keep class com.pocketclaw.agent.llm.schema.MessageRole { *; }
-keep class com.pocketclaw.agent.llm.schema.ToolDefinition { *; }
-keep class com.pocketclaw.agent.llm.schema.NodeBounds { *; }
-keep class com.pocketclaw.agent.llm.schema.LlmOutputType { *; }
-keep class com.pocketclaw.agent.llm.schema.AccessibilityActionType { *; }
-keep class com.pocketclaw.agent.llm.schema.HitlEscalationReason { *; }
-keep class com.pocketclaw.agent.llm.schema.ParsedLlmOutput { *; }
-keep class com.pocketclaw.agent.llm.schema.LlmValidationError { *; }
-keep class com.pocketclaw.agent.llm.schema.** { *; }

# ─── EncryptedSharedPreferences / MasterKey ──────────────────────────────────
-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }
-keep class androidx.security.crypto.MasterKey$Builder { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

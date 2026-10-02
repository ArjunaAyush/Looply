# Looply ProGuard & R8 Optimization Rules
# Ensures maximum code & resource shrinking while strictly preserving Room database integrity,
# Hilt dependency injection, Media3 ExoPlayer playback, and background ingestion.

# -----------------------------------------------------------------------------
# 1. ROOM DATABASE (CRITICAL: Preserves SQLite Tables, Columns & Reflection)
# -----------------------------------------------------------------------------
# Preserve all Room database definitions and generated _Impl classes
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class com.arjunaayush.looply.core.database.VideoDatabase** { *; }

# Preserve all Entities and their exact property names (columns in SQLite)
-keep @androidx.room.Entity class * { *; }
-keep class com.arjunaayush.looply.core.database.entity.** { *; }
-keepclassmembers class com.arjunaayush.looply.core.database.entity.** {
    <fields>;
    <methods>;
}

# Preserve all DAOs and their query methods
-keep @androidx.room.Dao interface * { *; }
-keep class * implements com.arjunaayush.looply.core.database.dao.** { *; }
-keep class com.arjunaayush.looply.core.database.dao.** { *; }

# Preserve TypeConverters
-keep class * {
    @androidx.room.TypeConverter <methods>;
}

-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# 2. DOMAIN MODELS & DATA TRANSFER OBJECTS
# -----------------------------------------------------------------------------
-keep class com.arjunaayush.looply.domain.model.** { *; }
-keep class com.arjunaayush.looply.core.network.instagram.ReelCandidate { *; }
-keep class com.arjunaayush.looply.core.network.instagram.config.** { *; }
-keep class com.arjunaayush.looply.core.network.instagram.capture.BridgeEvent** { *; }

# -----------------------------------------------------------------------------
# 3. HILT & DEPENDENCY INJECTION
# -----------------------------------------------------------------------------
-keep class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class com.arjunaayush.looply.**_HiltModules* { *; }
-keep class * implements dagger.hilt.internal.ComponentManager
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @dagger.Provides *;
}

# -----------------------------------------------------------------------------
# 4. WORKMANAGER & BACKGROUND WORKERS
# -----------------------------------------------------------------------------
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.** { *; }

# -----------------------------------------------------------------------------
# 5. MEDIA3 & EXOPLAYER
# -----------------------------------------------------------------------------
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# -----------------------------------------------------------------------------
# 6. WEBVIEW & JAVASCRIPT BRIDGES
# -----------------------------------------------------------------------------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.arjunaayush.looply.core.network.instagram.capture.WebViewReelSource$** { *; }

# -----------------------------------------------------------------------------
# 7. JSOUP & COIL
# -----------------------------------------------------------------------------
-keep public class org.jsoup.** {
    public *;
}
-dontwarn org.jsoup.**

-keep class coil.** { *; }
-dontwarn coil.**

# -----------------------------------------------------------------------------
# 8. KOTLIN COROUTINES & COMPOSE
# -----------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class androidx.compose.ui.platform.** {
    public static ** ...;
}

# -----------------------------------------------------------------------------
# 9. GENERAL STRIPPING OPTIMIZATIONS
# -----------------------------------------------------------------------------
# Strip verbose and debug logging in release builds to shrink dex & improve speed
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

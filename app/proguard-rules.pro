# Kaon Music ProGuard / R8 Rules (Full Mode)

# Room Database, Entities, DAOs & Migrations
-keep class * extends androidx.room.RoomDatabase
-keep class com.kaon.music.core.data.db.entity.** { *; }
-keep class com.kaon.music.core.data.db.dao.** { *; }
-keep class com.kaon.music.core.data.db.KaonDatabase_Impl { *; }
-dontwarn androidx.room.paging.**

# Domain Models
-keep class com.kaon.music.core.data.model.** { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-keep class com.kaon.music.core.playback.KaonPlaybackService { *; }

# Coil
-keepclassmembers class * implements io.coil-kt.coil3.decode.Decoder$Factory { *; }
-keepclassmembers class * implements io.coil-kt.coil3.fetch.Fetcher$Factory { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Timber
-dontwarn timber.log.**

# ARCHITECTURE.md §5.3: strip DEBUG/VERBOSE logging from release builds. The extraction layer logs
# heavily at debug level; removing the call sites is what guarantees none of it can reach logcat in
# a shipped build, independently of which Tree is planted.
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
}
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
}

# InnerTube & Serialization
-keep class com.metrolist.innertube.models.** { *; }
-keep class com.metrolist.innertubex.models.** { *; }
# innertubex's extraction and cipher packages are reached reflectively through kotlinx.serialization
# and JNI (QuickJS), so R8 cannot see all of their entry points.
-keep class com.metrolist.innertubex.extraction.** { *; }
-keep class com.metrolist.innertubex.cipher.** { *; }
-keep class com.dokar.quickjs.** { *; }
-dontwarn com.dokar.quickjs.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-dontwarn kotlinx.serialization.**
-dontwarn io.ktor.**
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**

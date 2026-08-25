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

# InnerTube & Serialization
-keep class com.metrolist.innertube.models.** { *; }
-keep class com.metrolist.innertubex.models.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-dontwarn kotlinx.serialization.**
-dontwarn io.ktor.**
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**

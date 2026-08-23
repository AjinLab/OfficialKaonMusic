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


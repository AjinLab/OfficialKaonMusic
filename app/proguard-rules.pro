# Kaon Music ProGuard / R8 Rules

# Keep Room generated migrations and schemas
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 keep rules
-keep class androidx.media3.** { *; }

# Coil
-keepclassmembers class * implements io.coil-kt.coil3.decode.Decoder$Factory { *; }
-keepclassmembers class * implements io.coil-kt.coil3.fetch.Fetcher$Factory { *; }

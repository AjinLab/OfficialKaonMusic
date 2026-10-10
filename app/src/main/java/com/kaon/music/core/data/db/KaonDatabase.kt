package com.kaon.music.core.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.PlaylistDao
import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.db.entity.TrackEntity

/**
 * Main Room Database for Kaon Music.
 *
 * Separation of Concerns (ARCHITECTURE.md §6):
 * - Derived table: [TrackEntity] — rebuildable from MediaStore for local rows.
 * - User-owned tables: [FavoriteTrackEntity], [PlayEventEntity], [PlaylistEntity], [PlaylistTrackEntity].
 *   None of them cascades from `tracks` any more: user data must not be destroyed because a file moved.
 * - Operational table: [QueueSnapshotEntity] — best-effort restoration.
 *
 * Migrations up to v7 are index/column additions Room can generate. v7 → v8 is the identity migration
 * and is hand-written ([MIGRATION_7_8]): it translates `source`/`youtube_video_id` into
 * `provider`/`external_id`, relaxes `media_store_id`/`relative_path` to nullable, and rebuilds the two
 * user-owned tables without their destructive foreign keys — none of which SQLite can do in place.
 * `Phase4IdentityMigrationTest` migrates a realistic v7 database and asserts the data survives.
 */
@Database(
    entities = [
        TrackEntity::class,
        FavoriteTrackEntity::class,
        PlayEventEntity::class,
        QueueSnapshotEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7)
    ],
    exportSchema = true
)
abstract class KaonDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun queueSnapshotDao(): QueueSnapshotDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        private const val DATABASE_NAME = "kaon_music.db"

        /**
         * Migrations Room cannot generate. Every builder — production and test — must add these, so
         * they live here rather than at the single call site.
         */
        val MANUAL_MIGRATIONS = arrayOf(MIGRATION_7_8)

        @Volatile
        private var INSTANCE: KaonDatabase? = null

        fun getInstance(context: Context): KaonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaonDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*MANUAL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

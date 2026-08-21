package com.kaon.music.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.db.entity.TrackEntity

/**
 * Main Room Database for Kaon Music.
 *
 * Separation of Concerns (from ARCHITECTURE_ATTRIBUTED.md §7):
 * - Derived tables: [TrackEntity] (rebuildable from MediaStore).
 * - User-owned tables: [FavoriteTrackEntity], [PlayEventEntity] (migrations are sacred).
 * - Operational tables: [QueueSnapshotEntity] (best-effort restoration).
 */
@Database(
    entities = [
        TrackEntity::class,
        FavoriteTrackEntity::class,
        PlayEventEntity::class,
        QueueSnapshotEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KaonDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun queueSnapshotDao(): QueueSnapshotDao

    companion object {
        private const val DATABASE_NAME = "kaon_music.db"

        @Volatile
        private var INSTANCE: KaonDatabase? = null

        fun getInstance(context: Context): KaonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaonDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

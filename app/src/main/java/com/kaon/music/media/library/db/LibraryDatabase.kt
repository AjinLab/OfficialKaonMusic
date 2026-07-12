package com.kaon.music.media.library.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kaon.music.media.library.db.dao.AlbumDao
import com.kaon.music.media.library.db.dao.ArtistDao
import com.kaon.music.media.library.db.dao.LibraryStateDao
import com.kaon.music.media.library.db.dao.LibrarySyncDao
import com.kaon.music.media.library.db.dao.PlaylistDao
import com.kaon.music.media.library.db.dao.SearchDao
import com.kaon.music.media.library.db.dao.SongDao
import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.ArtistEntity
import com.kaon.music.media.library.db.entity.LibraryStateEntity
import com.kaon.music.media.library.db.entity.PlaybackStatisticsEntity
import com.kaon.music.media.library.db.entity.PlaylistEntity
import com.kaon.music.media.library.db.entity.PlaylistSongEntity
import com.kaon.music.media.library.db.entity.SongEntity
import com.kaon.music.media.library.db.entity.FavoriteEntity
import com.kaon.music.media.library.db.dao.FavoriteDao

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlaybackStatisticsEntity::class,
        LibraryStateEntity::class,
        FavoriteEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchDao(): SearchDao
    abstract fun libraryStateDao(): LibraryStateDao
    abstract fun librarySyncDao(): LibrarySyncDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val DATABASE_NAME = "kaon_library_room.db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)")
            }
        }
    }
}

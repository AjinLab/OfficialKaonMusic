package com.kaon.music.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v6 → v7 migration.
 *
 * The change is index-only: composite indexes replace single-column ones on `tracks`. Room's
 * auto-migration executes `CREATE INDEX`/`DROP INDEX`, which does not touch a row — but
 * ARCHITECTURE.md §6 requires every schema bump to ship a test asserting user-owned data survives,
 * and "it should be fine" is exactly the assumption that loses a user's favorites. This builds a real
 * v6 database with user data, migrates it, and checks the data plus the new index set.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScaleMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "test_migration_v6_v7.db"

    @Before
    fun setup() {
        context.getDatabasePath(dbName).takeIf { it.exists() }?.delete()
    }

    @After
    fun tearDown() {
        context.getDatabasePath(dbName).takeIf { it.exists() }?.delete()
    }

    @Test
    fun migrationV6ToV7PreservesUserDataAndInstallsCompositeIndexes() = runBlocking {
        writeV6DatabaseWithUserData()

        val db = Room.databaseBuilder(context, KaonDatabase::class.java, dbName)
            // v8's manual migration must be on every builder: this test database now has to chain
            // through it (6→7 auto, 7→8 hand-written) like production does.
            .addMigrations(*KaonDatabase.MANUAL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // Derived data survives.
        assertEquals(100, db.trackDao().observeAllActiveTracks().first().size)
        assertNotNull(db.trackDao().getTrackById(1L))

        // User-owned data survives — the point of the test.
        assertEquals(40, db.favoriteDao().observeFavoriteTrackIds().first().size)
        val playlists = db.playlistDao().observeAllPlaylistsWithCount().first()
        assertEquals(5, playlists.size)
        assertEquals(10, playlists.first { it.name == "Playlist 1" }.track_count)
        assertTrue(db.playEventDao().observeRecentlyPlayedTrackEntities(limit = 200).first().isNotEmpty())

        // The new query shapes are index-backed after migration, not just after a fresh create.
        val indexNames = db.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'tracks'")
            .use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }

        assertTrue(indexNames.contains("index_tracks_is_missing_title_normalized"))
        assertTrue(indexNames.contains("index_tracks_is_missing_date_added"))
        assertTrue(indexNames.contains("index_tracks_is_missing_album_normalized"))
        assertTrue(indexNames.contains("index_tracks_is_missing_artist_normalized"))
        assertTrue(indexNames.contains("index_tracks_album_id_disc_number_track_number"))
        // Superseded single-column indexes are gone: keeping them would be write cost on every sync
        // for queries that no longer exist.
        assertTrue(indexNames.none { it == "index_tracks_title_normalized" })
        assertTrue(indexNames.none { it == "index_tracks_date_added" })

        db.close()
    }

    /** Builds the exported v6 schema by hand, then fills it with user-owned rows. */
    private fun writeV6DatabaseWithUserData() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val v6 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)

        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `tracks` (`track_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`media_store_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                "`artist_id` INTEGER NOT NULL DEFAULT 0, `album` TEXT NOT NULL, `album_id` INTEGER NOT NULL, " +
                "`track_number` INTEGER NOT NULL DEFAULT 0, `disc_number` INTEGER NOT NULL DEFAULT 1, " +
                "`year` INTEGER NOT NULL DEFAULT 0, `duration_ms` INTEGER NOT NULL, `size_bytes` INTEGER NOT NULL, " +
                "`date_modified` INTEGER NOT NULL, `date_added` INTEGER NOT NULL DEFAULT 0, " +
                "`relative_path` TEXT NOT NULL, `title_normalized` TEXT NOT NULL, `artist_normalized` TEXT NOT NULL, " +
                "`album_normalized` TEXT NOT NULL, `is_missing` INTEGER NOT NULL DEFAULT 0, " +
                "`source` TEXT NOT NULL DEFAULT 'LOCAL', `youtube_video_id` TEXT, `mime_type` TEXT, " +
                "`last_seen_timestamp` INTEGER NOT NULL)"
        )
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_tracks_media_store_id` ON `tracks` (`media_store_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_duration_ms_size_bytes` ON `tracks` (`duration_ms`, `size_bytes`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_title_normalized` ON `tracks` (`title_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_artist_normalized` ON `tracks` (`artist_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_album_normalized` ON `tracks` (`album_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_album_id` ON `tracks` (`album_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_artist_id` ON `tracks` (`artist_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_date_added` ON `tracks` (`date_added`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_youtube_video_id` ON `tracks` (`youtube_video_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_source` ON `tracks` (`source`)"
        ).forEach(v6::execSQL)

        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite_tracks` (`track_id` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`track_id`), FOREIGN KEY(`track_id`) REFERENCES `tracks`(`track_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        v6.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_tracks_track_id` ON `favorite_tracks` (`track_id`)")
        v6.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_tracks_added_at` ON `favorite_tracks` (`added_at`)")

        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `play_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`track_id` INTEGER NOT NULL, `event_type` TEXT NOT NULL, `played_at` INTEGER NOT NULL, " +
                "`played_ms` INTEGER NOT NULL, FOREIGN KEY(`track_id`) REFERENCES `tracks`(`track_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_play_events_track_id` ON `play_events` (`track_id`)",
            "CREATE INDEX IF NOT EXISTS `index_play_events_played_at` ON `play_events` (`played_at`)",
            "CREATE INDEX IF NOT EXISTS `index_play_events_event_type` ON `play_events` (`event_type`)",
            "CREATE INDEX IF NOT EXISTS `index_play_events_event_type_track_id_played_at` " +
                "ON `play_events` (`event_type`, `track_id`, `played_at`)"
        ).forEach(v6::execSQL)

        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `queue_snapshot` (`id` INTEGER NOT NULL, `serialized_track_ids` TEXT NOT NULL, " +
                "`current_index` INTEGER NOT NULL, `current_position_ms` INTEGER NOT NULL, " +
                "`is_shuffle_enabled` INTEGER NOT NULL, `repeat_mode` INTEGER NOT NULL, `saved_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`playlist_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)"
        )
        v6.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_name` ON `playlists` (`name`)")
        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlist_id` INTEGER NOT NULL, `track_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `track_id`), " +
                "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`playlist_id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        v6.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlist_id_position` " +
                "ON `playlist_tracks` (`playlist_id`, `position`)"
        )
        v6.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_track_id` ON `playlist_tracks` (`track_id`)")

        for (i in 1..100) {
            v6.execSQL(
                "INSERT INTO tracks (track_id, media_store_id, title, artist, artist_id, album, album_id, " +
                    "track_number, disc_number, year, duration_ms, size_bytes, date_modified, date_added, " +
                    "relative_path, title_normalized, artist_normalized, album_normalized, is_missing, source, " +
                    "last_seen_timestamp) VALUES ($i, ${1000 + i}, 'Track $i', 'Artist ${i % 10}', ${i % 10}, " +
                    "'Album ${i % 5}', ${i % 5}, $i, 1, 2020, 200000, 4000000, 1, $i, 'Music/t$i.mp3', " +
                    "'track $i', 'artist ${i % 10}', 'album ${i % 5}', 0, 'LOCAL', 1)"
            )
        }
        for (i in 1..40) {
            v6.execSQL("INSERT INTO favorite_tracks (track_id, added_at) VALUES ($i, ${5000 + i})")
        }
        for (p in 1..5) {
            v6.execSQL("INSERT INTO playlists (playlist_id, name, created_at, updated_at) VALUES ($p, 'Playlist $p', 1, 1)")
            for (pos in 0 until 10) {
                val trackId = (p - 1) * 10 + pos + 1
                v6.execSQL(
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at) " +
                        "VALUES ($p, $trackId, $pos, 1)"
                )
            }
        }
        for (e in 1..200) {
            v6.execSQL(
                "INSERT INTO play_events (track_id, event_type, played_at, played_ms) " +
                    "VALUES (${(e % 100) + 1}, 'PLAY', ${10_000 + e}, 150000)"
            )
        }

        v6.version = 6
        v6.close()
    }
}

package com.kaon.music.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.db.toDomain
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.ProviderId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v7 → v8 identity migration, against a real v7 database.
 *
 * The exported 7.json was once overwritten with v8 columns, which is exactly the kind of fiction this
 * test exists to catch: it builds the v7 schema the way v7 actually shipped (raw SQLite, `source` and
 * `youtube_video_id` columns, CASCADE foreign keys still present) and migrates it with the production
 * [com.kaon.music.core.data.db.MIGRATION_7_8]. Nothing here trusts the exported schema files.
 *
 * The dataset mirrors spec §24: local tracks, missing tracks, favorites, history, playlists with
 * ordering, YouTube rows (with one deliberate duplicate video id, which v7 had no constraint against).
 */
@RunWith(AndroidJUnit4::class)
class Phase4IdentityMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "test_phase4_migration_v7_v8.db"

    @Before
    fun setup() {
        context.getDatabasePath(dbName).takeIf { it.exists() }?.delete()
    }

    @After
    fun tearDown() {
        context.getDatabasePath(dbName).takeIf { it.exists() }?.delete()
    }

    @Test
    fun migrationPreservesUserDataAndTranslatesIdentity() = runBlocking {
        writeV7DatabaseWithUserData()

        val db = Room.databaseBuilder(context, KaonDatabase::class.java, dbName)
            .addMigrations(*KaonDatabase.MANUAL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // --- Track rows survive with ids verbatim (user data references them). ---
        // 12 is gone by design: it duplicated 11's video id and merged into it.
        val tracks = db.trackDao().getTracksByIds(listOf(1L, 2L, 3L, 4L, 10L, 11L))
        assertEquals(6, tracks.size)

        val local = tracks.first { it.trackId == 1L }
        assertEquals("LOCAL", local.provider)
        assertNull(local.externalId)
        assertNotNull(local.mediaStoreId)
        assertFalse(local.isMissing)

        val missing = tracks.first { it.trackId == 2L }
        assertTrue("missing state survives", missing.isMissing)
        assertEquals("LOCAL", missing.provider)

        val youtube = tracks.first { it.trackId == 10L }
        assertEquals("YOUTUBE", youtube.provider)
        assertEquals("dQw4w9WgXcQ", youtube.externalId)
        assertNull("a stream has no MediaStore id", youtube.mediaStoreId)
        assertNull(youtube.relativePath)

        // --- Duplicate video ids merged lowest-id-wins; user data re-pointed at the survivor. ---
        val merged = db.trackDao().getTrackByExternalIdentity("YOUTUBE", "jNQXAC9IVRw")
        assertEquals("only one row per provider identity", 11L, merged!!.trackId)
        assertNull(db.trackDao().getTrackById(12L))

        // --- Favorites survive — including the one that pointed at the merged duplicate. ---
        val favorites = db.favoriteDao().observeFavoriteTrackIds().first()
        assertEquals(setOf(1L, 2L, 10L, 11L), favorites.toSet())

        // --- History survives, and the merged duplicate's events moved to the survivor. ---
        val playEventCount = db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM play_events WHERE track_id = 11").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
        assertEquals("events of the deleted duplicate follow the survivor", 3, playEventCount)

        // --- Playlist membership survives with position order. ---
        val playlistTracks = db.openHelper.readableDatabase
            .query("SELECT track_id, position FROM playlist_tracks WHERE playlist_id = 1 ORDER BY position")
            .use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0) to c.getInt(1)) }
            }
        assertEquals(listOf(1L to 0, 10L to 1, 11L to 2), playlistTracks)

        // --- The dangerous CASCADE is gone from user-owned tables. ---
        val favoriteFks = foreignKeysOf(db, "favorite_tracks")
        assertTrue("favorites must not cascade from tracks", favoriteFks.isEmpty())
        val playEventFks = foreignKeysOf(db, "play_events")
        assertTrue("history must not cascade from tracks", playEventFks.isEmpty())

        // --- Phase 3 query plans still index-backed. ---
        val plan = db.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT track_id FROM tracks WHERE is_missing = 0 " +
                "ORDER BY title_normalized ASC, track_id ASC LIMIT 50"
        ).use { c ->
            buildString { while (c.moveToNext()) append(c.getString(c.columnCount - 1)).append('\n') }
        }
        assertTrue("composite index must serve the paged list: $plan",
            plan.contains("index_tracks_is_missing_title_normalized"))

        // --- No duplicate provider identities in the migrated table. ---
        val duplicateCount = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM (SELECT provider, external_id FROM tracks " +
                "WHERE external_id IS NOT NULL GROUP BY provider, external_id HAVING COUNT(*) > 1)"
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals(0, duplicateCount)

        // --- Locator round-trip: the migrated YouTube row reads back as a Remote locator. ---
        val domain = db.trackDao().getTrackById(10L)!!.toDomain(isFavorite = true)
        val locator = domain.locator
        assertTrue(locator is MediaLocator.Remote)
        assertEquals(ProviderId.YOUTUBE, (locator as MediaLocator.Remote).provider)
        assertEquals("dQw4w9WgXcQ", locator.externalId)
        assertTrue(domain.isFavorite)
        // v7 had no artwork column anywhere — thumbnails existed only in transient Track objects —
        // so a migrated provider row legitimately has artwork_url = NULL. It is repopulated by the
        // next provider upsert (ensurePersisted refreshes metadata), which the invariant test covers.
        assertNull(domain.artwork)

        db.close()
    }

    private fun foreignKeysOf(db: KaonDatabase, table: String): List<String> =
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndex("table"))) }
        }

    /**
     * Builds the v7 schema exactly as v7 shipped: columns of v6, Phase 3's composite index set, and
     * CASCADE foreign keys on the two user-owned tables that v8 removes.
     */
    private fun writeV7DatabaseWithUserData() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val v7 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)

        v7.execSQL(
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
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_title_normalized` ON `tracks` (`is_missing`, `title_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_date_added` ON `tracks` (`is_missing`, `date_added`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_album_normalized` ON `tracks` (`is_missing`, `album_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_artist_normalized` ON `tracks` (`is_missing`, `artist_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_album_id_disc_number_track_number` ON `tracks` (`album_id`, `disc_number`, `track_number`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_artist_id` ON `tracks` (`artist_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_youtube_video_id` ON `tracks` (`youtube_video_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_source` ON `tracks` (`source`)"
        ).forEach(v7::execSQL)

        v7.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite_tracks` (`track_id` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`track_id`), FOREIGN KEY(`track_id`) REFERENCES `tracks`(`track_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        v7.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_tracks_track_id` ON `favorite_tracks` (`track_id`)")
        v7.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_tracks_added_at` ON `favorite_tracks` (`added_at`)")

        v7.execSQL(
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
        ).forEach(v7::execSQL)

        v7.execSQL(
            "CREATE TABLE IF NOT EXISTS `queue_snapshot` (`id` INTEGER NOT NULL, `serialized_track_ids` TEXT NOT NULL, " +
                "`current_index` INTEGER NOT NULL, `current_position_ms` INTEGER NOT NULL, " +
                "`is_shuffle_enabled` INTEGER NOT NULL, `repeat_mode` INTEGER NOT NULL, `saved_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        v7.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`playlist_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)"
        )
        v7.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_name` ON `playlists` (`name`)")
        v7.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlist_id` INTEGER NOT NULL, `track_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `track_id`), " +
                "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`playlist_id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        v7.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlist_id_position` " +
                "ON `playlist_tracks` (`playlist_id`, `position`)"
        )
        v7.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_track_id` ON `playlist_tracks` (`track_id`)")

        // Local present (1), local missing (2), local present (3,4).
        fun insertLocal(id: Long, title: String, missing: Boolean) {
            v7.execSQL(
                "INSERT INTO tracks (track_id, media_store_id, title, artist, artist_id, album, album_id, " +
                    "track_number, disc_number, year, duration_ms, size_bytes, date_modified, date_added, " +
                    "relative_path, title_normalized, artist_normalized, album_normalized, is_missing, source, " +
                    "last_seen_timestamp) VALUES ($id, ${1000 + id}, '$title', 'Artist', 1, 'Album', 1, " +
                    "$id, 1, 2020, 200000, 4000000, 1, $id, 'Music/t$id.mp3', '${title.lowercase()}', 'artist', " +
                    "'album', ${if (missing) 1 else 0}, 'LOCAL', 1)"
            )
        }
        insertLocal(1, "Alpha", false)
        insertLocal(2, "Vanished", true)
        insertLocal(3, "Charlimits", false)
        insertLocal(4, "Delta", false)

        // YouTube rows: 10 and 11 with distinct videos, 12 duplicating 11's video id.
        fun insertYoutube(id: Long, videoId: String, title: String) {
            v7.execSQL(
                "INSERT INTO tracks (track_id, media_store_id, title, artist, artist_id, album, album_id, " +
                    "track_number, disc_number, year, duration_ms, size_bytes, date_modified, date_added, " +
                    "relative_path, title_normalized, artist_normalized, album_normalized, is_missing, source, " +
                    "youtube_video_id, last_seen_timestamp) VALUES ($id, 0, '$title', 'YT Artist', 0, 'Single', 0, " +
                    "0, 1, 0, 210000, 0, 1, $id, '', '${title.lowercase()}', 'yt artist', 'single', 0, 'YOUTUBE', " +
                    "'$videoId', 1)"
            )
        }
        insertYoutube(10, "dQw4w9WgXcQ", "Stream One")
        insertYoutube(11, "jNQXAC9IVRw", "Stream Two")
        insertYoutube(12, "jNQXAC9IVRw", "Stream Two Dup")

        // Favorites: local 1, missing 2 (must survive), YouTube 10, and 12 — which merges into 11.
        listOf(1L, 2L, 10L, 12L).forEachIndexed { i, id ->
            v7.execSQL("INSERT INTO favorite_tracks (track_id, added_at) VALUES ($id, ${6000 + i})")
        }

        // History: events on 10, and three on the duplicate 12 (must move to 11).
        for (e in 1..2) {
            v7.execSQL("INSERT INTO play_events (track_id, event_type, played_at, played_ms) VALUES (10, 'PLAY', ${10_000 + e}, 150000)")
        }
        for (e in 1..3) {
            v7.execSQL("INSERT INTO play_events (track_id, event_type, played_at, played_ms) VALUES (12, 'PLAY', ${20_000 + e}, 140000)")
        }

        v7.execSQL("INSERT INTO playlists (playlist_id, name, created_at, updated_at) VALUES (1, 'Mix', 1, 1)")
        v7.execSQL("INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at) VALUES (1, 1, 0, 1)")
        v7.execSQL("INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at) VALUES (1, 10, 1, 1)")
        v7.execSQL("INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at) VALUES (1, 12, 2, 1)")

        v7.version = 7
        v7.close()
    }
}

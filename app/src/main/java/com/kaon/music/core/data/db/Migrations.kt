package com.kaon.music.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8: unified track identity.
 *
 * This is the one data-carrying migration in Kaon's history, so it is written by hand rather than
 * auto-generated. Room's auto-migration cannot express it: `source`/`youtube_video_id` are *replaced*
 * by `provider`/`external_id`, `media_store_id` and `relative_path` become nullable, and two
 * user-owned tables lose a destructive foreign key. SQLite cannot drop a foreign key or relax a NOT
 * NULL in place, so each affected table is rebuilt with `INSERT INTO … SELECT` and every row is
 * carried across (spec §9: no destructive migration, no silent data loss).
 *
 * Identity translation:
 * - `source = 'YOUTUBE'` with a `youtube_video_id` → `provider = 'YOUTUBE'`, `external_id = <video id>`.
 * - everything else → `provider = 'LOCAL'`, `external_id = NULL`, which SQLite treats as distinct in a
 *   unique index, so unlimited local rows coexist with `UNIQUE(provider, external_id)`.
 * - a row claiming `source = 'YOUTUBE'` with **no** video id has no recoverable external identity. It
 *   stays `LOCAL` with `external_id = NULL` rather than being deleted or given an invented id: the
 *   user's favorites and playlist membership pointing at it survive, and the row is still playable if
 *   its MediaStore id resolves. `hashCode()`-derived ids are never reversed (spec §10).
 *
 * `media_store_id` is nulled for provider rows: a streamed track has no MediaStore entry, and the old
 * schema stored `0` there. Leaving the sentinel would make `getTrackByMediaStoreId(0)` match a stream.
 *
 * De-duplication before the unique index exists: if two v7 rows carry the same YouTube video id (which
 * the old schema had no constraint against), the lowest `track_id` wins and the losers' user data is
 * re-pointed at it, so favorites/history/membership survive a merge instead of failing the index.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        mergeDuplicateProviderIdentities(db)
        rebuildTracks(db)
        rebuildFavorites(db)
        rebuildPlayEvents(db)
    }

    /**
     * Collapses rows that would collide under `UNIQUE(provider, external_id)`.
     *
     * v7 had no constraint on `youtube_video_id`, so duplicates are possible in principle. User data is
     * re-pointed at the surviving id first, then the duplicate rows are deleted — in that order, so no
     * favorite or playlist entry is ever orphaned mid-migration.
     */
    private fun mergeDuplicateProviderIdentities(db: SupportSQLiteDatabase) {
        val duplicateGroups = mutableListOf<Pair<Long, List<Long>>>()
        db.query(
            """
            SELECT youtube_video_id, MIN(track_id) AS keep_id, GROUP_CONCAT(track_id) AS all_ids
            FROM tracks
            WHERE source = 'YOUTUBE' AND youtube_video_id IS NOT NULL AND youtube_video_id != ''
            GROUP BY youtube_video_id
            HAVING COUNT(*) > 1
            """
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val keepId = cursor.getLong(1)
                val losers = cursor.getString(2)
                    .split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                    .filter { it != keepId }
                if (losers.isNotEmpty()) duplicateGroups += keepId to losers
            }
        }

        for ((keepId, losers) in duplicateGroups) {
            val loserList = losers.joinToString(",")
            // Favorites and playlist membership are unique per (…, track_id), so an INSERT-style
            // re-point could collide with an existing row for the survivor. Delete the colliding
            // duplicates first, then move what remains.
            db.execSQL(
                "DELETE FROM favorite_tracks WHERE track_id IN ($loserList) " +
                    "AND EXISTS(SELECT 1 FROM favorite_tracks k WHERE k.track_id = $keepId)"
            )
            db.execSQL("UPDATE favorite_tracks SET track_id = $keepId WHERE track_id IN ($loserList)")

            db.execSQL(
                "DELETE FROM playlist_tracks WHERE track_id IN ($loserList) AND EXISTS(" +
                    "SELECT 1 FROM playlist_tracks k WHERE k.track_id = $keepId " +
                    "AND k.playlist_id = playlist_tracks.playlist_id)"
            )
            db.execSQL("UPDATE playlist_tracks SET track_id = $keepId WHERE track_id IN ($loserList)")

            // History is append-only with no uniqueness, so every event moves.
            db.execSQL("UPDATE play_events SET track_id = $keepId WHERE track_id IN ($loserList)")

            db.execSQL("DELETE FROM tracks WHERE track_id IN ($loserList)")
        }
    }

    private fun rebuildTracks(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tracks_new` (
                `track_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `media_store_id` INTEGER,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artist_id` INTEGER NOT NULL DEFAULT 0,
                `album` TEXT NOT NULL,
                `album_id` INTEGER NOT NULL,
                `track_number` INTEGER NOT NULL DEFAULT 0,
                `disc_number` INTEGER NOT NULL DEFAULT 1,
                `year` INTEGER NOT NULL DEFAULT 0,
                `duration_ms` INTEGER NOT NULL,
                `size_bytes` INTEGER NOT NULL,
                `date_modified` INTEGER NOT NULL,
                `date_added` INTEGER NOT NULL DEFAULT 0,
                `relative_path` TEXT,
                `title_normalized` TEXT NOT NULL,
                `artist_normalized` TEXT NOT NULL,
                `album_normalized` TEXT NOT NULL,
                `is_missing` INTEGER NOT NULL DEFAULT 0,
                `provider` TEXT NOT NULL DEFAULT 'LOCAL',
                `external_id` TEXT,
                `mime_type` TEXT,
                `artwork_url` TEXT,
                `last_seen_timestamp` INTEGER NOT NULL
            )
            """
        )

        // track_id is carried verbatim: every favorite, play event and playlist entry references it,
        // and spec §30.1/§30.13 require the identity to survive.
        db.execSQL(
            """
            INSERT INTO `tracks_new` (
                track_id, media_store_id, title, artist, artist_id, album, album_id, track_number,
                disc_number, year, duration_ms, size_bytes, date_modified, date_added, relative_path,
                title_normalized, artist_normalized, album_normalized, is_missing, provider,
                external_id, mime_type, artwork_url, last_seen_timestamp
            )
            SELECT
                track_id,
                CASE WHEN source = 'YOUTUBE' AND youtube_video_id IS NOT NULL AND youtube_video_id != ''
                     THEN NULL ELSE media_store_id END,
                title, artist, artist_id, album, album_id, track_number,
                disc_number, year, duration_ms, size_bytes, date_modified, date_added,
                CASE WHEN source = 'YOUTUBE' AND youtube_video_id IS NOT NULL AND youtube_video_id != ''
                     THEN NULL ELSE relative_path END,
                title_normalized, artist_normalized, album_normalized, is_missing,
                CASE WHEN source = 'YOUTUBE' AND youtube_video_id IS NOT NULL AND youtube_video_id != ''
                     THEN 'YOUTUBE' ELSE 'LOCAL' END,
                CASE WHEN source = 'YOUTUBE' AND youtube_video_id IS NOT NULL AND youtube_video_id != ''
                     THEN youtube_video_id ELSE NULL END,
                mime_type,
                NULL,
                last_seen_timestamp
            FROM `tracks`
            """
        )

        db.execSQL("DROP TABLE `tracks`")
        db.execSQL("ALTER TABLE `tracks_new` RENAME TO `tracks`")

        // Phase 3's composite indexes, recreated verbatim so its EXPLAIN QUERY PLAN assertions and
        // measured page costs still hold, plus the new external-identity constraint.
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_tracks_media_store_id` ON `tracks` (`media_store_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_duration_ms_size_bytes` ON `tracks` (`duration_ms`, `size_bytes`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_title_normalized` ON `tracks` (`is_missing`, `title_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_date_added` ON `tracks` (`is_missing`, `date_added`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_album_normalized` ON `tracks` (`is_missing`, `album_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_is_missing_artist_normalized` ON `tracks` (`is_missing`, `artist_normalized`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_album_id_disc_number_track_number` ON `tracks` (`album_id`, `disc_number`, `track_number`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_artist_id` ON `tracks` (`artist_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_provider_external_id` ON `tracks` (`provider`, `external_id`)"
        ).forEach(db::execSQL)
    }

    /** Rebuilt only to drop `ON DELETE CASCADE`; every column and row is preserved. */
    private fun rebuildFavorites(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite_tracks_new` (
                `track_id` INTEGER NOT NULL,
                `added_at` INTEGER NOT NULL,
                PRIMARY KEY(`track_id`)
            )
            """
        )
        db.execSQL(
            "INSERT INTO `favorite_tracks_new` (track_id, added_at) SELECT track_id, added_at FROM `favorite_tracks`"
        )
        db.execSQL("DROP TABLE `favorite_tracks`")
        db.execSQL("ALTER TABLE `favorite_tracks_new` RENAME TO `favorite_tracks`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_tracks_track_id` ON `favorite_tracks` (`track_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_tracks_added_at` ON `favorite_tracks` (`added_at`)")
    }

    /** Rebuilt only to drop `ON DELETE CASCADE`; every column and row is preserved. */
    private fun rebuildPlayEvents(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `play_events_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `track_id` INTEGER NOT NULL,
                `event_type` TEXT NOT NULL,
                `played_at` INTEGER NOT NULL,
                `played_ms` INTEGER NOT NULL
            )
            """
        )
        db.execSQL(
            "INSERT INTO `play_events_new` (id, track_id, event_type, played_at, played_ms) " +
                "SELECT id, track_id, event_type, played_at, played_ms FROM `play_events`"
        )
        db.execSQL("DROP TABLE `play_events`")
        db.execSQL("ALTER TABLE `play_events_new` RENAME TO `play_events`")
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_play_events_track_id` ON `play_events` (`track_id`)",
            "CREATE INDEX IF NOT EXISTS `index_play_events_played_at` ON `play_events` (`played_at`)",
            "CREATE INDEX IF NOT EXISTS `index_play_events_event_type` ON `play_events` (`event_type`)",
            "CREATE INDEX IF NOT EXISTS `index_play_events_event_type_track_id_played_at` " +
                "ON `play_events` (`event_type`, `track_id`, `played_at`)"
        ).forEach(db::execSQL)
    }
}

package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per track, whatever provider it came from.
 *
 * Identity (ARCHITECTURE.md §3.1):
 * - [trackId] is the stable Kaon-owned primary key that every piece of user data references. It is
 *   never derived from a hash, a title or a provider id, and it survives metadata edits, MediaStore
 *   re-inserts and re-links.
 * - [provider] together with [externalId] is the *external* identity, unique as a pair:
 *   `UNIQUE(provider, external_id)` turns "store this YouTube video at most once" into a database
 *   guarantee instead of a convention. Device files leave [externalId] `NULL`, and SQLite treats NULLs
 *   in a unique index as distinct, so unlimited local rows coexist with that constraint.
 * - [mediaStoreId] remains the sync match key, but only local files have one — hence nullable. A
 *   streamed row has no MediaStore entry and saying so with `NULL` is more honest than a sentinel.
 *
 * Where the audio comes from is read back as a `MediaLocator`: `provider = 'LOCAL'` is a device file,
 * a non-local provider without a [relativePath] is streamed, and a non-local provider *with* one is a
 * download. That is why a future download needs neither a new table nor a new column.
 *
 * [artworkUrl] exists so a provider thumbnail has a home that is not the playback location; the two
 * were previously the same nullable URI field.
 *
 * Indexes are composite on `is_missing` first (ARCHITECTURE.md §6: every column in a `WHERE`/`ORDER BY`
 * is indexed, or its absence is justified). Every library-facing query filters `is_missing = 0` and
 * then orders, so a single-column index on the sort column could not serve both halves — SQLite would
 * filter by index and re-sort the survivors, which is the whole cost at 50k rows. Those queries now
 * also filter `provider = 'LOCAL'`, which is deliberately *not* added to the index keys: it is a cheap
 * residual test that leaves the Phase 3 query plans (and their `EXPLAIN QUERY PLAN` assertions)
 * unchanged, whereas re-leading every composite index would rewrite them all for no measured gain.
 */
@Entity(
    tableName = "tracks",
    indices = [
        // Sync match key.
        Index(value = ["media_store_id"], unique = false),
        // findReLinkCandidates: is_missing = 1 AND duration BETWEEN … AND size_bytes = …
        Index(value = ["duration_ms", "size_bytes"]),
        // pagingTracksByTitle / countActiveTracks / searchTracks ordering.
        Index(value = ["is_missing", "title_normalized"]),
        // pagingTracksByDateAdded, observeRecentlyAddedTracks.
        Index(value = ["is_missing", "date_added"]),
        // pagingAlbums, observeAllAlbums grouping and ordering.
        Index(value = ["is_missing", "album_normalized"]),
        // pagingArtists, observeAllArtists, observeTracksForArtist (equality on both columns).
        Index(value = ["is_missing", "artist_normalized"]),
        // observeTracksForAlbum: album_id equality then disc/track ordering.
        Index(value = ["album_id", "disc_number", "track_number"]),
        Index(value = ["artist_id"]),
        // External identity: enforces one row per provider media and serves the upsert lookup.
        Index(value = ["provider", "external_id"], unique = true)
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "track_id")
    val trackId: Long = 0,

    /** MediaStore id, `null` for provider media. Never an identity. */
    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long? = null,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "artist_id", defaultValue = "0")
    val artistId: Long = 0,

    @ColumnInfo(name = "album")
    val album: String,

    @ColumnInfo(name = "album_id")
    val albumId: Long,

    @ColumnInfo(name = "track_number", defaultValue = "0")
    val trackNumber: Int = 0,

    @ColumnInfo(name = "disc_number", defaultValue = "1")
    val discNumber: Int = 1,

    @ColumnInfo(name = "year", defaultValue = "0")
    val year: Int = 0,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "date_modified")
    val dateModified: Long,

    @ColumnInfo(name = "date_added", defaultValue = "0")
    val dateAdded: Long = 0L,

    /** Relative path for a local file, file name for a download, `null` for a stream. */
    @ColumnInfo(name = "relative_path")
    val relativePath: String? = null,

    @ColumnInfo(name = "title_normalized")
    val titleNormalized: String,

    @ColumnInfo(name = "artist_normalized")
    val artistNormalized: String,

    @ColumnInfo(name = "album_normalized")
    val albumNormalized: String,

    /**
     * The local file backing this row is not present right now.
     *
     * Only meaningful for local files; it keeps favorites, history and playlist membership intact while
     * the file is gone instead of deleting the row (ARCHITECTURE.md §3.1).
     */
    @ColumnInfo(name = "is_missing", defaultValue = "0")
    val isMissing: Boolean = false,

    /** Catalogue this row belongs to: `LOCAL`, `YOUTUBE`, … */
    @ColumnInfo(name = "provider", defaultValue = "LOCAL")
    val provider: String = "LOCAL",

    /** Provider-scoped id, `null` for device files. Unique only together with [provider]. */
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    /** Provider-supplied artwork URL. Never a playback location. */
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,

    @ColumnInfo(name = "last_seen_timestamp")
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

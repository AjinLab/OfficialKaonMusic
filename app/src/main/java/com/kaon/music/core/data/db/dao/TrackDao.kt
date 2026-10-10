package com.kaon.music.core.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Track access.
 *
 * `tracks` now holds provider media as well as device files, so the queries divide by intent rather
 * than by table (ARCHITECTURE.md §3.1):
 * - **Device library** (browse, search, counts, album/artist aggregates, sync, re-link, purge) filters
 *   `provider = 'LOCAL'`. A YouTube row is not part of the on-device library and must not appear in it,
 *   be diffed against MediaStore, or be marked missing when a file disappears.
 * - **User-owned relationships** (favorites, playlist membership, history) deliberately carry no
 *   provider filter: that is what makes a streamed track a first-class citizen.
 * - **Identity lookups** ([getTrackById], [getTrackByExternalIdentity]) are provider-agnostic.
 */
@Dao
interface TrackDao {

    /**
     * Unbounded read of the device library.
     *
     * Sync-only (ARCHITECTURE.md §6: no DAO query returns an unbounded row set to the UI). The UI
     * reaches tracks through the `paging*` queries below; this exists for [com.kaon.music.core.data.sync.SyncEngine],
     * which genuinely has to diff every row against MediaStore.
     */
    @Query("SELECT * FROM tracks WHERE is_missing = 0 AND provider = 'LOCAL' ORDER BY title_normalized ASC")
    fun observeAllActiveTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_missing = 0 AND provider = 'LOCAL' ORDER BY title_normalized ASC")
    suspend fun getAllActiveTracks(): List<TrackEntity>

    /** Every stored device row, present or missing. Sync's diff input, so provider media is excluded. */
    @Query("SELECT * FROM tracks WHERE provider = 'LOCAL'")
    suspend fun getAllStoredTracks(): List<TrackEntity>

    // ==================== Paged library access ====================

    /**
     * Paged tracks with SQL-resolved favorite state and SQL ordering.
     *
     * One query per sort order rather than a runtime `ORDER BY :column`: SQLite cannot bind an
     * identifier, and a `CASE` over sort columns defeats the composite index the ordering relies on.
     *
     * `query` empty means no filter. The `LIKE` is over the indexed normalized columns; the leading
     * wildcard means it is a scan of the filtered set rather than an index seek, which is acceptable
     * because the result is paged — cost scales with what the UI shows, not the library size.
     */
    @Query(
        """
        SELECT t.track_id, t.media_store_id, t.title, t.artist, t.artist_id, t.album, t.album_id,
               t.track_number, t.disc_number, t.year, t.duration_ms, t.size_bytes, t.date_modified,
               t.date_added, t.relative_path, t.is_missing, t.provider, t.external_id, t.mime_type,
               t.artwork_url,
               EXISTS(SELECT 1 FROM favorite_tracks f WHERE f.track_id = t.track_id) AS is_favorite
        FROM tracks t
        WHERE t.is_missing = 0 AND t.provider = 'LOCAL'
          AND (:query = '' OR t.title_normalized LIKE '%' || :query || '%'
                           OR t.artist_normalized LIKE '%' || :query || '%'
                           OR t.album_normalized LIKE '%' || :query || '%')
        ORDER BY t.title_normalized ASC, t.track_id ASC
        """
    )
    fun pagingTracksByTitle(query: String): PagingSource<Int, TrackRow>

    @Query(
        """
        SELECT t.track_id, t.media_store_id, t.title, t.artist, t.artist_id, t.album, t.album_id,
               t.track_number, t.disc_number, t.year, t.duration_ms, t.size_bytes, t.date_modified,
               t.date_added, t.relative_path, t.is_missing, t.provider, t.external_id, t.mime_type,
               t.artwork_url,
               EXISTS(SELECT 1 FROM favorite_tracks f WHERE f.track_id = t.track_id) AS is_favorite
        FROM tracks t
        WHERE t.is_missing = 0 AND t.provider = 'LOCAL'
          AND (:query = '' OR t.title_normalized LIKE '%' || :query || '%'
                           OR t.artist_normalized LIKE '%' || :query || '%'
                           OR t.album_normalized LIKE '%' || :query || '%')
        ORDER BY t.date_added DESC, t.track_id ASC
        """
    )
    fun pagingTracksByDateAdded(query: String): PagingSource<Int, TrackRow>

    /**
     * Paged tracks ordered by play count.
     *
     * The `LEFT JOIN` keeps never-played tracks in the result (ordered last), so switching to this
     * sort does not silently shrink the library. Previously this ordering was built in Kotlin by
     * materialising the whole library plus a most-played list and partitioning them.
     */
    @Query(
        """
        SELECT t.track_id, t.media_store_id, t.title, t.artist, t.artist_id, t.album, t.album_id,
               t.track_number, t.disc_number, t.year, t.duration_ms, t.size_bytes, t.date_modified,
               t.date_added, t.relative_path, t.is_missing, t.provider, t.external_id, t.mime_type,
               t.artwork_url,
               EXISTS(SELECT 1 FROM favorite_tracks f WHERE f.track_id = t.track_id) AS is_favorite
        FROM tracks t
        LEFT JOIN (
            SELECT track_id, COUNT(*) AS play_count
            FROM play_events WHERE event_type = 'PLAY' GROUP BY track_id
        ) p ON p.track_id = t.track_id
        WHERE t.is_missing = 0 AND t.provider = 'LOCAL'
          AND (:query = '' OR t.title_normalized LIKE '%' || :query || '%'
                           OR t.artist_normalized LIKE '%' || :query || '%'
                           OR t.album_normalized LIKE '%' || :query || '%')
        ORDER BY COALESCE(p.play_count, 0) DESC, t.title_normalized ASC, t.track_id ASC
        """
    )
    fun pagingTracksByPlayCount(query: String): PagingSource<Int, TrackRow>

    /**
     * Paged favorites, newest-favorited first. Membership and ordering are both SQL-side.
     *
     * No provider filter: a favorited YouTube track is a favorite like any other (spec §6, §11).
     */
    @Query(
        """
        SELECT t.track_id, t.media_store_id, t.title, t.artist, t.artist_id, t.album, t.album_id,
               t.track_number, t.disc_number, t.year, t.duration_ms, t.size_bytes, t.date_modified,
               t.date_added, t.relative_path, t.is_missing, t.provider, t.external_id, t.mime_type,
               t.artwork_url,
               1 AS is_favorite
        FROM tracks t
        INNER JOIN favorite_tracks f ON f.track_id = t.track_id
        WHERE t.is_missing = 0
          AND (:query = '' OR t.title_normalized LIKE '%' || :query || '%'
                           OR t.artist_normalized LIKE '%' || :query || '%'
                           OR t.album_normalized LIKE '%' || :query || '%')
        ORDER BY f.added_at DESC, t.track_id ASC
        """
    )
    fun pagingFavoriteTracks(query: String): PagingSource<Int, TrackRow>

    /**
     * Counts for the paged lists.
     *
     * A count is what the screens actually needed from the full library (header totals, "play all"
     * enablement); it does not require materialising a single row.
     */
    @Query(
        """
        SELECT COUNT(*) FROM tracks
        WHERE is_missing = 0 AND provider = 'LOCAL'
          AND (:query = '' OR title_normalized LIKE '%' || :query || '%'
                           OR artist_normalized LIKE '%' || :query || '%'
                           OR album_normalized LIKE '%' || :query || '%')
        """
    )
    fun observeActiveTrackCount(query: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM tracks t
        INNER JOIN favorite_tracks f ON f.track_id = t.track_id
        WHERE t.is_missing = 0
          AND (:query = '' OR t.title_normalized LIKE '%' || :query || '%'
                           OR t.artist_normalized LIKE '%' || :query || '%'
                           OR t.album_normalized LIKE '%' || :query || '%')
        """
    )
    fun observeFavoriteTrackCount(query: String): Flow<Int>

    /**
     * Bounded id list for building a playback queue from the current list context.
     *
     * Tapping a track must enqueue the list the user sees, but that list is paged, so the queue is
     * built from a `LIMIT`-ed id projection matching the same order rather than from whatever pages
     * happen to be loaded. Ids only: [com.kaon.music.core.data.repository.TrackRepository.getTracksByIds]
     * resolves them, and a queue never needs the full row set in memory twice.
     */
    @Query(
        """
        SELECT track_id FROM tracks
        WHERE is_missing = 0 AND provider = 'LOCAL'
          AND (:query = '' OR title_normalized LIKE '%' || :query || '%'
                           OR artist_normalized LIKE '%' || :query || '%'
                           OR album_normalized LIKE '%' || :query || '%')
        ORDER BY title_normalized ASC, track_id ASC
        LIMIT :limit
        """
    )
    suspend fun getActiveTrackIdsByTitle(query: String, limit: Int): List<Long>

    @Query(
        """
        SELECT track_id FROM tracks
        WHERE is_missing = 0 AND provider = 'LOCAL'
          AND (:query = '' OR title_normalized LIKE '%' || :query || '%'
                           OR artist_normalized LIKE '%' || :query || '%'
                           OR album_normalized LIKE '%' || :query || '%')
        ORDER BY date_added DESC, track_id ASC
        LIMIT :limit
        """
    )
    suspend fun getActiveTrackIdsByDateAdded(query: String, limit: Int): List<Long>

    @Query(
        """
        SELECT t.track_id FROM tracks t
        LEFT JOIN (
            SELECT track_id, COUNT(*) AS play_count
            FROM play_events WHERE event_type = 'PLAY' GROUP BY track_id
        ) p ON p.track_id = t.track_id
        WHERE t.is_missing = 0 AND t.provider = 'LOCAL'
          AND (:query = '' OR t.title_normalized LIKE '%' || :query || '%'
                           OR t.artist_normalized LIKE '%' || :query || '%'
                           OR t.album_normalized LIKE '%' || :query || '%')
        ORDER BY COALESCE(p.play_count, 0) DESC, t.title_normalized ASC, t.track_id ASC
        LIMIT :limit
        """
    )
    suspend fun getActiveTrackIdsByPlayCount(query: String, limit: Int): List<Long>

    @Query(
        """
        SELECT t.track_id FROM tracks t
        INNER JOIN favorite_tracks f ON f.track_id = t.track_id
        WHERE t.is_missing = 0
          AND (:query = '' OR t.title_normalized LIKE '%' || :query || '%'
                           OR t.artist_normalized LIKE '%' || :query || '%'
                           OR t.album_normalized LIKE '%' || :query || '%')
        ORDER BY f.added_at DESC, t.track_id ASC
        LIMIT :limit
        """
    )
    suspend fun getFavoriteTrackIds(query: String, limit: Int): List<Long>

    // ==================== Identity ====================

    @Query("SELECT * FROM tracks WHERE track_id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: Long): TrackEntity?

    /** Provider-agnostic: a queue or playlist may reference a local row and a streamed row alike. */
    @Query("SELECT * FROM tracks WHERE track_id IN (:trackIds)")
    suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity>

    /**
     * The external-identity lookup behind every upsert.
     *
     * Served by the unique `(provider, external_id)` index, which is also what makes the same YouTube
     * video unable to produce a second row (spec §5, §7).
     */
    @Query("SELECT * FROM tracks WHERE provider = :provider AND external_id = :externalId LIMIT 1")
    suspend fun getTrackByExternalIdentity(provider: String, externalId: String): TrackEntity?

    /** Local sync match key; provider rows have no MediaStore id, hence the explicit filter. */
    @Query("SELECT * FROM tracks WHERE media_store_id = :mediaStoreId AND provider = 'LOCAL' LIMIT 1")
    suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity?

    /**
     * Bounded LIKE search over the device library.
     *
     * Retained for the non-paged callers (search screens that show a capped section). The paged
     * variants above take the same `query` parameter, so there is one search predicate shape in the
     * DAO, not two that can drift.
     */
    @Query("""
        SELECT * FROM tracks 
        WHERE is_missing = 0 AND provider = 'LOCAL'
        AND (
            title_normalized LIKE '%' || :query || '%' 
            OR artist_normalized LIKE '%' || :query || '%' 
            OR album_normalized LIKE '%' || :query || '%'
        )
        ORDER BY title_normalized ASC 
        LIMIT :limit
    """)
    fun searchTracks(query: String, limit: Int = 100): Flow<List<TrackEntity>>

    /**
     * Recently added device tracks.
     */
    @Query("SELECT * FROM tracks WHERE is_missing = 0 AND provider = 'LOCAL' ORDER BY date_added DESC LIMIT :limit")
    fun observeRecentlyAddedTracks(limit: Int = 100): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_missing = 0 AND provider = 'LOCAL' ORDER BY date_added DESC LIMIT :limit")
    suspend fun getRecentlyAddedTracks(limit: Int = 100): List<TrackEntity>

    // ==================== M3-D1 Derived Albums ====================

    /**
     * Paged albums with SQL-side name filtering.
     *
     * Same aggregate projection as [observeAllAlbums]; the filter is applied before grouping so a
     * search narrows the scan rather than grouping the whole library and filtering the groups.
     */
    @Query(
        """
        SELECT 
            album_id,
            album,
            artist,
            artist_id,
            MAX(year) as year,
            COUNT(track_id) as track_count,
            SUM(duration_ms) as total_duration_ms
        FROM tracks 
        WHERE is_missing = 0 
          AND (:query = '' OR album_normalized LIKE '%' || :query || '%'
                           OR artist_normalized LIKE '%' || :query || '%')
        GROUP BY album_id 
        ORDER BY album_normalized ASC
        """
    )
    fun pagingAlbums(query: String): PagingSource<Int, AlbumSummary>

    /** Bounded album search for the search screen's capped local section. */
    @Query(
        """
        SELECT 
            album_id,
            album,
            artist,
            artist_id,
            MAX(year) as year,
            COUNT(track_id) as track_count,
            SUM(duration_ms) as total_duration_ms
        FROM tracks 
        WHERE is_missing = 0 
          AND (album_normalized LIKE '%' || :query || '%'
               OR artist_normalized LIKE '%' || :query || '%')
        GROUP BY album_id 
        ORDER BY album_normalized ASC
        LIMIT :limit
        """
    )
    fun searchAlbums(query: String, limit: Int): Flow<List<AlbumSummary>>

    /** Bounded album list for the Home feed's small "recent albums" row. */
    @Query(
        """
        SELECT 
            album_id,
            album,
            artist,
            artist_id,
            MAX(year) as year,
            COUNT(track_id) as track_count,
            SUM(duration_ms) as total_duration_ms
        FROM tracks 
        WHERE is_missing = 0 
        GROUP BY album_id 
        ORDER BY MAX(date_added) DESC
        LIMIT :limit
        """
    )
    fun observeRecentAlbums(limit: Int): Flow<List<AlbumSummary>>

    /**
     * Unbounded album aggregate.
     *
     * Retained for callers that need the complete set; the browse UI uses [pagingAlbums]. Album count
     * is bounded by distinct `album_id`, not by track count, so this is orders of magnitude smaller
     * than the track table — but it is still not what a list should observe at 50k tracks.
     */
    @Query("""
        SELECT 
            album_id,
            album,
            artist,
            artist_id,
            MAX(year) as year,
            COUNT(track_id) as track_count,
            SUM(duration_ms) as total_duration_ms
        FROM tracks 
        WHERE is_missing = 0 
        GROUP BY album_id 
        ORDER BY album_normalized ASC
    """)
    fun observeAllAlbums(): Flow<List<AlbumSummary>>

    @Query("""
        SELECT 
            album_id,
            album,
            artist,
            artist_id,
            MAX(year) as year,
            COUNT(track_id) as track_count,
            SUM(duration_ms) as total_duration_ms
        FROM tracks 
        WHERE is_missing = 0 AND album_id = :albumId
        GROUP BY album_id 
        LIMIT 1
    """)
    suspend fun getAlbumById(albumId: Long): AlbumSummary?

    @Query("""
        SELECT * FROM tracks 
        WHERE album_id = :albumId AND is_missing = 0 
        ORDER BY disc_number ASC, track_number ASC, title_normalized ASC
    """)
    fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks 
        WHERE album_id = :albumId AND is_missing = 0 
        ORDER BY disc_number ASC, track_number ASC, title_normalized ASC
    """)
    suspend fun getTracksForAlbum(albumId: Long): List<TrackEntity>

    // ==================== M3-D1 Derived Artists ====================

    /** Paged artists with SQL-side name filtering. */
    @Query(
        """
        SELECT 
            artist_id,
            artist,
            COUNT(DISTINCT album_id) as album_count,
            COUNT(track_id) as track_count
        FROM tracks 
        WHERE is_missing = 0 
          AND (:query = '' OR artist_normalized LIKE '%' || :query || '%')
        GROUP BY artist_normalized 
        ORDER BY artist_normalized ASC
        """
    )
    fun pagingArtists(query: String): PagingSource<Int, ArtistSummary>

    /** Bounded artist search for the search screen's capped local section. */
    @Query(
        """
        SELECT 
            artist_id,
            artist,
            COUNT(DISTINCT album_id) as album_count,
            COUNT(track_id) as track_count
        FROM tracks 
        WHERE is_missing = 0 
          AND artist_normalized LIKE '%' || :query || '%'
        GROUP BY artist_normalized 
        ORDER BY artist_normalized ASC
        LIMIT :limit
        """
    )
    fun searchArtists(query: String, limit: Int): Flow<List<ArtistSummary>>

    /** Bounded artist list for the Home feed's small "recent artists" row. */
    @Query(
        """
        SELECT 
            artist_id,
            artist,
            COUNT(DISTINCT album_id) as album_count,
            COUNT(track_id) as track_count
        FROM tracks 
        WHERE is_missing = 0 
        GROUP BY artist_normalized 
        ORDER BY MAX(date_added) DESC
        LIMIT :limit
        """
    )
    fun observeRecentArtists(limit: Int): Flow<List<ArtistSummary>>

    @Query("""
        SELECT 
            artist_id,
            artist,
            COUNT(DISTINCT album_id) as album_count,
            COUNT(track_id) as track_count
        FROM tracks 
        WHERE is_missing = 0 
        GROUP BY artist_normalized 
        ORDER BY artist_normalized ASC
    """)
    fun observeAllArtists(): Flow<List<ArtistSummary>>

    @Query("""
        SELECT 
            album_id,
            album,
            artist,
            artist_id,
            MAX(year) as year,
            COUNT(track_id) as track_count,
            SUM(duration_ms) as total_duration_ms
        FROM tracks 
        WHERE is_missing = 0 AND artist_normalized = :artistNormalized
        GROUP BY album_id 
        ORDER BY year DESC, album_normalized ASC
    """)
    fun observeAlbumsForArtist(artistNormalized: String): Flow<List<AlbumSummary>>

    @Query("""
        SELECT * FROM tracks 
        WHERE artist_normalized = :artistNormalized AND is_missing = 0 
        ORDER BY year DESC, album_normalized ASC, disc_number ASC, track_number ASC, title_normalized ASC
    """)
    fun observeTracksForArtist(artistNormalized: String): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks 
        WHERE artist_normalized = :artistNormalized AND is_missing = 0 
        ORDER BY year DESC, album_normalized ASC, disc_number ASC, track_number ASC, title_normalized ASC
    """)
    suspend fun getTracksForArtist(artistNormalized: String): List<TrackEntity>

    // ==================== Re-linking & Mutation Queries ====================

    @Query("""
        SELECT * FROM tracks 
        WHERE is_missing = 1 
        AND duration_ms BETWEEN :minDurationMs AND :maxDurationMs 
        AND size_bytes = :sizeBytes
    """)
    suspend fun findReLinkCandidates(
        minDurationMs: Long,
        maxDurationMs: Long,
        sizeBytes: Long
    ): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Update
    suspend fun updateTracks(tracks: List<TrackEntity>)

    /**
     * Persists provider media at most once.
     *
     * Read-then-write inside a `@Transaction` rather than `INSERT OR REPLACE`: replace would delete and
     * re-insert the row, and with `AUTOINCREMENT` that assigns a **new** `track_id`, silently orphaning
     * every favorite, play event and playlist entry pointing at the old one. Returning the existing id
     * unchanged is what makes repeated resolution of the same YouTube video idempotent (spec §7).
     *
     * Metadata on an existing row is refreshed, because a provider can improve a title or thumbnail
     * between sessions and the row should not stay stale — but identity never moves.
     */
    @Transaction
    suspend fun upsertByExternalIdentity(track: TrackEntity): Long {
        val provider = track.provider
        val externalId = track.externalId
        require(!externalId.isNullOrBlank()) { "upsertByExternalIdentity requires an external id" }

        val existing = getTrackByExternalIdentity(provider, externalId)
            ?: return insertTrack(track.copy(trackId = 0L))

        updateTrack(
            existing.copy(
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumId = track.albumId,
                artistId = track.artistId,
                durationMs = if (track.durationMs > 0L) track.durationMs else existing.durationMs,
                titleNormalized = track.titleNormalized,
                artistNormalized = track.artistNormalized,
                albumNormalized = track.albumNormalized,
                mimeType = track.mimeType ?: existing.mimeType,
                artworkUrl = track.artworkUrl ?: existing.artworkUrl,
                lastSeenTimestamp = track.lastSeenTimestamp
            )
        )
        return existing.trackId
    }

    /**
     * Re-links a moved or re-inserted local file.
     *
     * Only the locator columns change: `track_id` is deliberately absent from the `SET` list, so the
     * user's favorites, history and playlist membership follow the file (spec §13, §30.13).
     */
    @Query(
        """
        UPDATE tracks
        SET media_store_id = :newMediaStoreId,
            relative_path = COALESCE(:newRelativePath, relative_path),
            is_missing = 0,
            last_seen_timestamp = :timestamp
        WHERE track_id = :trackId
        """
    )
    suspend fun reLinkTrack(
        trackId: Long,
        newMediaStoreId: Long,
        newRelativePath: String? = null,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tracks SET is_missing = 1 WHERE track_id IN (:trackIds)")
    suspend fun markTracksMissing(trackIds: List<Long>)

    @Query("UPDATE tracks SET is_missing = 0, last_seen_timestamp = :timestamp WHERE track_id IN (:trackIds)")
    suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long = System.currentTimeMillis())

    /**
     * Hard-deletes long-missing device rows.
     *
     * Restricted to `provider = 'LOCAL'`: a streamed row is never "missing" and must not be swept up by
     * a MediaStore-driven purge. User-owned tables no longer cascade from `tracks`, so a purge here
     * leaves favorites and history intact (ARCHITECTURE.md §6).
     */
    @Query(
        "DELETE FROM tracks WHERE provider = 'LOCAL' AND is_missing = 1 " +
            "AND last_seen_timestamp < :purgeCutoffTimestamp"
    )
    suspend fun purgeOrphanedTracks(purgeCutoffTimestamp: Long): Int

    data class AlbumSummary(
        val album_id: Long,
        val album: String,
        val artist: String,
        val artist_id: Long,
        val year: Int,
        val track_count: Int,
        val total_duration_ms: Long
    )

    data class ArtistSummary(
        val artist_id: Long,
        val artist: String,
        val album_count: Int,
        val track_count: Int
    )
}

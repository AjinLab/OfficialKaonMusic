package com.kaon.music.core.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    data class PlaylistSummary(
        val playlist_id: Long,
        val name: String,
        val created_at: Long,
        val updated_at: Long,
        val track_count: Int
    )

    /** Header facts for a playlist detail screen: no member rows required. */
    data class PlaylistStats(
        val track_count: Int,
        val total_duration_ms: Long
    )

    // ==================== Playlist CRUD ====================

    @Query("""
        SELECT 
            p.playlist_id, 
            p.name, 
            p.created_at, 
            p.updated_at,
            COUNT(CASE WHEN t.is_missing = 0 THEN 1 ELSE NULL END) as track_count
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.playlist_id = pt.playlist_id
        LEFT JOIN tracks t ON pt.track_id = t.track_id
        GROUP BY p.playlist_id
        ORDER BY p.name COLLATE NOCASE ASC
    """)
    fun observeAllPlaylistsWithCount(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE playlist_id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    /** Member count without loading members. Matches `observeAllPlaylistsWithCount`'s definition. */
    @Query(
        """
        SELECT COUNT(*) FROM playlist_tracks pt
        INNER JOIN tracks t ON t.track_id = pt.track_id
        WHERE pt.playlist_id = :playlistId AND t.is_missing = 0
        """
    )
    suspend fun countTracksInPlaylist(playlistId: Long): Int

    /**
     * Count and total duration for the detail header.
     *
     * The header used to derive both by summing the materialised member list; an aggregate gives the
     * same two numbers without the rows, which matters once the list itself is paged.
     */
    @Query(
        """
        SELECT COUNT(*) AS track_count, COALESCE(SUM(t.duration_ms), 0) AS total_duration_ms
        FROM playlist_tracks pt
        INNER JOIN tracks t ON t.track_id = pt.track_id
        WHERE pt.playlist_id = :playlistId AND t.is_missing = 0
        """
    )
    fun observePlaylistStats(playlistId: Long): Flow<PlaylistStats>

    /** Member ids in playlist order, for reorder operations and queue construction. */
    @Query(
        """
        SELECT t.track_id FROM tracks t
        INNER JOIN playlist_tracks pt ON t.track_id = pt.track_id
        WHERE pt.playlist_id = :playlistId AND t.is_missing = 0
        ORDER BY pt.position ASC
        """
    )
    suspend fun getOrderedTrackIds(playlistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlist_id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // ==================== Playlist Track Membership (M5-D2) ====================

    /**
     * Paged playlist members with SQL-resolved favorite state.
     *
     * `position ASC` is served by the existing `(playlist_id, position)` index, so paging reads the
     * pages the UI shows rather than the whole membership.
     *
     * No provider filter: a playlist holds local and provider media in one list (spec §14). The
     * `is_missing = 0` predicate hides a *local* file that has vanished without deleting the user's
     * membership row (M5-D2); a streamed member is never missing, so it is unaffected.
     */
    @Query(
        """
        SELECT t.track_id, t.media_store_id, t.title, t.artist, t.artist_id, t.album, t.album_id,
               t.track_number, t.disc_number, t.year, t.duration_ms, t.size_bytes, t.date_modified,
               t.date_added, t.relative_path, t.is_missing, t.provider, t.external_id, t.mime_type,
               t.artwork_url,
               EXISTS(SELECT 1 FROM favorite_tracks f WHERE f.track_id = t.track_id) AS is_favorite
        FROM tracks t
        INNER JOIN playlist_tracks pt ON t.track_id = pt.track_id
        WHERE pt.playlist_id = :playlistId AND t.is_missing = 0
        ORDER BY pt.position ASC
        """
    )
    fun pagingTracksForPlaylist(playlistId: Long): PagingSource<Int, TrackRow>

    /**
     * Observes non-orphaned tracks in a playlist ordered by explicit position ASC.
     * M5-D2: Orphaned tracks (is_missing = 1) are cleanly hidden without deleting user membership.
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.track_id = pt.track_id
        WHERE pt.playlist_id = :playlistId AND t.is_missing = 0
        ORDER BY pt.position ASC
    """)
    fun observeTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.track_id = pt.track_id
        WHERE pt.playlist_id = :playlistId AND t.is_missing = 0
        ORDER BY pt.position ASC
    """)
    suspend fun getTracksForPlaylist(playlistId: Long): List<TrackEntity>

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTrackEntries(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(entry: PlaylistTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTracksToPlaylist(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE track_id = :trackId")
    suspend fun removeTrackFromAllPlaylists(trackId: Long)

    // ==================== Reordering (M5-D5) ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlaylistTracks(entries: List<PlaylistTrackEntity>)

    /**
     * Atomically updates track positions in a single database transaction.
     */
    @Transaction
    suspend fun updateTrackPositions(entries: List<PlaylistTrackEntity>) {
        insertOrUpdatePlaylistTracks(entries)
    }
}

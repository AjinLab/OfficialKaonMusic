package com.kaon.music.core.data.db.dao

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlist_id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // ==================== Playlist Track Membership (M5-D2) ====================

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

package com.kaon.music.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks WHERE is_missing = 0 ORDER BY title_normalized ASC")
    fun observeAllActiveTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_missing = 0 ORDER BY title_normalized ASC")
    suspend fun getAllActiveTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks")
    suspend fun getAllStoredTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE track_id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE track_id IN (:trackIds) AND is_missing = 0")
    suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE media_store_id = :mediaStoreId LIMIT 1")
    suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity?

    /**
     * Plain Room LIKE search over indexed case-folded columns (§14 of ARCHITECTURE_ATTRIBUTED.md).
     */
    @Query("""
        SELECT * FROM tracks 
        WHERE is_missing = 0 
        AND (
            title_normalized LIKE '%' || :query || '%' 
            OR artist_normalized LIKE '%' || :query || '%' 
            OR album_normalized LIKE '%' || :query || '%'
        )
        ORDER BY title_normalized ASC 
        LIMIT 100
    """)
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    /**
     * Recently added active tracks ordered by date_added DESC.
     */
    @Query("SELECT * FROM tracks WHERE is_missing = 0 ORDER BY date_added DESC LIMIT :limit")
    fun observeRecentlyAddedTracks(limit: Int = 100): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_missing = 0 ORDER BY date_added DESC LIMIT :limit")
    suspend fun getRecentlyAddedTracks(limit: Int = 100): List<TrackEntity>

    // ==================== M3-D1 Derived Albums ====================

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

    @Query("UPDATE tracks SET media_store_id = :newMediaStoreId, is_missing = 0, last_seen_timestamp = :timestamp WHERE track_id = :trackId")
    suspend fun reLinkTrack(trackId: Long, newMediaStoreId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE tracks SET is_missing = 1 WHERE track_id IN (:trackIds)")
    suspend fun markTracksMissing(trackIds: List<Long>)

    @Query("UPDATE tracks SET is_missing = 0, last_seen_timestamp = :timestamp WHERE track_id IN (:trackIds)")
    suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM tracks WHERE is_missing = 1 AND last_seen_timestamp < :purgeCutoffTimestamp")
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

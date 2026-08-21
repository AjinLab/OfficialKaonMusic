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

    @Query("SELECT DISTINCT album, artist, album_id FROM tracks WHERE is_missing = 0 ORDER BY album_normalized ASC")
    fun observeDistinctAlbums(): Flow<List<AlbumSummary>>

    @Query("SELECT DISTINCT artist FROM tracks WHERE is_missing = 0 ORDER BY artist_normalized ASC")
    fun observeDistinctArtists(): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE album = :album AND is_missing = 0 ORDER BY track_id ASC")
    fun observeTracksByAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :artist AND is_missing = 0 ORDER BY album_normalized, title_normalized ASC")
    fun observeTracksByArtist(artist: String): Flow<List<TrackEntity>>

    /**
     * Re-linking heuristic lookup query (§2 of ARCHITECTURE_ATTRIBUTED.md):
     * Finds candidates within duration tolerance (+-1000ms) and exact file size.
     */
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
        val album: String,
        val artist: String,
        val album_id: Long
    )
}

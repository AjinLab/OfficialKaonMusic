package com.kaon.music.core.data.repository

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlaylistDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao
) {

    private val audioCollectionUri: Uri? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } catch (e: Throwable) {
            null
        }
    }

    // ==================== Playlist Operations ====================

    fun observeAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.observeAllPlaylistsWithCount().map { summaries ->
            summaries.map {
                Playlist(
                    id = it.playlist_id,
                    name = it.name,
                    trackCount = it.track_count,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at
                )
            }
        }
    }

    suspend fun getPlaylist(playlistId: Long): Playlist? {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return null
        val tracks = playlistDao.getTracksForPlaylist(playlistId)
        return Playlist(
            id = entity.playlistId,
            name = entity.name,
            trackCount = tracks.size,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim().ifBlank { "New Playlist" }
        val now = System.currentTimeMillis()
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = trimmed,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val existing = playlistDao.getPlaylistById(playlistId) ?: return
        val trimmed = newName.trim().ifBlank { existing.name }
        playlistDao.updatePlaylist(
            existing.copy(
                name = trimmed,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    // ==================== Playlist Track Membership (M5-D2, M5-D3) ====================

    fun observeTracksForPlaylist(playlistId: Long): Flow<List<Track>> {
        return combine(
            playlistDao.observeTracksForPlaylist(playlistId),
            favoriteDao.observeFavoriteTrackIds()
        ) { trackEntities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            trackEntities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    suspend fun getTracksForPlaylist(playlistId: Long): List<Track> {
        val trackEntities = playlistDao.getTracksForPlaylist(playlistId)
        val favSet = favoriteDao.getFavoriteTrackIds().toSet()
        return trackEntities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
    }

    /**
     * Adds a track to the end of the playlist.
     * Enforces application-level referential integrity and duplicate prevention.
     */
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
        val track = trackDao.getTrackById(trackId) ?: return false
        val maxPos = playlistDao.getMaxPosition(playlistId) ?: -1
        val nextPos = maxPos + 1

        val insertedId = playlistDao.addTrackToPlaylist(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.trackId,
                position = nextPos,
                addedAt = System.currentTimeMillis()
            )
        )
        // If insertedId == -1, conflict on composite PK (duplicate track) was ignored
        val added = insertedId != -1L
        if (added) {
            touchPlaylist(playlistId)
        }
        return added
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>): Int {
        val validTrackIds = trackDao.getTracksByIds(trackIds).map { it.trackId }
        if (validTrackIds.isEmpty()) return 0

        var currentPos = (playlistDao.getMaxPosition(playlistId) ?: -1) + 1
        val now = System.currentTimeMillis()

        val entries = validTrackIds.map { trackId ->
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = currentPos++,
                addedAt = now
            )
        }
        playlistDao.addTracksToPlaylist(entries)
        touchPlaylist(playlistId)
        return validTrackIds.size
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)

        // Re-index remaining tracks in transaction to avoid position gaps
        val remaining = playlistDao.getPlaylistTrackEntries(playlistId)
        val reindexed = remaining.mapIndexed { index, entry ->
            entry.copy(position = index)
        }
        if (reindexed.isNotEmpty()) {
            playlistDao.updateTrackPositions(reindexed)
        }
        touchPlaylist(playlistId)
    }

    /**
     * Transactional drag-to-reorder (M5-D5).
     * Given an ordered list of track IDs, updates their positions atomically.
     */
    suspend fun reorderTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        val currentEntries = playlistDao.getPlaylistTrackEntries(playlistId).associateBy { it.trackId }
        val updatedEntries = orderedTrackIds.mapIndexedNotNull { index, trackId ->
            currentEntries[trackId]?.copy(position = index)
        }

        if (updatedEntries.isNotEmpty()) {
            playlistDao.updateTrackPositions(updatedEntries)
            touchPlaylist(playlistId)
        }
    }

    private suspend fun touchPlaylist(playlistId: Long) {
        val existing = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(existing.copy(updatedAt = System.currentTimeMillis()))
    }

    private fun TrackEntity.toDomainModel(isFavorite: Boolean): Track {
        val uri: Uri? = try {
            val baseUri = audioCollectionUri
            if (baseUri != null) {
                ContentUris.withAppendedId(baseUri, mediaStoreId)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }

        return Track(
            id = trackId,
            mediaStoreId = mediaStoreId,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            dateModified = dateModified,
            dateAdded = dateAdded,
            contentUri = uri,
            isFavorite = isFavorite,
            isMissing = isMissing
        )
    }
}

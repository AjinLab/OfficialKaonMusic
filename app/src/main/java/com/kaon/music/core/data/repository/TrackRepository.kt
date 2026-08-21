package com.kaon.music.core.data.repository

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TrackRepository(
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val syncEngine: SyncEngine
) {

    private val audioCollectionUri: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    fun observeAllTracks(): Flow<List<Track>> {
        return combine(
            trackDao.observeAllActiveTracks(),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    fun searchTracks(query: String): Flow<List<Track>> {
        val normalizedQuery = query.trim().lowercase()
        return combine(
            trackDao.searchTracks(normalizedQuery),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    suspend fun getTrackById(trackId: Long): Track? {
        val entity = trackDao.getTrackById(trackId) ?: return null
        val isFav = favoriteDao.isFavorite(trackId)
        return entity.toDomainModel(isFavorite = isFav)
    }

    suspend fun getTracksByIds(trackIds: List<Long>): List<Track> {
        val entities = trackDao.getTracksByIds(trackIds)
        val entitiesById = entities.associateBy { it.trackId }
        // Preserve input order
        return trackIds.mapNotNull { id ->
            entitiesById[id]?.toDomainModel(isFavorite = false)
        }
    }

    suspend fun toggleFavorite(trackId: Long) {
        if (favoriteDao.isFavorite(trackId)) {
            favoriteDao.removeFavorite(trackId)
        } else {
            favoriteDao.addFavorite(FavoriteTrackEntity(trackId = trackId))
        }
    }

    fun observeIsFavorite(trackId: Long): Flow<Boolean> {
        return favoriteDao.observeIsFavorite(trackId)
    }

    suspend fun syncLibrary(): SyncResult {
        return syncEngine.synchronize()
    }

    private fun TrackEntity.toDomainModel(isFavorite: Boolean): Track {
        return Track(
            id = trackId,
            mediaStoreId = mediaStoreId,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            dateModified = dateModified,
            contentUri = ContentUris.withAppendedId(audioCollectionUri, mediaStoreId),
            isFavorite = isFavorite,
            isMissing = isMissing
        )
    }
}

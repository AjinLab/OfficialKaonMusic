package com.kaon.music.core.data.repository

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TrackRepository(
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val syncEngine: SyncEngine,
    private val playEventDao: PlayEventDao? = null
) {

    private val audioCollectionUri: Uri?
        get() = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } catch (e: Throwable) {
            null
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

    fun observeFavoriteTracks(): Flow<List<Track>> {
        return favoriteDao.observeFavoriteTrackEntities().map { entities ->
            entities.map { it.toDomainModel(isFavorite = true) }
        }
    }

    suspend fun getFavoriteTracks(): List<Track> {
        return favoriteDao.getFavoriteTrackEntities().map {
            it.toDomainModel(isFavorite = true)
        }
    }

    fun observeRecentlyPlayedTracks(limit: Int = 100): Flow<List<Track>> {
        val playDao = playEventDao ?: return flowOf(emptyList())
        return combine(
            playDao.observeRecentlyPlayedTrackEntities(limit),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    suspend fun getRecentlyPlayedTracks(limit: Int = 100): List<Track> {
        val playDao = playEventDao ?: return emptyList()
        val entities = playDao.getRecentlyPlayedTrackEntities(limit)
        return entities.map { it.toDomainModel(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    fun observeMostPlayedTracks(limit: Int = 100): Flow<List<Track>> {
        val playDao = playEventDao ?: return flowOf(emptyList())
        return combine(
            playDao.observeMostPlayedTrackEntities(limit),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    suspend fun getMostPlayedTracks(limit: Int = 100): List<Track> {
        val playDao = playEventDao ?: return emptyList()
        val entities = playDao.getMostPlayedTrackEntities(limit)
        return entities.map { it.toDomainModel(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    fun observeRecentlyAddedTracks(limit: Int = 100): Flow<List<Track>> {
        return combine(
            trackDao.observeRecentlyAddedTracks(limit),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    suspend fun getRecentlyAddedTracks(limit: Int = 100): List<Track> {
        val entities = trackDao.getRecentlyAddedTracks(limit)
        return entities.map { it.toDomainModel(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    // ==================== Derived Albums ====================

    fun observeAllAlbums(): Flow<List<Album>> {
        return trackDao.observeAllAlbums().map { summaries ->
            summaries.map {
                Album(
                    albumId = it.album_id,
                    title = it.album,
                    artist = it.artist,
                    artistId = it.artist_id,
                    year = it.year,
                    trackCount = it.track_count,
                    totalDurationMs = it.total_duration_ms
                )
            }
        }
    }

    suspend fun getAlbumById(albumId: Long): Album? {
        val summary = trackDao.getAlbumById(albumId) ?: return null
        return Album(
            albumId = summary.album_id,
            title = summary.album,
            artist = summary.artist,
            artistId = summary.artist_id,
            year = summary.year,
            trackCount = summary.track_count,
            totalDurationMs = summary.total_duration_ms
        )
    }

    fun observeTracksForAlbum(albumId: Long): Flow<List<Track>> {
        return combine(
            trackDao.observeTracksForAlbum(albumId),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    suspend fun getTracksForAlbum(albumId: Long): List<Track> {
        val entities = trackDao.getTracksForAlbum(albumId)
        return entities.map { it.toDomainModel(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    // ==================== Derived Artists ====================

    fun observeAllArtists(): Flow<List<Artist>> {
        return trackDao.observeAllArtists().map { summaries ->
            summaries.map {
                Artist(
                    artistId = it.artist_id,
                    name = it.artist,
                    albumCount = it.album_count,
                    trackCount = it.track_count
                )
            }
        }
    }

    fun observeAlbumsForArtist(artistName: String): Flow<List<Album>> {
        return trackDao.observeAlbumsForArtist(artistName.trim().lowercase()).map { summaries ->
            summaries.map {
                Album(
                    albumId = it.album_id,
                    title = it.album,
                    artist = it.artist,
                    artistId = it.artist_id,
                    year = it.year,
                    trackCount = it.track_count,
                    totalDurationMs = it.total_duration_ms
                )
            }
        }
    }

    fun observeTracksForArtist(artistName: String): Flow<List<Track>> {
        return combine(
            trackDao.observeTracksForArtist(artistName.trim().lowercase()),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomainModel(isFavorite = favSet.contains(it.trackId)) }
        }
    }

    suspend fun getTracksForArtist(artistName: String): List<Track> {
        val entities = trackDao.getTracksForArtist(artistName.trim().lowercase())
        return entities.map { it.toDomainModel(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    // ==================== Common Lookups ====================

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
        val favSet = favoriteDao.getFavoriteTrackIds().toSet()
        // Preserve input order
        return trackIds.mapNotNull { id ->
            entitiesById[id]?.toDomainModel(isFavorite = favSet.contains(id))
        }
    }

    suspend fun toggleFavorite(trackId: Long) {
        // Search results from YouTube are playable queue-only tracks and do not
        // have a local Room row to satisfy the FavoriteTrack foreign key.
        if (trackDao.getTrackById(trackId) == null) return
        if (favoriteDao.isFavorite(trackId)) {
            favoriteDao.removeFavorite(trackId)
        } else {
            favoriteDao.addFavorite(FavoriteTrackEntity(trackId = trackId))
        }
    }

    fun observeIsFavorite(trackId: Long): Flow<Boolean> {
        return favoriteDao.observeIsFavorite(trackId)
    }

    suspend fun syncLibrary(minDurationMs: Long = 5000L): SyncResult {
        return syncEngine.synchronize(minDurationMs = minDurationMs)
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
            isMissing = isMissing,
            source = source,
            youtubeVideoId = youtubeVideoId,
            mimeType = mimeType
        )
    }
}

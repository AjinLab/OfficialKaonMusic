package com.kaon.music.core.data.db.dao

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Default implementations for the DAO surface, for JVM tests that only care about a few methods.
 *
 * Phase 3 added paged and count queries to every library DAO. Without a shared base, each of the
 * eight hand-written fakes across the test sources would carry ~15 unused overrides, and adding a
 * query later would break all of them again. Tests override only what they exercise.
 *
 * `PagingSource` returns an empty page rather than throwing: a fake that cannot page is still a
 * valid fake for a test about counts or sorting, and a throwing default would fail the ViewModel's
 * unrelated flows.
 */
class EmptyPagingSource<V : Any> : PagingSource<Int, V>() {
    override fun getRefreshKey(state: PagingState<Int, V>): Int? = null
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, V> =
        LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
}

/**
 * Pages an in-memory list, so a fake DAO can be exercised through Paging.
 *
 * Room invalidates its `PagingSource` through the invalidation tracker when the underlying tables
 * change. A fake has no tracker, so [FakePagingInvalidator] plays that role: the fake calls
 * `invalidateAll()` after a mutation and Paging asks the factory for a fresh source. Without it a
 * snapshot taken after an insert would still show the pre-insert list, and the test would pass or
 * fail for the wrong reason.
 */
class FakeListPagingSource<V : Any>(private val items: () -> List<V>) : PagingSource<Int, V>() {
    override fun getRefreshKey(state: PagingState<Int, V>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, V> {
        val all = items()
        val start = params.key ?: 0
        val end = minOf(start + params.loadSize, all.size)
        val page = if (start >= all.size) emptyList() else all.subList(start, end)
        return LoadResult.Page(
            data = page,
            prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
            nextKey = if (end >= all.size) null else end,
            itemsBefore = start,
            itemsAfter = (all.size - end).coerceAtLeast(0)
        )
    }
}

/** Tracks issued fake paging sources so a mutation can invalidate them, as Room's tracker would. */
class FakePagingInvalidator {
    private val issued = mutableListOf<PagingSource<*, *>>()

    fun <V : Any> source(items: () -> List<V>): PagingSource<Int, V> =
        FakeListPagingSource(items).also { issued += it }

    fun invalidateAll() {
        val snapshot = issued.toList()
        issued.clear()
        snapshot.forEach { it.invalidate() }
    }
}

abstract class FakeTrackDaoBase : TrackDao {
    override fun observeAllActiveTracks(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getAllActiveTracks(): List<TrackEntity> = emptyList()
    override suspend fun getAllStoredTracks(): List<TrackEntity> = emptyList()

    override fun pagingTracksByTitle(query: String): PagingSource<Int, TrackRow> = EmptyPagingSource()
    override fun pagingTracksByDateAdded(query: String): PagingSource<Int, TrackRow> = EmptyPagingSource()
    override fun pagingTracksByPlayCount(query: String): PagingSource<Int, TrackRow> = EmptyPagingSource()
    override fun pagingFavoriteTracks(query: String): PagingSource<Int, TrackRow> = EmptyPagingSource()
    override fun pagingAlbums(query: String): PagingSource<Int, TrackDao.AlbumSummary> = EmptyPagingSource()
    override fun pagingArtists(query: String): PagingSource<Int, TrackDao.ArtistSummary> = EmptyPagingSource()

    override fun observeActiveTrackCount(query: String): Flow<Int> = flowOf(0)
    override fun observeFavoriteTrackCount(query: String): Flow<Int> = flowOf(0)

    override suspend fun getActiveTrackIdsByTitle(query: String, limit: Int): List<Long> = emptyList()
    override suspend fun getActiveTrackIdsByDateAdded(query: String, limit: Int): List<Long> = emptyList()
    override suspend fun getActiveTrackIdsByPlayCount(query: String, limit: Int): List<Long> = emptyList()
    override suspend fun getFavoriteTrackIds(query: String, limit: Int): List<Long> = emptyList()

    override suspend fun getTrackById(trackId: Long): TrackEntity? = null
    override suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity> = emptyList()
    override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity? = null

    override suspend fun getTrackByExternalIdentity(provider: String, externalId: String): TrackEntity? = null

    override suspend fun upsertByExternalIdentity(track: TrackEntity): Long = 0L

    override fun searchTracks(query: String, limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun searchAlbums(query: String, limit: Int): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
    override fun searchArtists(query: String, limit: Int): Flow<List<TrackDao.ArtistSummary>> = flowOf(emptyList())

    override fun observeRecentlyAddedTracks(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getRecentlyAddedTracks(limit: Int): List<TrackEntity> = emptyList()

    override fun observeAllAlbums(): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
    override fun observeRecentAlbums(limit: Int): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
    override suspend fun getAlbumById(albumId: Long): TrackDao.AlbumSummary? = null
    override fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getTracksForAlbum(albumId: Long): List<TrackEntity> = emptyList()

    override fun observeAllArtists(): Flow<List<TrackDao.ArtistSummary>> = flowOf(emptyList())
    override fun observeRecentArtists(limit: Int): Flow<List<TrackDao.ArtistSummary>> = flowOf(emptyList())
    override fun observeAlbumsForArtist(artistNormalized: String): Flow<List<TrackDao.AlbumSummary>> =
        flowOf(emptyList())
    override fun observeTracksForArtist(artistNormalized: String): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getTracksForArtist(artistNormalized: String): List<TrackEntity> = emptyList()

    override suspend fun findReLinkCandidates(
        minDurationMs: Long,
        maxDurationMs: Long,
        sizeBytes: Long
    ): List<TrackEntity> = emptyList()

    override suspend fun insertTrack(track: TrackEntity): Long = 0L
    override suspend fun insertTracks(tracks: List<TrackEntity>): List<Long> = emptyList()
    override suspend fun updateTrack(track: TrackEntity) = Unit
    override suspend fun updateTracks(tracks: List<TrackEntity>) = Unit
    override suspend fun reLinkTrack(
        trackId: Long,
        newMediaStoreId: Long,
        newRelativePath: String?,
        timestamp: Long
    ) = Unit
    override suspend fun markTracksMissing(trackIds: List<Long>) = Unit
    override suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long) = Unit
    override suspend fun purgeOrphanedTracks(purgeCutoffTimestamp: Long): Int = 0
}

abstract class FakeFavoriteDaoBase : FavoriteDao {
    override fun observeFavoriteTrackIds(): Flow<List<Long>> = flowOf(emptyList())
    override suspend fun getFavoriteTrackIds(): List<Long> = emptyList()
    override fun observeFavoriteTrackEntities(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun observeFavoriteTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getFavoriteTrackEntities(): List<TrackEntity> = emptyList()
    override fun observeIsFavorite(trackId: Long): Flow<Boolean> = flowOf(false)
    override suspend fun isFavorite(trackId: Long): Boolean = false
    override suspend fun addFavorite(favorite: com.kaon.music.core.data.db.entity.FavoriteTrackEntity) = Unit
    override suspend fun removeFavorite(trackId: Long) = Unit
}

abstract class FakePlaylistDaoBase : PlaylistDao {
    override fun observeAllPlaylistsWithCount(): Flow<List<PlaylistDao.PlaylistSummary>> = flowOf(emptyList())
    override suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? = null
    override suspend fun countTracksInPlaylist(playlistId: Long): Int = 0
    override fun observePlaylistStats(playlistId: Long): Flow<PlaylistDao.PlaylistStats> =
        flowOf(PlaylistDao.PlaylistStats(0, 0L))
    override suspend fun getOrderedTrackIds(playlistId: Long): List<Long> = emptyList()
    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long = 0L
    override suspend fun updatePlaylist(playlist: PlaylistEntity) = Unit
    override suspend fun deletePlaylist(playlistId: Long) = Unit
    override fun pagingTracksForPlaylist(playlistId: Long): PagingSource<Int, TrackRow> = EmptyPagingSource()
    override fun observeTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getTracksForPlaylist(playlistId: Long): List<TrackEntity> = emptyList()
    override suspend fun getPlaylistTrackEntries(playlistId: Long): List<PlaylistTrackEntity> = emptyList()
    override suspend fun getMaxPosition(playlistId: Long): Int? = null
    override suspend fun addTrackToPlaylist(entry: PlaylistTrackEntity): Long = 0L
    override suspend fun addTracksToPlaylist(entries: List<PlaylistTrackEntity>) = Unit
    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = Unit
    override suspend fun removeTrackFromAllPlaylists(trackId: Long) = Unit
    override suspend fun insertOrUpdatePlaylistTracks(entries: List<PlaylistTrackEntity>) = Unit
}

abstract class FakePlayEventDaoBase : PlayEventDao {
    override suspend fun insertEvent(event: com.kaon.music.core.data.db.entity.PlayEventEntity): Long = 0L
    override fun observeRecentlyPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getRecentlyPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
    override fun observeMostPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getMostPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
    override suspend fun clearAllEvents(): Int = 0
}

/** Projects a fake's stored entity into the SQL row shape the paged queries return. */
fun TrackEntity.toTrackRow(isFavorite: Boolean): TrackRow {
    val audio = com.kaon.music.core.data.model.AudioSpec(mimeType = mimeType, sizeBytes = sizeBytes)
    val locator = com.kaon.music.core.data.db.mediaLocatorOf(provider, externalId, mediaStoreId, relativePath, audio)
    return TrackRow(
        track_id = trackId,
        media_store_id = mediaStoreId,
        title = title,
        artist = artist,
        artist_id = artistId,
        album = album,
        album_id = albumId,
        track_number = trackNumber,
        disc_number = discNumber,
        year = year,
        duration_ms = durationMs,
        size_bytes = sizeBytes,
        date_modified = dateModified,
        date_added = dateAdded,
        relative_path = relativePath,
        is_missing = isMissing,
        provider = provider,
        external_id = externalId,
        mime_type = mimeType,
        artwork_url = artworkUrl,
        is_favorite = isFavorite
    )
}

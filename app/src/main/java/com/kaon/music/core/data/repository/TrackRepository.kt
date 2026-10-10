package com.kaon.music.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.normalizeForStorage
import com.kaon.music.core.data.db.toDomain
import com.kaon.music.core.data.db.toEntity
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.ProviderId
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackId
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.data.sync.SyncResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Sort orders available to the paged track list. Each maps to one indexed DAO query. */
enum class LibrarySort {
    TITLE_ASC,
    DATE_ADDED_DESC,
    PLAY_COUNT_DESC
}

class TrackRepository(
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val syncEngine: SyncEngine,
    private val playEventDao: PlayEventDao? = null,
    /**
     * Where entity→domain mapping runs.
     *
     * ARCHITECTURE.md §5.4: CPU mapping uses `Dispatchers.Default`. Room already performs its I/O on
     * its own executor; what was left on the collector's dispatcher (Main, for a ViewModel) was the
     * per-row mapping, which is the part that scales with library size.
     */
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    // ==================== Paged library access ====================

    /**
     * Paged library tracks, filtered and sorted in SQL.
     *
     * [PagingData.map] runs per loaded page, so mapping cost is proportional to what the UI has
     * scrolled through — not to the library. The flow is not `flowOn`-ed here: Paging performs its
     * loads on the [Pager]'s fetch dispatcher already, and moving the resulting `PagingData` stream
     * between dispatchers breaks its presenter contract.
     */
    fun pagedTracks(
        query: String = "",
        sort: LibrarySort = LibrarySort.TITLE_ASC,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Flow<PagingData<Track>> {
        val normalized = query.normalizeForSearch()
        return Pager(config = pagingConfig(pageSize)) {
            when (sort) {
                LibrarySort.TITLE_ASC -> trackDao.pagingTracksByTitle(normalized)
                LibrarySort.DATE_ADDED_DESC -> trackDao.pagingTracksByDateAdded(normalized)
                LibrarySort.PLAY_COUNT_DESC -> trackDao.pagingTracksByPlayCount(normalized)
            }
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    fun pagedFavoriteTracks(
        query: String = "",
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Flow<PagingData<Track>> {
        val normalized = query.normalizeForSearch()
        return Pager(config = pagingConfig(pageSize)) {
            trackDao.pagingFavoriteTracks(normalized)
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    fun pagedAlbums(query: String = "", pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<Album>> {
        val normalized = query.normalizeForSearch()
        return Pager(config = pagingConfig(pageSize)) {
            trackDao.pagingAlbums(normalized)
        }.flow.map { pagingData -> pagingData.map { it.toAlbum() } }
    }

    fun pagedArtists(query: String = "", pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<Artist>> {
        val normalized = query.normalizeForSearch()
        return Pager(config = pagingConfig(pageSize)) {
            trackDao.pagingArtists(normalized)
        }.flow.map { pagingData -> pagingData.map { it.toArtist() } }
    }

    /** Count of the same result set [pagedTracks] pages over, for headers and empty-state decisions. */
    fun observeTrackCount(query: String = ""): Flow<Int> =
        trackDao.observeActiveTrackCount(query.normalizeForSearch())

    fun observeFavoriteCount(query: String = ""): Flow<Int> =
        trackDao.observeFavoriteTrackCount(query.normalizeForSearch())

    /**
     * Builds a playback queue from the current list context.
     *
     * Bounded by [limit]: tapping a track in a 50k library enqueues a large-but-finite window in the
     * same SQL order the list shows, rather than materialising the library to hand Media3 a timeline.
     */
    suspend fun getQueueForContext(
        query: String = "",
        sort: LibrarySort = LibrarySort.TITLE_ASC,
        favoritesOnly: Boolean = false,
        limit: Int = MAX_QUEUE_SIZE
    ): List<Track> {
        val normalized = query.normalizeForSearch()
        val ids = if (favoritesOnly) {
            trackDao.getFavoriteTrackIds(normalized, limit)
        } else {
            when (sort) {
                LibrarySort.TITLE_ASC -> trackDao.getActiveTrackIdsByTitle(normalized, limit)
                LibrarySort.DATE_ADDED_DESC -> trackDao.getActiveTrackIdsByDateAdded(normalized, limit)
                LibrarySort.PLAY_COUNT_DESC -> trackDao.getActiveTrackIdsByPlayCount(normalized, limit)
            }
        }
        return getTracksByIds(ids)
    }

    fun observeAllTracks(): Flow<List<Track>> {
        return combine(
            trackDao.observeAllActiveTracks(),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    fun observeFavoriteTracks(): Flow<List<Track>> {
        return favoriteDao.observeFavoriteTrackEntities().map { entities ->
            entities.map { it.toDomain(isFavorite = true) }
        }.flowOn(mappingDispatcher)
    }

    /**
     * Bounded favorites for a fixed-size row.
     *
     * Named `…Sample` rather than `pagedFavorites` because it is a `LIMIT`, not Paging: a carousel of
     * six to fifty items gains nothing from a pager and would add a presenter for no benefit.
     */
    fun pagedFavoriteSample(limit: Int): Flow<List<Track>> {
        return favoriteDao.observeFavoriteTrackEntities(limit).map { entities ->
            entities.map { it.toDomain(isFavorite = true) }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getFavoriteTracks(): List<Track> {
        return favoriteDao.getFavoriteTrackEntities().map {
            it.toDomain(isFavorite = true)
        }
    }

    fun observeRecentlyPlayedTracks(limit: Int = 100): Flow<List<Track>> {
        val playDao = playEventDao ?: return flowOf(emptyList())
        return combine(
            playDao.observeRecentlyPlayedTrackEntities(limit),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getRecentlyPlayedTracks(limit: Int = 100): List<Track> {
        val playDao = playEventDao ?: return emptyList()
        val entities = playDao.getRecentlyPlayedTrackEntities(limit)
        return entities.map { it.toDomain(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    fun observeMostPlayedTracks(limit: Int = 100): Flow<List<Track>> {
        val playDao = playEventDao ?: return flowOf(emptyList())
        return combine(
            playDao.observeMostPlayedTrackEntities(limit),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getMostPlayedTracks(limit: Int = 100): List<Track> {
        val playDao = playEventDao ?: return emptyList()
        val entities = playDao.getMostPlayedTrackEntities(limit)
        return entities.map { it.toDomain(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    fun observeRecentlyAddedTracks(limit: Int = 100): Flow<List<Track>> {
        return combine(
            trackDao.observeRecentlyAddedTracks(limit),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getRecentlyAddedTracks(limit: Int = 100): List<Track> {
        val entities = trackDao.getRecentlyAddedTracks(limit)
        return entities.map { it.toDomain(isFavorite = favoriteDao.isFavorite(it.trackId)) }
    }

    // ==================== Derived Albums ====================

    fun observeAllAlbums(): Flow<List<Album>> {
        return trackDao.observeAllAlbums().map { summaries ->
            summaries.map { it.toAlbum() }
        }.flowOn(mappingDispatcher)
    }

    /** Bounded album row for the Home feed. */
    fun observeRecentAlbums(limit: Int): Flow<List<Album>> {
        return trackDao.observeRecentAlbums(limit).map { summaries ->
            summaries.map { it.toAlbum() }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getAlbumById(albumId: Long): Album? =
        trackDao.getAlbumById(albumId)?.toAlbum()

    fun observeTracksForAlbum(albumId: Long): Flow<List<Track>> {
        return combine(
            trackDao.observeTracksForAlbum(albumId),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getTracksForAlbum(albumId: Long): List<Track> {
        val entities = trackDao.getTracksForAlbum(albumId)
        val favSet = favoriteDao.getFavoriteTrackIds().toSet()
        return entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
    }

    // ==================== Derived Artists ====================

    fun observeAllArtists(): Flow<List<Artist>> {
        return trackDao.observeAllArtists().map { summaries ->
            summaries.map { it.toArtist() }
        }.flowOn(mappingDispatcher)
    }

    /** Bounded artist row for the Home feed. */
    fun observeRecentArtists(limit: Int): Flow<List<Artist>> {
        return trackDao.observeRecentArtists(limit).map { summaries ->
            summaries.map { it.toArtist() }
        }.flowOn(mappingDispatcher)
    }

    fun observeAlbumsForArtist(artistName: String): Flow<List<Album>> {
        return trackDao.observeAlbumsForArtist(artistName.trim().lowercase()).map { summaries ->
            summaries.map { it.toAlbum() }
        }.flowOn(mappingDispatcher)
    }

    fun observeTracksForArtist(artistName: String): Flow<List<Track>> {
        return combine(
            trackDao.observeTracksForArtist(artistName.trim().lowercase()),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getTracksForArtist(artistName: String): List<Track> {
        val entities = trackDao.getTracksForArtist(artistName.trim().lowercase())
        val favSet = favoriteDao.getFavoriteTrackIds().toSet()
        return entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
    }

    // ==================== Common Lookups ====================

    /**
     * Bounded local search.
     *
     * [limit] is explicit rather than baked into the DAO so a caller that shows a capped section
     * ("Local Library Songs") asks for exactly what it renders.
     */
    fun searchTracks(query: String, limit: Int = SEARCH_RESULT_LIMIT): Flow<List<Track>> {
        val normalizedQuery = query.normalizeForSearch()
        return combine(
            trackDao.searchTracks(normalizedQuery, limit),
            favoriteDao.observeFavoriteTrackIds()
        ) { entities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            entities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    fun searchAlbums(query: String, limit: Int = SEARCH_RESULT_LIMIT): Flow<List<Album>> =
        trackDao.searchAlbums(query.normalizeForSearch(), limit)
            .map { summaries -> summaries.map { it.toAlbum() } }
            .flowOn(mappingDispatcher)

    fun searchArtists(query: String, limit: Int = SEARCH_RESULT_LIMIT): Flow<List<Artist>> =
        trackDao.searchArtists(query.normalizeForSearch(), limit)
            .map { summaries -> summaries.map { it.toArtist() } }
            .flowOn(mappingDispatcher)

    suspend fun getTrackById(trackId: Long): Track? {
        val entity = trackDao.getTrackById(trackId) ?: return null
        return entity.toDomain(isFavorite = favoriteDao.isFavorite(trackId))
    }

    suspend fun getTracksByIds(trackIds: List<Long>): List<Track> {
        val entities = trackDao.getTracksByIds(trackIds)
        val entitiesById = entities.associateBy { it.trackId }
        val favSet = favoriteDao.getFavoriteTrackIds().toSet()
        // Preserve input order
        return trackIds.mapNotNull { id ->
            entitiesById[id]?.toDomain(isFavorite = favSet.contains(id))
        }
    }

    // ==================== Identity ====================

    /**
     * Looks a track up by provider identity.
     *
     * The pair is the external identity; `externalId` alone is not unique across providers (spec §5).
     */
    suspend fun getTrackByExternalIdentity(provider: ProviderId, externalId: String): Track? {
        val entity = trackDao.getTrackByExternalIdentity(provider.value, externalId.trim())
            ?: return null
        return entity.toDomain(isFavorite = favoriteDao.isFavorite(entity.trackId))
    }

    /**
     * Makes [track] durable and returns it with a real [TrackId].
     *
     * This is the single door through which provider media enters Room, and it is idempotent: the
     * upsert keys on `(provider, external_id)`, so resolving or playing the same YouTube video any
     * number of times yields one row with one stable id (spec §7, §30.4). An already-saved track is
     * returned untouched — nothing re-writes a row just because something asked for it.
     *
     * Local tracks are owned by [SyncEngine] and are not created here; passing one through returns it
     * unchanged rather than inserting a row MediaStore never reported.
     */
    suspend fun ensurePersisted(track: Track): Track {
        if (track.id.isSaved) return track
        val externalId = track.externalId?.takeIf { it.isNotBlank() } ?: return track

        val assignedId = trackDao.upsertByExternalIdentity(
            track.toEntity().copy(externalId = externalId.trim())
        )
        return track.copy(id = TrackId(assignedId))
    }

    /**
     * Persists several provider tracks, preserving input order.
     *
     * Used when a whole queue of search results has to become durable at once; one call per track would
     * be one transaction per track.
     */
    suspend fun ensureAllPersisted(tracks: List<Track>): List<Track> =
        tracks.map { ensurePersisted(it) }

    /**
     * Replaces a track's locator without touching its identity.
     *
     * The re-link path (spec §13) and, later, a completed download. `TrackId` is untouched by
     * construction: only the locator columns are written.
     */
    suspend fun updateLocator(trackId: TrackId, locator: MediaLocator) {
        val existing = trackDao.getTrackById(trackId.value) ?: return
        trackDao.updateTrack(
            existing.copy(
                provider = locator.provider.value,
                externalId = locator.externalId,
                mediaStoreId = (locator as? MediaLocator.LocalFile)?.mediaStoreId,
                relativePath = when (locator) {
                    is MediaLocator.LocalFile -> locator.relativePath
                    is MediaLocator.Downloaded -> locator.fileName
                    is MediaLocator.Remote -> null
                },
                isMissing = false,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )
    }

    /** Refreshes displayed metadata. Identity, locator and user data are untouched. */
    suspend fun updateMetadata(
        trackId: TrackId,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artworkUrl: String? = null
    ) {
        val existing = trackDao.getTrackById(trackId.value) ?: return
        trackDao.updateTrack(
            existing.copy(
                title = title ?: existing.title,
                artist = artist ?: existing.artist,
                album = album ?: existing.album,
                titleNormalized = (title ?: existing.title).normalizeForStorage(),
                artistNormalized = (artist ?: existing.artist).normalizeForStorage(),
                albumNormalized = (album ?: existing.album).normalizeForStorage(),
                artworkUrl = artworkUrl ?: existing.artworkUrl
            )
        )
    }

    // ==================== Favorites ====================

    /**
     * Toggles favorite state, persisting the track first when it is not yet a row.
     *
     * The previous implementation returned early when no row existed, which meant favoriting a YouTube
     * search result silently did nothing — the exact failure spec §23 forbids. A provider track is now
     * upserted and then favorited; the returned id is the durable one.
     *
     * Returns the [TrackId] the favorite was recorded against, or `null` when the track cannot be made
     * durable (an unsaved *local* track, which only a bug could produce).
     */
    suspend fun toggleFavorite(track: Track): TrackId? {
        val persisted = ensurePersisted(track)
        if (!persisted.id.isSaved) return null
        toggleFavorite(persisted.id.value)
        return persisted.id
    }

    /**
     * Toggles favorite state for a track already known to be persisted.
     *
     * Kept for callers that hold only an id (a list row). The existence check remains, because
     * favoriting a nonexistent id would create a row referencing nothing now that the foreign key is
     * gone — but it is no longer the *remote* early return: a remote track has a row by this point.
     */
    suspend fun toggleFavorite(trackId: Long) {
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

    private fun TrackDao.AlbumSummary.toAlbum() = Album(
        albumId = album_id,
        title = album,
        artist = artist,
        artistId = artist_id,
        year = year,
        trackCount = track_count,
        totalDurationMs = total_duration_ms
    )

    private fun TrackDao.ArtistSummary.toArtist() = Artist(
        artistId = artist_id,
        name = artist,
        albumCount = album_count,
        trackCount = track_count
    )

    private fun pagingConfig(pageSize: Int) = PagingConfig(
        pageSize = pageSize,
        // Placeholders keep scroll position stable while a page loads without the presenter needing
        // the total row set in memory.
        enablePlaceholders = true,
        initialLoadSize = pageSize * 2,
        prefetchDistance = pageSize
    )

    companion object {
        /** ~2 screens of rows on a phone: large enough to avoid load churn, small enough to map cheaply. */
        const val DEFAULT_PAGE_SIZE = 50

        /**
         * Upper bound on a queue built from a list context. Media3 holds every item as a `MediaItem`
         * in its timeline, so an unbounded queue is memory proportional to the library.
         */
        const val MAX_QUEUE_SIZE = 2_000

        const val SEARCH_RESULT_LIMIT = 100
    }
}

/** One normalization rule for every search predicate, matching the case-folded columns. */
internal fun String.normalizeForSearch(): String = trim().lowercase()

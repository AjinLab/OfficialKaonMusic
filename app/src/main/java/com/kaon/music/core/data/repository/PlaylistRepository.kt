package com.kaon.music.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlaylistDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.toDomain
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    /**
     * Used to make provider media durable before it becomes playlist membership.
     *
     * A playlist entry is a foreign reference to `tracks`; a YouTube search result has no row until
     * something persists it, and [TrackRepository.ensurePersisted] is the one door that does so
     * idempotently (spec §7). Injected rather than duplicating the upsert here, so there is exactly one
     * place provider identity turns into a row.
     */
    private val trackRepository: TrackRepository,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

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
        }.flowOn(mappingDispatcher)
    }

    /**
     * Playlist metadata without materialising its members.
     *
     * The previous implementation loaded every member track just to report `trackCount`; the count
     * comes from the aggregate query the list already uses.
     */
    suspend fun getPlaylist(playlistId: Long): Playlist? {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return null
        return Playlist(
            id = entity.playlistId,
            name = entity.name,
            trackCount = playlistDao.countTracksInPlaylist(playlistId),
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

    /** Paged playlist members; favorite state and ordering both come from SQL. */
    fun pagedTracksForPlaylist(
        playlistId: Long,
        pageSize: Int = TrackRepository.DEFAULT_PAGE_SIZE
    ): Flow<PagingData<Track>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = true,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize
            )
        ) {
            playlistDao.pagingTracksForPlaylist(playlistId)
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    /** Header facts for a playlist detail screen, without materialising its members. */
    fun observePlaylistStats(playlistId: Long): Flow<PlaylistStats> =
        playlistDao.observePlaylistStats(playlistId)
            .map { PlaylistStats(trackCount = it.track_count, totalDurationMs = it.total_duration_ms) }
            .flowOn(mappingDispatcher)

    /** Member ids in playlist order. Used to compute a reorder without holding the member rows. */
    suspend fun getOrderedTrackIds(playlistId: Long): List<Long> =
        playlistDao.getOrderedTrackIds(playlistId)

    fun observeTracksForPlaylist(playlistId: Long): Flow<List<Track>> {
        return combine(
            playlistDao.observeTracksForPlaylist(playlistId),
            favoriteDao.observeFavoriteTrackIds()
        ) { trackEntities, favoriteIds ->
            val favSet = favoriteIds.toSet()
            trackEntities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
        }.flowOn(mappingDispatcher)
    }

    suspend fun getTracksForPlaylist(playlistId: Long): List<Track> {
        val trackEntities = playlistDao.getTracksForPlaylist(playlistId)
        val favSet = favoriteDao.getFavoriteTrackIds().toSet()
        return trackEntities.map { it.toDomain(isFavorite = favSet.contains(it.trackId)) }
    }

    /**
     * Adds a track to the end of the playlist.
     * Enforces application-level referential integrity and duplicate prevention.
     */
    /**
     * Adds a track by id, appending it to the end.
     *
     * The id must already be durable. A caller holding a possibly-unsaved [Track] — a YouTube search
     * result — uses [addTrack] instead, which persists first (spec §23: no silent early return).
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

    /**
     * Adds a domain track, persisting provider media first.
     *
     * A YouTube track reaches this with [TrackId.UNSAVED]; without the upsert the membership row would
     * reference an id that does not exist, which is what "add to playlist" silently did before
     * (spec §6, §14). Local and provider media take the same path from here — one playlist model.
     */
    suspend fun addTrack(playlistId: Long, track: Track): Boolean {
        val persisted = trackRepository.ensurePersisted(track)
        if (!persisted.id.isSaved) return false
        return addTrackToPlaylist(playlistId, persisted.id.value)
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

    /** Adds domain tracks, persisting any provider media first. Used by "save queue as playlist". */
    suspend fun addTracks(playlistId: Long, tracks: List<Track>): Int {
        val persisted = trackRepository.ensureAllPersisted(tracks).filter { it.id.isSaved }
        if (persisted.isEmpty()) return 0
        return addTracksToPlaylist(playlistId, persisted.map { it.id.value })
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

}

/** Playlist header aggregate: what the detail screen shows above the paged list. */
data class PlaylistStats(
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L
)

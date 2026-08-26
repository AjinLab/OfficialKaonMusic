package com.kaon.music.feature.search

import android.content.ContextWrapper
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.PlaylistDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.PlaylistRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.sync.MediaStoreScanner
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.network.NetworkMonitor
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.feature.library.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeTrack1 = TrackEntity(
        trackId = 1L,
        mediaStoreId = 101L,
        title = "Midnight Echoes",
        artist = "Solaris",
        album = "Deep Space",
        albumId = 10L,
        durationMs = 200000L,
        sizeBytes = 4000000L,
        dateModified = 1000L,
        dateAdded = 1000L,
        relativePath = "Music/MidnightEchoes.mp3",
        titleNormalized = "midnight echoes",
        artistNormalized = "solaris",
        albumNormalized = "deep space",
        isMissing = false,
        lastSeenTimestamp = 1000L
    )

    private class FakeSearchTrackDao(
        val storedTracks: MutableList<TrackEntity>
    ) : TrackDao {
        override fun observeAllActiveTracks(): Flow<List<TrackEntity>> = flowOf(storedTracks.filter { !it.isMissing })
        override suspend fun getAllActiveTracks(): List<TrackEntity> = storedTracks.filter { !it.isMissing }
        override suspend fun getAllStoredTracks(): List<TrackEntity> = storedTracks.toList()
        override suspend fun getTrackById(trackId: Long): TrackEntity? = storedTracks.find { it.trackId == trackId }
        override suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity> = storedTracks.filter { trackIds.contains(it.trackId) }
        override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity? = storedTracks.find { it.mediaStoreId == mediaStoreId }
        override fun searchTracks(query: String): Flow<List<TrackEntity>> {
            val q = query.trim().lowercase()
            return flowOf(storedTracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) })
        }
        override fun observeRecentlyAddedTracks(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
        override suspend fun getRecentlyAddedTracks(limit: Int): List<TrackEntity> = emptyList()
        override fun observeAllAlbums(): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
        override suspend fun getAlbumById(albumId: Long): TrackDao.AlbumSummary? = null
        override fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>> = flowOf(emptyList())
        override suspend fun getTracksForAlbum(albumId: Long): List<TrackEntity> = emptyList()
        override fun observeAllArtists(): Flow<List<TrackDao.ArtistSummary>> = flowOf(emptyList())
        override fun observeAlbumsForArtist(artistNormalized: String): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
        override fun observeTracksForArtist(artistNormalized: String): Flow<List<TrackEntity>> = flowOf(emptyList())
        override suspend fun getTracksForArtist(artistNormalized: String): List<TrackEntity> = emptyList()
        override suspend fun findReLinkCandidates(minDurationMs: Long, maxDurationMs: Long, sizeBytes: Long): List<TrackEntity> = emptyList()
        override suspend fun insertTrack(track: TrackEntity): Long = track.trackId
        override suspend fun insertTracks(tracks: List<TrackEntity>): List<Long> = tracks.map { it.trackId }
        override suspend fun updateTrack(track: TrackEntity) {}
        override suspend fun updateTracks(tracks: List<TrackEntity>) {}
        override suspend fun reLinkTrack(trackId: Long, newMediaStoreId: Long, timestamp: Long) {}
        override suspend fun markTracksMissing(trackIds: List<Long>) {}
        override suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long) {}
        override suspend fun purgeOrphanedTracks(purgeCutoffTimestamp: Long): Int = 0
    }

    private class FakeSearchFavoriteDao : FavoriteDao {
        override fun observeFavoriteTrackIds(): Flow<List<Long>> = flowOf(emptyList())
        override suspend fun getFavoriteTrackIds(): List<Long> = emptyList()
        override fun observeFavoriteTrackEntities(): Flow<List<TrackEntity>> = flowOf(emptyList())
        override suspend fun getFavoriteTrackEntities(): List<TrackEntity> = emptyList()
        override fun observeIsFavorite(trackId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun isFavorite(trackId: Long): Boolean = false
        override suspend fun addFavorite(favorite: FavoriteTrackEntity) {}
        override suspend fun removeFavorite(trackId: Long) {}
    }

    private class FakeSearchPlayEventDao : PlayEventDao {
        override suspend fun insertEvent(event: PlayEventEntity): Long = 1L
        override fun observeRecentlyPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
        override suspend fun getRecentlyPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
        override fun observeMostPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
        override suspend fun getMostPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
    }

    private class FakeSearchPlaylistDao(private val fakeTrackDao: FakeSearchTrackDao) : PlaylistDao {
        private var nextId = 1L
        val playlists = mutableListOf<PlaylistEntity>()
        val playlistTracks = mutableListOf<PlaylistTrackEntity>()
        private val playlistsFlow = MutableStateFlow<List<PlaylistDao.PlaylistSummary>>(emptyList())

        private fun updateFlow() {
            val withCount = playlists.map { p ->
                val count = playlistTracks.count { it.playlistId == p.playlistId }
                PlaylistDao.PlaylistSummary(p.playlistId, p.name, p.createdAt, p.updatedAt, count)
            }
            playlistsFlow.value = withCount
        }

        override fun observeAllPlaylistsWithCount(): Flow<List<PlaylistDao.PlaylistSummary>> {
            updateFlow()
            return playlistsFlow
        }

        override fun observeTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> = flowOf(emptyList())

        override suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? =
            playlists.find { it.playlistId == playlistId }

        override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
            val id = nextId++
            val saved = playlist.copy(playlistId = id)
            playlists.add(saved)
            updateFlow()
            return id
        }

        override suspend fun updatePlaylist(playlist: PlaylistEntity) {
            val idx = playlists.indexOfFirst { it.playlistId == playlist.playlistId }
            if (idx >= 0) {
                playlists[idx] = playlist
                updateFlow()
            }
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            playlists.removeIf { it.playlistId == playlistId }
            playlistTracks.removeIf { it.playlistId == playlistId }
            updateFlow()
        }

        override suspend fun addTrackToPlaylist(entry: PlaylistTrackEntity): Long {
            val exists = playlistTracks.any { it.playlistId == entry.playlistId && it.trackId == entry.trackId }
            if (exists) return -1L
            playlistTracks.add(entry)
            updateFlow()
            return 1L
        }

        override suspend fun addTracksToPlaylist(entries: List<PlaylistTrackEntity>) {
            for (entry in entries) {
                addTrackToPlaylist(entry)
            }
        }

        override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
            playlistTracks.removeIf { it.playlistId == playlistId && it.trackId == trackId }
            updateFlow()
        }

        override suspend fun removeTrackFromAllPlaylists(trackId: Long) {
            playlistTracks.removeIf { it.trackId == trackId }
            updateFlow()
        }

        override suspend fun getTracksForPlaylist(playlistId: Long): List<TrackEntity> {
            val trackIds = playlistTracks.filter { it.playlistId == playlistId }.sortedBy { it.position }.map { it.trackId }
            return trackIds.mapNotNull { id -> fakeTrackDao.storedTracks.find { it.trackId == id && !it.isMissing } }
        }

        override suspend fun getPlaylistTrackEntries(playlistId: Long): List<PlaylistTrackEntity> =
            playlistTracks.filter { it.playlistId == playlistId }.sortedBy { it.position }

        override suspend fun getMaxPosition(playlistId: Long): Int? =
            playlistTracks.filter { it.playlistId == playlistId }.maxOfOrNull { it.position }

        override suspend fun insertOrUpdatePlaylistTracks(entries: List<PlaylistTrackEntity>) {
            for (entry in entries) {
                val idx = playlistTracks.indexOfFirst { it.playlistId == entry.playlistId && it.trackId == entry.trackId }
                if (idx >= 0) {
                    playlistTracks[idx] = entry
                } else {
                    playlistTracks.add(entry)
                }
            }
            updateFlow()
        }

        override suspend fun updateTrackPositions(entries: List<PlaylistTrackEntity>) {
            insertOrUpdatePlaylistTracks(entries)
        }
    }

    private class FakeNetworkMonitor(val online: Boolean) : NetworkMonitor {
        override val isOnline: Flow<Boolean> = flowOf(online)
    }

    private lateinit var trackRepository: TrackRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var playbackFacade: PlaybackFacade
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        val tracks = mutableListOf(fakeTrack1)
        val dummyContext = object : ContextWrapper(null) {}
        val fakeTrackDao = FakeSearchTrackDao(tracks)
        val fakeFavDao = FakeSearchFavoriteDao()
        val fakePlayDao = FakeSearchPlayEventDao()
        val fakePlaylistDao = FakeSearchPlaylistDao(fakeTrackDao)

        trackRepository = TrackRepository(
            trackDao = fakeTrackDao,
            favoriteDao = fakeFavDao,
            syncEngine = SyncEngine(
                scanner = MediaStoreScanner(dummyContext),
                trackDao = fakeTrackDao
            ),
            playEventDao = fakePlayDao
        )
        playlistRepository = PlaylistRepository(fakePlaylistDao, fakeTrackDao, fakeFavDao)

        playbackFacade = PlaybackFacade(
            context = dummyContext,
            trackRepository = trackRepository
        )
        viewModel = SearchViewModel(
            trackRepository = trackRepository,
            playbackFacade = playbackFacade,
            networkConnectivityMonitor = FakeNetworkMonitor(false),
            playlistRepository = playlistRepository
        )
    }

    @Test
    fun voiceSearch_updatesQuery_andFindsMatchingLocalTracks() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onSearchQueryChanged("Solaris")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Solaris", state.searchQuery)
        assertEquals(1, state.matchingTracks.size)
        assertEquals("Midnight Echoes", state.matchingTracks[0].title)
    }

    @Test
    fun createPlaylistAndAddTrack_createsPlaylist_andEnrollsTrack() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.playlists.collect {} }
        advanceUntilIdle()

        val track = Track(
            id = 1L,
            mediaStoreId = 101L,
            title = "Midnight Echoes",
            artist = "Solaris",
            album = "Deep Space",
            albumId = 10L,
            durationMs = 200000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            isMissing = false,
            isFavorite = false
        )

        viewModel.createPlaylistAndAddTrack("Night Drives", track)
        advanceUntilIdle()

        val playlists = viewModel.playlists.value
        assertEquals(1, playlists.size)
        assertEquals("Night Drives", playlists[0].name)
        assertEquals(1, playlists[0].trackCount)
    }
}

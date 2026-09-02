package com.kaon.music.feature.library

import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.PlaylistDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.PlaylistRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.playback.PlaybackFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakePlaylistDao: FakeVmPlaylistDao
    private lateinit var fakeTrackDao: FakeVmTrackDao
    private lateinit var fakeFavoriteDao: FakeVmFavoriteDao
    private lateinit var fakePlayEventDao: FakeVmPlayEventDao
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var trackRepository: TrackRepository
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        fakeTrackDao = FakeVmTrackDao()
        fakePlaylistDao = FakeVmPlaylistDao(fakeTrackDao)
        fakeFavoriteDao = FakeVmFavoriteDao()
        fakePlayEventDao = FakeVmPlayEventDao()

        // Insert sample tracks
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 1L,
                mediaStoreId = 101L,
                title = "Song A",
                artist = "Artist 1",
                album = "Album 1",
                albumId = 1L,
                durationMs = 180000L,
                sizeBytes = 4000000L,
                dateModified = 1000L,
                dateAdded = 1000L,
                relativePath = "Music/SongA.mp3",
                titleNormalized = "song a",
                artistNormalized = "artist 1",
                albumNormalized = "album 1",
                isMissing = false,
                lastSeenTimestamp = 1000L
            )
        )
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 2L,
                mediaStoreId = 102L,
                title = "Song B",
                artist = "Artist 2",
                album = "Album 2",
                albumId = 2L,
                durationMs = 200000L,
                sizeBytes = 5000000L,
                dateModified = 1000L,
                dateAdded = 1000L,
                relativePath = "Music/SongB.mp3",
                titleNormalized = "song b",
                artistNormalized = "artist 2",
                albumNormalized = "album 2",
                isMissing = false,
                lastSeenTimestamp = 1000L
            )
        )

        playlistRepository = PlaylistRepository(fakePlaylistDao, fakeTrackDao, fakeFavoriteDao)

        val dummyContext = object : android.content.ContextWrapper(null) {}

        // Mock/Fake TrackRepository with basic active tracks
        trackRepository = TrackRepository(
            trackDao = fakeTrackDao,
            favoriteDao = fakeFavoriteDao,
            syncEngine = SyncEngine(
                scanner = com.kaon.music.core.data.sync.MediaStoreScanner(dummyContext),
                trackDao = fakeTrackDao
            ),
            playEventDao = fakePlayEventDao
        )

        val playbackFacade = PlaybackFacade(
            context = dummyContext,
            trackRepository = trackRepository
        )

        viewModel = LibraryViewModel(
            trackRepository = trackRepository,
            playbackFacade = playbackFacade,
            playlistRepository = playlistRepository
        )
    }

    @Test
    fun `create playlist flow updates state and surfaces in uiState`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createPlaylist("Midnight Chill")
        advanceUntilIdle()

        val playlists = viewModel.uiState.value.playlists
        assertEquals(1, playlists.size)
        assertEquals("Midnight Chill", playlists.first().name)
        assertEquals(0, playlists.first().trackCount)
    }

    @Test
    fun `add track to playlist updates count and playlist tracks stream`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createPlaylist("Focus Beats")
        advanceUntilIdle()

        val playlist = viewModel.uiState.value.playlists.first()
        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.playlistTracks.size)

        viewModel.addTrackToPlaylist(playlist.id, 1L, "Song A", playlist.name)
        advanceUntilIdle()

        val updatedPlaylists = viewModel.uiState.value.playlists
        assertEquals(1, updatedPlaylists.first().trackCount)

        val playlistTracks = viewModel.uiState.value.playlistTracks
        assertEquals(1, playlistTracks.size)
        assertEquals(1L, playlistTracks.first().id)
        assertEquals("Song A", playlistTracks.first().title)
    }

    @Test
    fun `reorder playlist tracks updates positions in uiState`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createPlaylist("Running Mix")
        advanceUntilIdle()

        val playlist = viewModel.uiState.value.playlists.first()
        viewModel.selectPlaylist(playlist)

        viewModel.addTrackToPlaylist(playlist.id, 1L, "Song A", playlist.name)
        viewModel.addTrackToPlaylist(playlist.id, 2L, "Song B", playlist.name)
        advanceUntilIdle()

        val initialTracks = viewModel.uiState.value.playlistTracks
        assertEquals(2, initialTracks.size)
        assertEquals(1L, initialTracks[0].id)
        assertEquals(2L, initialTracks[1].id)

        // Swap order
        viewModel.reorderPlaylistTracks(playlist.id, listOf(2L, 1L))
        advanceUntilIdle()

        val reorderedTracks = viewModel.uiState.value.playlistTracks
        assertEquals(2, reorderedTracks.size)
        assertEquals(2L, reorderedTracks[0].id)
        assertEquals(1L, reorderedTracks[1].id)
    }

    @Test
    fun `rename playlist updates name in list and selected playlist`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createPlaylist("Old Name")
        advanceUntilIdle()

        val playlist = viewModel.uiState.value.playlists.first()
        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        viewModel.renamePlaylist(playlist.id, "New Better Name")
        advanceUntilIdle()

        val updatedPlaylists = viewModel.uiState.value.playlists
        assertEquals("New Better Name", updatedPlaylists.first().name)
        assertEquals("New Better Name", viewModel.uiState.value.selectedPlaylist?.name)
    }

    @Test
    fun `delete playlist removes from list and resets selectedPlaylist`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createPlaylist("To Be Deleted")
        advanceUntilIdle()

        val playlist = viewModel.uiState.value.playlists.first()
        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.selectedPlaylist)

        viewModel.deletePlaylist(playlist.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.playlists.isEmpty())
        assertNull(viewModel.uiState.value.selectedPlaylist)
    }

    @Test
    fun `remove track from playlist updates track list and count`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createPlaylist("Workout")
        advanceUntilIdle()

        val playlist = viewModel.uiState.value.playlists.first()
        viewModel.selectPlaylist(playlist)

        viewModel.addTrackToPlaylist(playlist.id, 1L, "Song A", playlist.name)
        viewModel.addTrackToPlaylist(playlist.id, 2L, "Song B", playlist.name)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.playlistTracks.size)

        viewModel.removeTrackFromPlaylist(playlist.id, 1L)
        advanceUntilIdle()

        val remaining = viewModel.uiState.value.playlistTracks
        assertEquals(1, remaining.size)
        assertEquals(2L, remaining.first().id)
    }

    @Test
    fun `filter selection switches to PLAYLISTS filter correctly`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(LibraryFilter.TRACKS, viewModel.uiState.value.selectedFilter)

        viewModel.selectFilter(LibraryFilter.PLAYLISTS)
        advanceUntilIdle()

        assertEquals(LibraryFilter.PLAYLISTS, viewModel.uiState.value.selectedFilter)
    }
}

// ==================== Fake DAO Implementations for ViewModel Unit Tests ====================

private class FakeVmPlaylistDao(private val fakeTrackDao: FakeVmTrackDao) : PlaylistDao {
    private var nextId = 1L
    val playlists = mutableListOf<PlaylistEntity>()
    val playlistTracks = mutableListOf<PlaylistTrackEntity>()

    private val playlistsFlow = MutableStateFlow<List<PlaylistDao.PlaylistSummary>>(emptyList())
    private val tracksFlowMap = mutableMapOf<Long, MutableStateFlow<List<TrackEntity>>>()

    private fun updateFlow() {
        val withCount = playlists.map { p ->
            val count = playlistTracks.count { it.playlistId == p.playlistId }
            PlaylistDao.PlaylistSummary(p.playlistId, p.name, p.createdAt, p.updatedAt, count)
        }
        playlistsFlow.value = withCount

        for (p in playlists) {
            val trackIds = playlistTracks.filter { it.playlistId == p.playlistId }.sortedBy { it.position }.map { it.trackId }
            val tracks = trackIds.mapNotNull { id -> fakeTrackDao.storedTracks.find { it.trackId == id && !it.isMissing } }
            tracksFlowMap.getOrPut(p.playlistId) { MutableStateFlow(emptyList()) }.value = tracks
        }
    }

    override fun observeAllPlaylistsWithCount(): Flow<List<PlaylistDao.PlaylistSummary>> {
        updateFlow()
        return playlistsFlow
    }

    override fun observeTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> {
        val flow = tracksFlowMap.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
        return flow
    }

    fun updateTracksFlow(playlistId: Long, tracks: List<TrackEntity>) {
        val flow = tracksFlowMap.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
        flow.value = tracks
    }

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
        updateTracksFlow(playlistId, emptyList())
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

private class FakeVmFavoriteDao : FavoriteDao {
    val favorites = mutableSetOf<Long>()
    private val flow = MutableStateFlow<List<Long>>(emptyList())

    override fun observeFavoriteTrackIds(): Flow<List<Long>> {
        flow.value = favorites.toList()
        return flow
    }
    override suspend fun getFavoriteTrackIds(): List<Long> = favorites.toList()
    override fun observeFavoriteTrackEntities(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getFavoriteTrackEntities(): List<TrackEntity> = emptyList()
    override fun observeIsFavorite(trackId: Long): Flow<Boolean> = flowOf(favorites.contains(trackId))
    override suspend fun isFavorite(trackId: Long): Boolean = favorites.contains(trackId)
    override suspend fun addFavorite(favorite: FavoriteTrackEntity) {
        favorites.add(favorite.trackId)
        flow.value = favorites.toList()
    }
    override suspend fun removeFavorite(trackId: Long) {
        favorites.remove(trackId)
        flow.value = favorites.toList()
    }
}

private class FakeVmTrackDao : TrackDao {
    val storedTracks = mutableListOf<TrackEntity>()

    override fun observeAllActiveTracks(): Flow<List<TrackEntity>> = flowOf(storedTracks.filter { !it.isMissing })
    override suspend fun getAllActiveTracks(): List<TrackEntity> = storedTracks.filter { !it.isMissing }
    override suspend fun getAllStoredTracks(): List<TrackEntity> = storedTracks.toList()
    override suspend fun getTrackById(trackId: Long): TrackEntity? = storedTracks.find { it.trackId == trackId }
    override suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity> = storedTracks.filter { trackIds.contains(it.trackId) }
    override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity? = storedTracks.find { it.mediaStoreId == mediaStoreId }
    override fun searchTracks(query: String): Flow<List<TrackEntity>> = flowOf(emptyList())
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
    override suspend fun insertTrack(track: TrackEntity): Long {
        storedTracks.add(track)
        return track.trackId
    }
    override suspend fun insertTracks(tracks: List<TrackEntity>): List<Long> {
        storedTracks.addAll(tracks)
        return tracks.map { it.trackId }
    }
    override suspend fun updateTrack(track: TrackEntity) {}
    override suspend fun updateTracks(tracks: List<TrackEntity>) {}
    override suspend fun reLinkTrack(trackId: Long, newMediaStoreId: Long, timestamp: Long) {}
    override suspend fun markTracksMissing(trackIds: List<Long>) {}
    override suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long) {}
    override suspend fun purgeOrphanedTracks(purgeCutoffTimestamp: Long): Int = 0
}

private class FakeVmPlayEventDao : PlayEventDao {
    override suspend fun insertEvent(event: PlayEventEntity): Long = 1L
    override fun observeRecentlyPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getRecentlyPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
    override fun observeMostPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getMostPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
    override suspend fun clearAllEvents(): Int = 0
}

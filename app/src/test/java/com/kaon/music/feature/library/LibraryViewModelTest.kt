package com.kaon.music.feature.library
import androidx.paging.PagingSource
import com.kaon.music.core.data.db.dao.FakePagingInvalidator
import com.kaon.music.core.data.db.dao.TrackRow
import com.kaon.music.core.data.db.dao.toTrackRow
import androidx.paging.testing.asSnapshot

import com.kaon.music.core.data.db.dao.FakeTrackDaoBase
import com.kaon.music.core.data.db.dao.FakePlaylistDaoBase
import com.kaon.music.core.data.db.dao.FakePlayEventDaoBase
import com.kaon.music.core.data.db.dao.FakeFavoriteDaoBase
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


        val dummyContext = object : android.content.ContextWrapper(null) {}

        // Mock/Fake TrackRepository with basic active tracks
        trackRepository = TrackRepository(
            trackDao = fakeTrackDao,
            favoriteDao = fakeFavoriteDao,
            syncEngine = SyncEngine(
                scanner = com.kaon.music.core.data.sync.MediaStoreScanner(dummyContext),
                trackDao = fakeTrackDao
            ),
            playEventDao = fakePlayEventDao,
            // Repository mapping now runs on Dispatchers.Default in production (ARCHITECTURE.md
            // §5.4). runTest cannot advance the real Default dispatcher, so tests drive it on the
            // test scheduler — the same reason computeDispatcher is injected below.
            mappingDispatcher = mainDispatcherRule.testDispatcher
        )
        playlistRepository = PlaylistRepository(
            fakePlaylistDao,
            fakeTrackDao,
            fakeFavoriteDao,
            trackRepository = trackRepository,
            mappingDispatcher = mainDispatcherRule.testDispatcher
        )

        val playbackFacade = PlaybackFacade(
            context = dummyContext,
            trackRepository = trackRepository
        )

        val libraryController = com.kaon.music.core.data.repository.DefaultLibraryController(
            trackRepository = trackRepository,
            playlistRepository = playlistRepository
        )

        viewModel = LibraryViewModel(
            libraryController = libraryController,
            playerController = playbackFacade,
            // Keep the sort/filter pipeline on the test dispatcher; production runs it on
            // Dispatchers.Default, which the test scheduler cannot advance.
            computeDispatcher = mainDispatcherRule.testDispatcher
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
        // playlistStats is WhileSubscribed, so it stays at its initial value until something collects.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.playlistStats.collect {} }
        viewModel.createPlaylist("Focus Beats")
        advanceUntilIdle()

        val playlist = viewModel.uiState.value.playlists.first()
        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), viewModel.pagedPlaylistTracks.asSnapshot().map { it.id.value })

        viewModel.addTrackToPlaylist(playlist.id, com.kaon.music.core.data.model.localTrack(1L, "Song A"), "Song A", playlist.name)
        advanceUntilIdle()

        val updatedPlaylists = viewModel.uiState.value.playlists
        assertEquals(1, updatedPlaylists.first().trackCount)

        // The member list is paged now, so it is asserted through the paging snapshot rather than
        // through UI state, which no longer holds it.
        val playlistTracks = viewModel.pagedPlaylistTracks.asSnapshot()
        assertEquals(1, playlistTracks.size)
        assertEquals(1L, playlistTracks.first().id.value)
        assertEquals("Song A", playlistTracks.first().title)
        assertEquals(1, viewModel.playlistStats.value.trackCount)
    }

    @Test
    fun `reorder playlist tracks updates positions in uiState`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createPlaylist("Running Mix")
        advanceUntilIdle()

        val playlist = viewModel.uiState.value.playlists.first()
        viewModel.selectPlaylist(playlist)

        viewModel.addTrackToPlaylist(playlist.id, com.kaon.music.core.data.model.localTrack(1L, "Song A"), "Song A", playlist.name)
        viewModel.addTrackToPlaylist(playlist.id, com.kaon.music.core.data.model.localTrack(2L, "Song B"), "Song B", playlist.name)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.pagedPlaylistTracks.asSnapshot().map { it.id.value })

        // Swap order
        viewModel.reorderPlaylistTracks(playlist.id, listOf(2L, 1L))
        advanceUntilIdle()

        assertEquals(listOf(2L, 1L), viewModel.pagedPlaylistTracks.asSnapshot().map { it.id.value })
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

        viewModel.addTrackToPlaylist(playlist.id, com.kaon.music.core.data.model.localTrack(1L, "Song A"), "Song A", playlist.name)
        viewModel.addTrackToPlaylist(playlist.id, com.kaon.music.core.data.model.localTrack(2L, "Song B"), "Song B", playlist.name)
        advanceUntilIdle()

        assertEquals(2, viewModel.pagedPlaylistTracks.asSnapshot().size)

        viewModel.removeTrackFromPlaylist(playlist.id, 1L)
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.pagedPlaylistTracks.asSnapshot().map { it.id.value })
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

private class FakeVmPlaylistDao(private val fakeTrackDao: FakeVmTrackDao) : FakePlaylistDaoBase() {
    private var nextId = 1L
    val playlists = mutableListOf<PlaylistEntity>()
    val playlistTracks = mutableListOf<PlaylistTrackEntity>()

    private val playlistsFlow = MutableStateFlow<List<PlaylistDao.PlaylistSummary>>(emptyList())
    private val tracksFlowMap = mutableMapOf<Long, MutableStateFlow<List<TrackEntity>>>()
    private val statsFlowMap = mutableMapOf<Long, MutableStateFlow<PlaylistDao.PlaylistStats>>()

    // Stands in for Room's invalidation tracker: paged member lists must reload after a mutation.
    private val pagingInvalidator = FakePagingInvalidator()

    private fun membersOf(playlistId: Long): List<TrackEntity> {
        val trackIds = playlistTracks.filter { it.playlistId == playlistId }
            .sortedBy { it.position }
            .map { it.trackId }
        return trackIds.mapNotNull { id -> fakeTrackDao.storedTracks.find { it.trackId == id && !it.isMissing } }
    }

    private fun updateFlow() {
        val withCount = playlists.map { p ->
            val count = playlistTracks.count { it.playlistId == p.playlistId }
            PlaylistDao.PlaylistSummary(p.playlistId, p.name, p.createdAt, p.updatedAt, count)
        }
        playlistsFlow.value = withCount

        for (p in playlists) {
            val tracks = membersOf(p.playlistId)
            tracksFlowMap.getOrPut(p.playlistId) { MutableStateFlow(emptyList()) }.value = tracks
            statsFlowMap.getOrPut(p.playlistId) { MutableStateFlow(PlaylistDao.PlaylistStats(0, 0L)) }.value =
                PlaylistDao.PlaylistStats(tracks.size, tracks.sumOf { it.durationMs })
        }
        pagingInvalidator.invalidateAll()
    }

    override fun pagingTracksForPlaylist(playlistId: Long): PagingSource<Int, TrackRow> =
        pagingInvalidator.source { membersOf(playlistId).map { it.toTrackRow(isFavorite = false) } }

    override fun observePlaylistStats(playlistId: Long): Flow<PlaylistDao.PlaylistStats> =
        statsFlowMap.getOrPut(playlistId) { MutableStateFlow(PlaylistDao.PlaylistStats(0, 0L)) }

    override suspend fun countTracksInPlaylist(playlistId: Long): Int = membersOf(playlistId).size

    override suspend fun getOrderedTrackIds(playlistId: Long): List<Long> = membersOf(playlistId).map { it.trackId }

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

private class FakeVmFavoriteDao : FakeFavoriteDaoBase() {
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

private class FakeVmTrackDao : FakeTrackDaoBase() {
    val storedTracks = mutableListOf<TrackEntity>()

    override fun observeAllActiveTracks(): Flow<List<TrackEntity>> = flowOf(storedTracks.filter { !it.isMissing })
    override suspend fun getAllActiveTracks(): List<TrackEntity> = storedTracks.filter { !it.isMissing }
    override suspend fun getAllStoredTracks(): List<TrackEntity> = storedTracks.toList()
    override suspend fun getTrackById(trackId: Long): TrackEntity? = storedTracks.find { it.trackId == trackId }
    override suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity> = storedTracks.filter { trackIds.contains(it.trackId) }
    override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity? = storedTracks.find { it.mediaStoreId == mediaStoreId }
    override fun searchTracks(query: String, limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
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
    override suspend fun reLinkTrack(trackId: Long, newMediaStoreId: Long, newRelativePath: String?, timestamp: Long) {}
    override suspend fun markTracksMissing(trackIds: List<Long>) {}
    override suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long) {}
    override suspend fun purgeOrphanedTracks(purgeCutoffTimestamp: Long): Int = 0
}

private class FakeVmPlayEventDao : FakePlayEventDaoBase() {
    override suspend fun insertEvent(event: PlayEventEntity): Long = 1L
    override fun observeRecentlyPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getRecentlyPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
    override fun observeMostPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getMostPlayedTrackEntities(limit: Int): List<TrackEntity> = emptyList()
    override suspend fun clearAllEvents(): Int = 0
}

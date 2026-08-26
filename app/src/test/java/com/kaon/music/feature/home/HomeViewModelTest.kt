package com.kaon.music.feature.home

import android.content.ContextWrapper
import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.sync.MediaStoreScanner
import com.kaon.music.core.data.sync.SyncEngine
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeTrack1 = TrackEntity(
        trackId = 1L,
        mediaStoreId = 101L,
        title = "Track One",
        artist = "Artist A",
        album = "Album Alpha",
        albumId = 10L,
        durationMs = 200000L,
        sizeBytes = 4000000L,
        dateModified = 1000L,
        dateAdded = 1000L,
        relativePath = "Music/TrackOne.mp3",
        titleNormalized = "track one",
        artistNormalized = "artist a",
        albumNormalized = "album alpha",
        isMissing = false,
        lastSeenTimestamp = 1000L
    )

    private val fakeTrack2 = TrackEntity(
        trackId = 2L,
        mediaStoreId = 102L,
        title = "Track Two",
        artist = "Artist B",
        album = "Album Beta",
        albumId = 20L,
        durationMs = 180000L,
        sizeBytes = 3500000L,
        dateModified = 2000L,
        dateAdded = 2000L,
        relativePath = "Music/TrackTwo.mp3",
        titleNormalized = "track two",
        artistNormalized = "artist b",
        albumNormalized = "album beta",
        isMissing = false,
        lastSeenTimestamp = 2000L
    )

    private val fakeTrack3 = TrackEntity(
        trackId = 3L,
        mediaStoreId = 103L,
        title = "Track Three",
        artist = "Artist C",
        album = "Album Gamma",
        albumId = 30L,
        durationMs = 240000L,
        sizeBytes = 5000000L,
        dateModified = 3000L,
        dateAdded = 3000L,
        relativePath = "Music/TrackThree.mp3",
        titleNormalized = "track three",
        artistNormalized = "artist c",
        albumNormalized = "album gamma",
        isMissing = false,
        lastSeenTimestamp = 3000L
    )

    private class FakeHomeTrackDao(
        val storedTracks: MutableList<TrackEntity>
    ) : TrackDao {
        override fun observeAllActiveTracks(): Flow<List<TrackEntity>> = flowOf(storedTracks.filter { !it.isMissing })
        override suspend fun getAllActiveTracks(): List<TrackEntity> = storedTracks.filter { !it.isMissing }
        override suspend fun getAllStoredTracks(): List<TrackEntity> = storedTracks.toList()
        override suspend fun getTrackById(trackId: Long): TrackEntity? = storedTracks.find { it.trackId == trackId }
        override suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity> = storedTracks.filter { trackIds.contains(it.trackId) }
        override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity? = storedTracks.find { it.mediaStoreId == mediaStoreId }
        override fun searchTracks(query: String): Flow<List<TrackEntity>> = flowOf(emptyList())
        override fun observeRecentlyAddedTracks(limit: Int): Flow<List<TrackEntity>> =
            flowOf(storedTracks.sortedByDescending { it.dateAdded }.take(limit))
        override suspend fun getRecentlyAddedTracks(limit: Int): List<TrackEntity> =
            storedTracks.sortedByDescending { it.dateAdded }.take(limit)
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

    private class FakeHomeFavoriteDao(
        val favorites: MutableSet<Long>
    ) : FavoriteDao {
        private val flow = MutableStateFlow<List<Long>>(favorites.toList())

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

    private class FakeHomePlayEventDao(
        val mostPlayed: List<TrackEntity>,
        val recentlyPlayed: List<TrackEntity>
    ) : PlayEventDao {
        override suspend fun insertEvent(event: PlayEventEntity): Long = 1L
        override fun observeRecentlyPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> =
            flowOf(recentlyPlayed.take(limit))
        override suspend fun getRecentlyPlayedTrackEntities(limit: Int): List<TrackEntity> =
            recentlyPlayed.take(limit)
        override fun observeMostPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> =
            flowOf(mostPlayed.take(limit))
        override suspend fun getMostPlayedTrackEntities(limit: Int): List<TrackEntity> =
            mostPlayed.take(limit)
    }

    private lateinit var trackRepository: TrackRepository
    private lateinit var playbackFacade: PlaybackFacade
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        val tracks = mutableListOf(fakeTrack1, fakeTrack2, fakeTrack3)
        val dummyContext = object : ContextWrapper(null) {}
        val fakeTrackDao = FakeHomeTrackDao(tracks)
        val fakeFavDao = FakeHomeFavoriteDao(mutableSetOf(1L))
        val fakePlayDao = FakeHomePlayEventDao(
            mostPlayed = listOf(fakeTrack2, fakeTrack1),
            recentlyPlayed = listOf(fakeTrack3)
        )

        trackRepository = TrackRepository(
            trackDao = fakeTrackDao,
            favoriteDao = fakeFavDao,
            syncEngine = SyncEngine(
                scanner = MediaStoreScanner(dummyContext),
                trackDao = fakeTrackDao
            ),
            playEventDao = fakePlayDao
        )

        playbackFacade = PlaybackFacade(
            context = dummyContext,
            trackRepository = trackRepository
        )
        viewModel = HomeViewModel(trackRepository, playbackFacade)
    }

    @Test
    fun homeFeed_combinesRealStreams_andSurfacesDynamicData() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.heavyRotationTracks.size)
        assertEquals("Track Two", state.heavyRotationTracks[0].displayTitle)
        assertEquals(1, state.recentTracks.size)
        assertEquals("Track Three", state.recentTracks[0].displayTitle)
        assertEquals(3, state.recentlyAddedTracks.size)

        // Your Mix should have aggregated the tracks
        assertTrue("Your Mix should contain tracks", state.yourMixTracks.isNotEmpty())
        assertEquals(2, state.yourMixTracks.size)
    }
}

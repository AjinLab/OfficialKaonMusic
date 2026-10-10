package com.kaon.music.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.PlayerController
import com.kaon.music.core.policy.LibraryPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val yourMixTracks: List<Track> = emptyList(),
    val heavyRotationTracks: List<Track> = emptyList(),
    val recentlyAddedTracks: List<Track> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val recentArtists: List<Artist> = emptyList(),
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false
)

/**
 * Home feed state holder.
 *
 * Interacts with subsystems strictly through capability controllers ([LibraryController], [PlayerController]).
 */
class HomeViewModel(
    private val libraryController: LibraryController,
    private val playerController: PlayerController,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val mixSeed: Long = savedStateHandle.get<Long>(KEY_MIX_SEED)
        ?: System.currentTimeMillis().also { savedStateHandle[KEY_MIX_SEED] = it }

    private val mixSeedFlow: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_MIX_SEED, mixSeed)

    private data class FeedExtras(
        val albums: List<Album>,
        val artists: List<Artist>,
        val seed: Long
    )

    private val feedExtras = combine(
        libraryController.observeRecentAlbums(limit = ROW_LIMIT),
        libraryController.observeRecentArtists(limit = ROW_LIMIT),
        mixSeedFlow
    ) { albums, artists, seed -> FeedExtras(albums, artists, seed) }

    private val libraryFeed = combine(
        libraryController.observeRecentlyPlayedTracks(limit = RECENT_LIMIT),
        libraryController.observeMostPlayedTracks(limit = MOST_PLAYED_LIMIT),
        libraryController.observeRecentlyAddedTracks(limit = RECENT_LIMIT),
        libraryController.pagedFavoriteSample(limit = FAVORITE_LIMIT),
        feedExtras
    ) { recentTracks, mostPlayedTracks, recentlyAddedTracks, favoriteTracks, extras ->
        HomeUiState(
            yourMixTracks = LibraryPolicy.buildYourMix(
                mostPlayed = mostPlayedTracks,
                allTracks = recentlyAddedTracks,
                seed = extras.seed
            ),
            heavyRotationTracks = mostPlayedTracks.take(ROW_TRACK_LIMIT),
            recentlyAddedTracks = recentlyAddedTracks,
            favoriteTracks = favoriteTracks,
            recentTracks = recentTracks,
            recentAlbums = extras.albums,
            recentArtists = extras.artists
        )
    }.flowOn(computeDispatcher)

    val uiState: StateFlow<HomeUiState> = combine(
        libraryFeed,
        playerController.nowPlaying
            .map { it.currentTrack?.id?.value to it.isPlaying }
            .distinctUntilChanged()
    ) { feed, (activeTrackId, isPlaying) ->
        feed.copy(activeTrackId = activeTrackId, isPlaying = isPlaying)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun refreshMix() {
        savedStateHandle[KEY_MIX_SEED] = System.currentTimeMillis()
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val activeQueue = queue ?: listOf(track)
        playerController.playTrack(track, activeQueue)
    }

    fun playYourMix() {
        val tracks = uiState.value.yourMixTracks
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, startIndex = 0)
        }
    }

    fun playHeavyRotation() {
        val tracks = uiState.value.heavyRotationTracks
        if (tracks.isNotEmpty()) {
            playerController.playQueue(LibraryPolicy.shuffled(tracks, mixSeed), startIndex = 0)
        }
    }

    fun playRecentlyAdded() {
        val tracks = uiState.value.recentlyAddedTracks
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, startIndex = 0)
        }
    }

    fun playRecentlyPlayed() {
        val tracks = uiState.value.recentTracks
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, startIndex = 0)
        }
    }

    fun playFavorites() {
        val tracks = uiState.value.favoriteTracks
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, startIndex = 0)
        }
    }

    private companion object {
        const val KEY_MIX_SEED = "home.mixSeed"
        const val ROW_LIMIT = 10
        const val ROW_TRACK_LIMIT = 15
        const val RECENT_LIMIT = 20
        const val MOST_PLAYED_LIMIT = 50
        const val FAVORITE_LIMIT = 20
    }
}

package com.kaon.music.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.core.policy.LibraryPolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * ARCHITECTURE.md §3.2: observes [PlaybackFacade.nowPlaying], never `progress`. The previous version
 * combined the whole playback state — including the 500 ms position tick — with seven library flows,
 * so every tick rebuilt the entire feed.
 */
class HomeViewModel(
    private val trackRepository: TrackRepository,
    private val playbackFacade: PlaybackFacade,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    /**
     * Seeds the mix shuffle. Persisted so the feed is stable across configuration change, and bumped
     * only by [refreshMix]; a clock-based seed would reintroduce the non-deterministic feed.
     */
    private val mixSeed: Long = savedStateHandle.get<Long>(KEY_MIX_SEED)
        ?: System.currentTimeMillis().also { savedStateHandle[KEY_MIX_SEED] = it }

    private val mixSeedFlow: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_MIX_SEED, mixSeed)

    private val libraryFeed = combine(
        trackRepository.observeRecentlyPlayedTracks(limit = 15),
        trackRepository.observeMostPlayedTracks(limit = 50),
        trackRepository.observeRecentlyAddedTracks(limit = 15),
        trackRepository.observeFavoriteTracks(),
        trackRepository.observeAllAlbums(),
        trackRepository.observeAllArtists(),
        mixSeedFlow
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val recentTracks = args[0] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val mostPlayedTracks = args[1] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val recentlyAddedTracks = args[2] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val favoriteTracks = args[3] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val allAlbums = args[4] as List<Album>
        @Suppress("UNCHECKED_CAST")
        val allArtists = args[5] as List<Artist>
        val seed = args[6] as Long

        // The mix draws from most-played, falling back to recently added rather than the whole
        // library: loading every track just to pick 50 was the only reason this ViewModel observed
        // the full library, and it held a second copy of it in UI state.
        HomeUiState(
            yourMixTracks = LibraryPolicy.buildYourMix(
                mostPlayed = mostPlayedTracks,
                allTracks = recentlyAddedTracks,
                seed = seed
            ),
            heavyRotationTracks = mostPlayedTracks.take(15),
            recentlyAddedTracks = recentlyAddedTracks,
            favoriteTracks = favoriteTracks,
            recentTracks = recentTracks,
            recentAlbums = allAlbums.take(6),
            recentArtists = allArtists.take(6)
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        libraryFeed,
        playbackFacade.nowPlaying.map { it.currentTrack?.id to it.isPlaying }
    ) { feed, (activeTrackId, isPlaying) ->
        feed.copy(activeTrackId = activeTrackId, isPlaying = isPlaying)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    /** Regenerates the mix. The only sanctioned way to change the shuffle order. */
    fun refreshMix() {
        savedStateHandle[KEY_MIX_SEED] = System.currentTimeMillis()
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val activeQueue = queue ?: listOf(track)
        playbackFacade.playTrack(track, activeQueue)
    }

    fun playYourMix() {
        val tracks = uiState.value.yourMixTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(tracks, startIndex = 0)
        }
    }

    fun playHeavyRotation() {
        val tracks = uiState.value.heavyRotationTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(LibraryPolicy.shuffled(tracks, mixSeed), startIndex = 0)
        }
    }

    fun playRecentlyAdded() {
        val tracks = uiState.value.recentlyAddedTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(tracks, startIndex = 0)
        }
    }

    fun playFavorites() {
        val tracks = uiState.value.favoriteTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(LibraryPolicy.shuffled(tracks, mixSeed), startIndex = 0)
        }
    }

    private companion object {
        const val KEY_MIX_SEED = "home.mixSeed"
    }
}

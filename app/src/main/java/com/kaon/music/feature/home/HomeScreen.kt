package com.kaon.music.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.feature.home.component.HomeHeader
import com.kaon.music.feature.home.component.RecentlyPlayedSection
import com.kaon.music.feature.home.component.YourMixSection

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    bottomPadding: PaddingValues,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSeeAllRecentlyPlayed: () -> Unit,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
            .padding(bottom = bottomPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            HomeHeader(onSettingsClick = onSettingsClick)
        }

        item {
            YourMixSection(
                yourMixTracks = uiState.yourMixTracks,
                heavyRotationCount = uiState.heavyRotationTracks.size,
                recentlyAddedCount = uiState.recentlyAddedTracks.size,
                favoriteCount = uiState.favoriteTracks.size,
                onPlayYourMix = viewModel::playYourMix,
                onPlayHeavyRotation = viewModel::playHeavyRotation,
                onPlayRecentlyAdded = viewModel::playRecentlyAdded,
                onPlayFavorites = viewModel::playFavorites
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            RecentlyPlayedSection(
                recentTracks = uiState.recentTracks,
                recentAlbums = uiState.recentAlbums,
                recentArtists = uiState.recentArtists,
                onTrackClick = { track -> viewModel.playTrack(track, uiState.recentTracks) },
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onSeeAllClick = onSeeAllRecentlyPlayed,
                onExploreMusicClick = onSeeAllRecentlyPlayed
            )
        }
    }
}

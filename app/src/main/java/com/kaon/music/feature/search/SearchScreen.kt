package com.kaon.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.component.TrackItem
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary
import com.kaon.music.feature.search.component.GenreCard

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    bottomPadding: PaddingValues,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
            .padding(bottom = bottomPadding.calculateBottomPadding())
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = KaonTextPrimary
            )

            IconButton(onClick = { /* Voice search trigger */ }) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = KaonTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Search Text Field
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            placeholder = {
                Text(
                    text = "Artists, songs, or podcasts",
                    color = KaonTextTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = KaonTextSecondary
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = KaonTextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = KaonSurfaceElevated,
                unfocusedContainerColor = KaonSurfaceElevated,
                disabledContainerColor = KaonSurfaceElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = KaonTextPrimary,
                unfocusedTextColor = KaonTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.searchQuery.isBlank()) {
            // Browse All Genres Grid
            Text(
                text = "Browse All",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = KaonTextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.genres, key = { it.name }) { genre ->
                    GenreCard(
                        genre = genre,
                        onClick = { viewModel.onGenreSelected(genre) }
                    )
                }
            }
        } else {
            // Live Search Results
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Artists row
                if (uiState.matchingArtists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Artists",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.matchingArtists, key = { "artist_${it.artistId}_${it.name}" }) { artist ->
                                Column(
                                    modifier = Modifier
                                        .width(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onArtistClick(artist) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = CircleShape,
                                        color = KaonSurfaceElevated
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = KaonPrimary,
                                            modifier = Modifier.padding(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = artist.displayName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Albums row
                if (uiState.matchingAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.matchingAlbums, key = { "album_${it.albumId}" }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAlbumClick(album) }
                                ) {
                                    ArtworkImage(
                                        albumId = album.albumId,
                                        sizeBucket = SizeBucket.FULL,
                                        modifier = Modifier.size(110.dp),
                                        cornerRadius = 10.dp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = album.displayTitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Songs list
                if (uiState.matchingTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Songs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(
                        items = uiState.matchingTracks,
                        key = { _, track -> track.id }
                    ) { _, track ->
                        TrackItem(
                            track = track,
                            isPlaying = uiState.isPlaying,
                            isCurrent = track.id == uiState.activeTrackId,
                            onClick = { viewModel.playTrack(track) },
                            onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                            onPlayNext = { viewModel.playNext(track) },
                            onAddToQueue = { viewModel.addToQueue(track) }
                        )
                    }
                } else if (uiState.matchingArtists.isEmpty() && uiState.matchingAlbums.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No results found for \"${uiState.searchQuery}\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = KaonTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

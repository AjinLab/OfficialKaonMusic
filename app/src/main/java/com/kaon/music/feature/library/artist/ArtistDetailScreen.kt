package com.kaon.music.feature.library.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.TrackItem
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.feature.library.album.AlbumGridItem

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    albums: List<Album>,
    tracks: List<Track>,
    activeTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit = {},
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
            .padding(bottom = bottomPadding.calculateBottomPadding())
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = KaonTextPrimary
                )
            }
            Text(
                text = artist.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = KaonTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                // Header Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape = CircleShape,
                        color = KaonSurfaceElevated
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = KaonPrimary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = artist.displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${albums.size} ${if (albums.size == 1) "album" else "albums"} • ${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaonTextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onPlayAll,
                        colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary),
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All")
                    }
                }
            }

            // Albums Row
            if (albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        items(albums, key = { it.albumId }) { album ->
                            AlbumGridItem(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                modifier = Modifier.width(150.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Tracks Header
            item {
                Text(
                    text = "Tracks",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = KaonTextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Tracks List
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id }
            ) { _, track ->
                TrackItem(
                    track = track,
                    isPlaying = isPlaying,
                    isCurrent = track.id == activeTrackId,
                    onClick = { onTrackClick(track) },
                    onFavoriteToggle = { onFavoriteToggle(track.id) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) }
                )
            }
        }
    }
}

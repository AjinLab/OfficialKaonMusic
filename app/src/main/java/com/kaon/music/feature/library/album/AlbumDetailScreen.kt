package com.kaon.music.feature.library.album

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.component.TrackItem
import com.kaon.music.core.designsystem.component.formatDuration
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary

@Composable
fun AlbumDetailScreen(
    album: Album,
    tracks: List<Track>,
    activeTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
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
                text = album.displayTitle,
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
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        ArtworkImage(
                            albumId = album.albumId,
                            sizeBucket = SizeBucket.FULL,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 16.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = album.displayTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${album.displayArtist}${if (album.year > 0) " • ${album.year}" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                        color = KaonTextSecondary
                    )

                    Text(
                        text = "${tracks.size} ${if (tracks.size == 1) "track" else "tracks"} • ${formatDuration(album.totalDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaonTextTertiary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Play and Shuffle Header Actions (M3-D2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onPlayAlbum,
                            colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play")
                        }

                        OutlinedButton(
                            onClick = onShuffleAlbum,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = KaonPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = KaonTextPrimary)
                        }
                    }
                }
            }

            // Track List in Disc/Track Order (M3-D2: Never alphabetical!)
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

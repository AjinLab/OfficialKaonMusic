package com.kaon.music.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.theme.KaonHeartRed
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary
import java.util.Locale

@Composable
fun TrackItem(
    track: Track,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) KaonPrimary else KaonTextPrimary,
        label = "TitleColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            albumId = track.albumId,
            artworkUri = track.contentUri.takeIf { track.source == "YOUTUBE" },
            sizeBucket = SizeBucket.THUMBNAIL,
            modifier = Modifier.size(52.dp),
            cornerRadius = 8.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${track.displayArtist} • ${track.displayAlbum}",
                style = MaterialTheme.typography.bodyMedium,
                color = KaonTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = KaonTextTertiary
        )

        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (track.isFavorite) KaonHeartRed else KaonTextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        if (onPlayNext != null || onAddToQueue != null || onAddToPlaylist != null) {
            var isMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track options",
                        tint = KaonTextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (track.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                        onClick = {
                            isMenuExpanded = false
                            onFavoriteToggle()
                        }
                    )
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            onClick = {
                                isMenuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            onClick = {
                                isMenuExpanded = false
                                onPlayNext()
                            }
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            onClick = {
                                isMenuExpanded = false
                                onAddToQueue()
                            }
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

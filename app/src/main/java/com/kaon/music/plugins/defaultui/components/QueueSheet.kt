package com.kaon.music.plugins.defaultui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkSizes
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.media.artwork.ArtworkRequest
import com.kaon.music.media.model.Song
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playerController: PlayerController,
    artworkRepository: ArtworkRepository,
    onDismiss: () -> Unit
) {
    val queue by playerController.queue.collectAsState()
    val currentSong by playerController.currentSong.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { index, song -> "${song.id}_$index" }
                ) { index, song ->
                    QueueItem(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        onClick = {
                            playerController.play(index)
                        },
                        onRemove = {
                            // Implement remove if supported by controller
                        },
                        artworkRepository = artworkRepository
                    )
                }
            }
        }
    }
}

@Composable
fun QueueItem(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    artworkRepository: ArtworkRepository,
    modifier: Modifier = Modifier
) {
    val initialArtwork = remember(song.id, song.artworkHash) {
        val request = ArtworkRequest(song.albumId, ArtworkSizes.Thumbnail, song)
        artworkRepository.getCachedArtwork(request)?.let { Artwork.BitmapSource(it) } ?: Artwork.None
    }
    val artwork by produceState<Artwork>(initialValue = initialArtwork, key1 = song.id, key2 = song.artworkHash) {
        if (initialArtwork is Artwork.None) {
            val request = ArtworkRequest(song.albumId, ArtworkSizes.Thumbnail, song)
            val bitmap = artworkRepository.load(request)
            value = if (bitmap != null) Artwork.BitmapSource(bitmap) else Artwork.None
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            artwork = artwork,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            size = ArtworkSizes.Thumbnail
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.kaon.music.plugins.defaultui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.media.model.Song
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.plugins.defaultui.components.ArtworkImage

import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.kaon.music.media.artwork.ArtworkRequest
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkSizes


@Composable
fun SongListItem(
    song: Song,
    index: Int,
    onClick: () -> Unit,
    artworkRepository: ArtworkRepository,
    onFavoriteClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
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
    val favoriteScale by animateFloatAsState(
        targetValue = if (song.favorite) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "FavoriteScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index >= 0) {
            Text(
                text = remember(index) { formatIndex(index) },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.width(32.dp)
            )
        }

        ArtworkImage(
            artwork = artwork,
            modifier = Modifier.size(48.dp),
            cornerRadius = 8,
            size = ArtworkSizes.Thumbnail
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onFavoriteClick != null) {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (song.favorite) {
                        "Remove ${song.title} from liked songs"
                    } else {
                        "Add ${song.title} to liked songs"
                    },
                    tint = if (song.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.graphicsLayer {
                        scaleX = favoriteScale
                        scaleY = favoriteScale
                    }
                )
            }
        }
        
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "More options for ${song.title}",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

private fun formatIndex(index: Int): String {
    val displayIndex = index + 1
    return if (displayIndex < 10) "0$displayIndex" else displayIndex.toString()
}

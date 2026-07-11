package com.kaon.music.plugins.defaultui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.media.model.Album
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.plugins.defaultui.components.ArtworkImage

import com.kaon.music.media.artwork.ArtworkRepository

import androidx.compose.runtime.produceState
import com.kaon.music.media.artwork.ArtworkSizes

import com.kaon.music.media.artwork.ArtworkRequest

@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    artworkRepository: ArtworkRepository,
    modifier: Modifier = Modifier
) {
    val initialArtwork = remember(album.id) {
        val request = ArtworkRequest(album.id, ArtworkSizes.MiniPlayer)
        artworkRepository.getCachedArtwork(request)?.let { Artwork.BitmapSource(it) } ?: Artwork.None
    }
    val artwork by produceState<Artwork>(initialValue = initialArtwork, key1 = album.id) {
        if (initialArtwork is Artwork.None) {
            val request = ArtworkRequest(album.id, ArtworkSizes.MiniPlayer)
            val bitmap = artworkRepository.load(request)
            value = if (bitmap != null) Artwork.BitmapSource(bitmap) else Artwork.None
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        ArtworkImage(
            artwork = artwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 16,
            size = ArtworkSizes.MiniPlayer
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

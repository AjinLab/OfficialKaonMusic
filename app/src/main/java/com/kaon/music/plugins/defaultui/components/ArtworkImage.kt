package com.kaon.music.plugins.defaultui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaon.music.media.artwork.Artwork

import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.kaon.music.media.artwork.ArtworkSize

@Composable
fun ArtworkImage(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    size: ArtworkSize = com.kaon.music.media.artwork.ArtworkSizes.Original
) {
    val model = when (artwork) {
        is Artwork.FileSource -> artwork.file
        is Artwork.BitmapSource -> artwork.bitmap
        else -> null
    }

    val context = LocalContext.current
    val request = remember(model, size) {
        if (model == null) null
        else {
            ImageRequest.Builder(context)
                .data(model)
                .apply {
                    val px = size.pixels
                    if (px > 0) size(px)
                }
                .crossfade(true)
                .build()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = "Album Artwork",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

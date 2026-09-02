package com.kaon.music.core.designsystem.component

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kaon.music.app.KaonApplication
import com.kaon.music.core.artwork.ArtworkResolver
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonTrackPlaceholder

@Composable
fun ArtworkImage(
    modifier: Modifier = Modifier,
    albumId: Long = 0L,
    artworkUri: Uri? = null,
    model: Any? = null,
    track: Track? = null,
    title: String? = null,
    artist: String? = null,
    album: String? = null,
    artistName: String? = null,
    isArtist: Boolean = false,
    sizeBucket: SizeBucket = SizeBucket.THUMBNAIL,
    cornerRadius: Dp = if (isArtist) 50.dp else 8.dp,
    contentDescription: String? = if (isArtist) "Artist Photo" else "Album Artwork"
) {
    val context = LocalContext.current
    val app = context.applicationContext as? KaonApplication
    val metadataRepo = app?.container?.metadataRepository

    val effectiveArtist = (artistName ?: track?.artist ?: artist).orEmpty().trim()
    val effectiveAlbum = (track?.album ?: album).orEmpty().trim()
    val effectiveAlbumId = track?.albumId ?: albumId
    val effectiveArtworkUri = artworkUri ?: (track?.contentUri.takeIf { track?.source == "YOUTUBE" })

    val shape = if (isArtist && cornerRadius >= 20.dp) CircleShape else RoundedCornerShape(cornerRadius)

    val initialModel = when {
        model != null -> model
        effectiveArtworkUri != null -> effectiveArtworkUri
        effectiveAlbumId > 0 && !isArtist -> ArtworkResolver.getAlbumArtUri(effectiveAlbumId)
        else -> null
    }

    var resolvedModel by remember(model, effectiveArtworkUri, effectiveAlbumId, effectiveArtist, effectiveAlbum, isArtist) {
        mutableStateOf<Any?>(initialModel)
    }

    LaunchedEffect(model, effectiveArtworkUri, effectiveAlbumId, effectiveArtist, effectiveAlbum, isArtist) {
        if (model != null) {
            resolvedModel = model
            return@LaunchedEffect
        }
        if (effectiveArtworkUri != null) {
            resolvedModel = effectiveArtworkUri
            return@LaunchedEffect
        }

        if (isArtist) {
            if (effectiveArtist.isNotBlank() && metadataRepo != null) {
                val photoUrl = metadataRepo.getArtistPhotoUrl(effectiveArtist)
                if (!photoUrl.isNullOrBlank()) {
                    resolvedModel = photoUrl
                }
            }
        } else {
            if (effectiveAlbumId > 0) {
                val localUri = ArtworkResolver.getAlbumArtUri(effectiveAlbumId)
                resolvedModel = localUri
            } else if ((effectiveAlbum.isNotBlank() || effectiveArtist.isNotBlank()) && metadataRepo != null) {
                val coverUrl = metadataRepo.getAlbumCoverArtUrl(effectiveAlbum, effectiveArtist)
                if (!coverUrl.isNullOrBlank()) {
                    resolvedModel = coverUrl
                }
            }
        }
    }

    var onlineFallbackModel by remember(effectiveAlbumId, effectiveAlbum, effectiveArtist, isArtist) {
        mutableStateOf<String?>(null)
    }
    var fallbackAttempt by remember(effectiveAlbumId, effectiveAlbum, effectiveArtist, isArtist) {
        mutableStateOf(0)
    }

    // Active image model: explicit model -> resolved model -> online fallback
    val activeModel = model ?: effectiveArtworkUri ?: onlineFallbackModel ?: resolvedModel

    // Fetched when the primary (e.g. local MediaStore) image fails to load
    LaunchedEffect(fallbackAttempt) {
        if (fallbackAttempt == 0 || isArtist || metadataRepo == null) return@LaunchedEffect
        if (effectiveAlbum.isBlank() && effectiveArtist.isBlank()) return@LaunchedEffect
        val coverUrl = metadataRepo.getAlbumCoverArtUrl(effectiveAlbum, effectiveArtist)
        if (!coverUrl.isNullOrBlank()) {
            onlineFallbackModel = coverUrl
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(KaonTrackPlaceholder),
        contentAlignment = Alignment.Center
    ) {
        if (activeModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeModel)
                    .size(sizeBucket.sizePx)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = {
                    // If the local MediaStore URI fails for a track/album, fall back to the
                    // online cover-art repository (triggers the LaunchedEffect above).
                    if (!isArtist && onlineFallbackModel == null) {
                        fallbackAttempt++
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (isArtist) Icons.Default.Person else Icons.Default.MusicNote,
                contentDescription = null,
                tint = KaonPrimary.copy(alpha = 0.5f),
                modifier = Modifier.size(if (isArtist) 28.dp else 24.dp)
            )
        }
    }
}

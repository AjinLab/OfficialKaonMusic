package com.kaon.music.plugins.defaultui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Sealed UI state for the async image. */
sealed interface ImageState {
    data object Loading : ImageState
    data class Success(val bitmap: Bitmap) : ImageState
    data object Error : ImageState
    data object Empty : ImageState
}

/** Process-wide LRU cache keyed by file path. */
private val bitmapCache: LruCache<String, Bitmap> by lazy {
    val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
}

/** Compute inSampleSize to downsample large files into the requested bounds. */
private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (h, w) = options.outHeight to options.outWidth
    var sample = 1
    if (h > reqHeight || w > reqWidth) {
        val halfH = h / 2
        val halfW = w / 2
        while (halfH / sample >= reqHeight && halfW / sample >= reqWidth) sample *= 2
    }
    return sample
}

/**
 * Async image loader with:
 *  - LRU memory cache (process-wide)
 *  - Automatic downsampling to avoid OOM
 *  - Crossfade on path change
 *  - Optional shadow + rounded shape
 *  - Optional gaussian blur (API 31+)
 *  - Distinct Loading/Success/Error/Empty states
 */
@Composable
fun AsyncImage(
    path: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    elevation: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Crop,
    blurRadius: Dp = 0.dp,
    targetWidth: Int = 512,
    targetHeight: Int = 512
) {
    val state by produceState<ImageState>(
        initialValue = if (path == null) ImageState.Empty else ImageState.Loading,
        key1 = path
    ) {
        if (path == null) { value = ImageState.Empty; return@produceState }

        bitmapCache[path]?.let { cached ->
            value = ImageState.Success(cached); return@produceState
        }

        value = ImageState.Loading
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds, targetWidth, targetHeight)
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inPurgeable = true
                }
                BitmapFactory.decodeFile(path, opts)
            }.getOrNull()
        }

        value = if (decoded != null) {
            bitmapCache.put(path, decoded)
            ImageState.Success(decoded)
        } else ImageState.Error
    }

    val baseModifier = modifier
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
        .clip(shape)
        .then(
            if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Modifier.blur(blurRadius) else Modifier
        )

    Crossfade(
        targetState = state,
        animationSpec = tween(300),
        label = "artworkCrossfade"
    ) { current ->
        when (current) {
            is ImageState.Success -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = baseModifier,
                contentScale = contentScale
            )
            ImageState.Loading -> Box(
                modifier = baseModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { /* subtle empty slot — could add a shimmer here */ }
            ImageState.Error, ImageState.Empty -> Box(
                modifier = baseModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
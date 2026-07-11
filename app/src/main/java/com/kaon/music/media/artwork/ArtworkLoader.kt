package com.kaon.music.media.artwork

import android.content.Context
import android.graphics.BitmapFactory
import com.kaon.music.media.cache.AlbumArtCache
import com.kaon.music.media.model.Song
import com.kaon.music.media.services.MetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of loading artwork — contains both the artwork reference and extracted palette colors.
 */
data class ArtworkResult(
    val artwork: Artwork,
    val colors: ArtworkColors?
)

class ArtworkLoader(
    private val paletteCache: ArtworkPaletteCache,
    private val repository: ArtworkRepository
) {
    /**
     * Loads artwork and extracts palette colors in one pass.
     * Colors are cached by artworkKey (file path) so different editions are handled correctly.
     */
    suspend fun load(song: Song): ArtworkResult = withContext(Dispatchers.IO) {
        val bitmap = repository.load(ArtworkRequest(song.albumId, ArtworkSizes.Player, song))
        val artwork = if (bitmap != null) Artwork.BitmapSource(bitmap) else Artwork.None

        val colors = when (artwork) {
            is Artwork.BitmapSource -> {
                val artworkKey = "bitmap_${song.albumId}_${song.artworkHash ?: song.id}"
                paletteCache.getOrExtract(artworkKey, artwork.bitmap)
            }
            else -> null
        }

        ArtworkResult(artwork, colors)
    }
}

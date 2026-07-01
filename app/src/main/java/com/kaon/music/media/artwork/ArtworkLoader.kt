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
    private val context: Context,
    private val cache: AlbumArtCache,
    private val metadataReader: MetadataProvider,
    private val paletteCache: ArtworkPaletteCache
) {
    /**
     * Loads artwork and extracts palette colors in one pass.
     * Colors are cached by artworkKey (file path) so different editions are handled correctly.
     */
    suspend fun load(song: Song): ArtworkResult = withContext(Dispatchers.IO) {
        val artwork = loadArtwork(song)

        val colors = when (artwork) {
            is Artwork.FileSource -> {
                val artworkKey = artwork.file.absolutePath
                paletteCache.getOrExtract(artworkKey, artwork.file)
            }
            is Artwork.BitmapSource -> {
                val artworkKey = "bitmap_${song.albumId}_${song.artworkHash ?: song.id}"
                paletteCache.getOrExtract(artworkKey, artwork.bitmap)
            }
            else -> null
        }

        ArtworkResult(artwork, colors)
    }

    /**
     * Original artwork loading logic — returns the Artwork without palette.
     */
    private suspend fun loadArtwork(song: Song): Artwork {
        val cachedFile = cache.get(song.albumId)
        if (cachedFile != null) {
            return Artwork.FileSource(cachedFile)
        }

        val metadata = metadataReader.read(song)
        if (metadata.artwork != null) {
            val savedFile = cache.put(song.albumId, metadata.artwork)
            if (savedFile != null) {
                return Artwork.FileSource(savedFile)
            }
        }

        // If artwork is missing, generate placeholder based on album/song and cache it
        val placeholderBitmap = PlaceholderGenerator.generate(
            title = metadata.title,
            album = metadata.album,
            artist = metadata.artist
        )
        val savedFile = cache.put(song.albumId, placeholderBitmap)
        if (savedFile != null) {
            return Artwork.FileSource(savedFile)
        }

        return Artwork.None
    }
}

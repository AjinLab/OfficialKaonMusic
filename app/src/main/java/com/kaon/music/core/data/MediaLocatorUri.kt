package com.kaon.music.core.data

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.kaon.music.core.data.model.ArtworkRef
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.Track

/**
 * The platform boundary: domain locators become Android URIs here and nowhere else.
 *
 * Keeping `android.net.Uri` out of [MediaLocator] is what lets identity be unit-tested on the JVM
 * (ARCHITECTURE.md §3.1), but something still has to produce a concrete URI for Media3 and Coil. That
 * translation is a single function per direction, in one file, so a second "how do I build a YouTube
 * URI" never appears.
 */
object MediaLocatorUri {

    /** Scheme for streamed provider media; `KaonPlaybackService`'s resolver matches on it. */
    const val PROVIDER_SCHEME = "provider"

    /** Legacy scheme, still accepted when reading so an existing queue keeps playing. */
    private const val LEGACY_YOUTUBE_SCHEME = "youtube"

    private val audioCollection: Uri?
        get() = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } catch (e: Throwable) {
            null
        }

    /**
     * The URI Media3 should open for [locator].
     *
     * A stream becomes `provider://<provider>/<externalId>`, which the playback service's
     * `ResolvingDataSource` recognises and hands to the resolver — the resolved CDN URL never appears
     * in a `MediaItem`, because it expires. A download points straight at its file, which is what makes
     * offline playback a locator change rather than a new code path.
     */
    fun playbackUri(locator: MediaLocator): Uri? = when (locator) {
        is MediaLocator.LocalFile -> audioCollection?.let {
            ContentUris.withAppendedId(it, locator.mediaStoreId)
        }
        is MediaLocator.Remote -> providerUri(locator.provider.value, locator.externalId)
        // Downloads are not implemented yet; the file name is not a path until a download directory
        // exists, so this deliberately resolves to null rather than inventing one.
        is MediaLocator.Downloaded -> null
    }

    fun providerUri(provider: String, externalId: String): Uri =
        Uri.Builder()
            .scheme(PROVIDER_SCHEME)
            .authority(provider.lowercase())
            .appendPath(externalId)
            .build()

    /**
     * Reads provider identity back out of a playback URI.
     *
     * Accepts the legacy `youtube://<videoId>` form as well, so a queue snapshot or a `MediaItem`
     * written by an older build still resolves (spec §15: maintain backward compatibility).
     */
    fun providerIdentityOf(uri: Uri): Pair<String, String>? = when (uri.scheme) {
        PROVIDER_SCHEME -> {
            val provider = uri.authority?.uppercase()?.takeIf { it.isNotBlank() }
            val externalId = uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
            if (provider != null && externalId != null) provider to externalId else null
        }
        LEGACY_YOUTUBE_SCHEME -> {
            val videoId = (uri.authority ?: uri.schemeSpecificPart?.removePrefix("//"))
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            videoId?.let { "YOUTUBE" to it }
        }
        else -> null
    }

    /** The image URI for [Track.artwork], or null when there is nothing to draw. */
    fun artworkUri(artwork: ArtworkRef?): Uri? = when (artwork) {
        null -> null
        is ArtworkRef.RemoteUrl -> runCatching { Uri.parse(artwork.url) }.getOrNull()
        is ArtworkRef.MediaStoreAlbum -> runCatching {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                artwork.albumId
            )
        }.getOrNull()
    }
}

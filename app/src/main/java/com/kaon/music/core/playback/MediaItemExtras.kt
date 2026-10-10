package com.kaon.music.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.kaon.music.core.data.MediaLocatorUri
import com.kaon.music.core.data.model.ArtworkRef
import com.kaon.music.core.data.model.AudioSpec
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.ProviderId
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackId

/**
 * Keys for the metadata Kaon attaches to a Media3 [MediaItem].
 *
 * Media3 owns the timeline, so a `MediaItem` has to carry enough to reconstruct a [Track] when Room
 * cannot be consulted synchronously. These are that payload — identity and locator facts, never
 * artwork, which travels in `MediaMetadata.artworkUri` where Media3 and the notification expect it.
 */
internal object MediaItemExtras {
    const val MEDIA_STORE_ID = "kaon.media_store_id"
    const val PROVIDER = "kaon.provider"
    const val EXTERNAL_ID = "kaon.external_id"
    const val RELATIVE_PATH = "kaon.relative_path"
    const val MIME_TYPE = "kaon.mime_type"
    const val ALBUM_ID = "kaon.album_id"
    const val ARTIST_ID = "kaon.artist_id"
    const val DURATION_MS = "kaon.duration_ms"
    const val SIZE_BYTES = "kaon.size_bytes"
}

/**
 * The one canonical `Track → MediaItem` conversion.
 *
 * There were three before — `PlaybackFacade`, the queue-restore path in `KaonPlaybackService`, and the
 * live-streaming test — and they had already drifted: the restore path built items with no extras at
 * all, so a restored streamed track lost its provider identity and fell back to URI-scheme sniffing
 * (spec §16).
 *
 * The URI is produced by [MediaLocatorUri], so `provider://youtube/<id>` exists in exactly one place;
 * a resolved CDN URL is never stored in a `MediaItem` because it expires.
 */
internal fun Track.toMediaItem(): MediaItem {
    val locator = this.locator
    return MediaItem.Builder()
        .setMediaId(id.value.toString())
        .setUri(MediaLocatorUri.playbackUri(locator))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                // Artwork travels as artwork. It is never the playback URI (ARCHITECTURE.md §3.1).
                .setArtworkUri(MediaLocatorUri.artworkUri(artwork))
                .setExtras(
                    Bundle().apply {
                        putString(MediaItemExtras.PROVIDER, locator.provider.value)
                        putString(MediaItemExtras.EXTERNAL_ID, locator.externalId)
                        putString(MediaItemExtras.RELATIVE_PATH, fileName)
                        putLong(
                            MediaItemExtras.MEDIA_STORE_ID,
                            (locator as? MediaLocator.LocalFile)?.mediaStoreId ?: 0L
                        )
                        putString(MediaItemExtras.MIME_TYPE, audio.mimeType)
                        putLong(MediaItemExtras.ALBUM_ID, albumId)
                        putLong(MediaItemExtras.ARTIST_ID, artistId)
                        putLong(MediaItemExtras.DURATION_MS, durationMs)
                        putLong(MediaItemExtras.SIZE_BYTES, audio.sizeBytes)
                    }
                )
                .build()
        )
        .build()
}

/**
 * Rebuilds a [Track] from a `MediaItem` when Room has no row for it.
 *
 * Still needed after Phase 4: a search result is only persisted once something *references* it, so a
 * track can legitimately be playing before it has a row. The reconstruction prefers the extras and
 * falls back to parsing the URI, which is what makes a `MediaItem` written by an older build (legacy
 * `youtube://` scheme, old extras) still resolve.
 */
internal fun MediaItem.toTrackOrNull(): Track? {
    val trackId = mediaId.toLongOrNull() ?: return null
    val extras = mediaMetadata.extras
    val uri = localConfiguration?.uri

    val identityFromUri = uri?.let { MediaLocatorUri.providerIdentityOf(it) }
    val provider = extras?.getString(MediaItemExtras.PROVIDER)?.takeIf { it.isNotBlank() }
        ?: identityFromUri?.first
        ?: ProviderId.LOCAL.value
    val externalId = extras?.getString(MediaItemExtras.EXTERNAL_ID)?.takeIf { it.isNotBlank() }
        ?: identityFromUri?.second

    val mediaStoreId = extras?.getLong(MediaItemExtras.MEDIA_STORE_ID, 0L) ?: 0L
    val relativePath = extras?.getString(MediaItemExtras.RELATIVE_PATH)
    val audio = AudioSpec(
        mimeType = extras?.getString(MediaItemExtras.MIME_TYPE),
        sizeBytes = extras?.getLong(MediaItemExtras.SIZE_BYTES, 0L) ?: 0L
    )

    val providerId = ProviderId.of(provider)
    val locator = when {
        !providerId.isLocal && externalId != null -> {
            val origin = MediaLocator.Remote(providerId, externalId)
            if (relativePath.isNullOrBlank()) origin
            else MediaLocator.Downloaded(origin, relativePath, audio)
        }
        else -> MediaLocator.LocalFile(mediaStoreId, relativePath.orEmpty())
    }

    return Track(
        id = TrackId(trackId),
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        durationMs = extras?.getLong(MediaItemExtras.DURATION_MS, 0L) ?: 0L,
        locator = locator,
        artistId = extras?.getLong(MediaItemExtras.ARTIST_ID, 0L) ?: 0L,
        albumId = extras?.getLong(MediaItemExtras.ALBUM_ID, 0L) ?: 0L,
        audio = audio,
        artwork = mediaMetadata.artworkUri?.toString()?.let { ArtworkRef.RemoteUrl(it) }
    )
}

package com.kaon.music.core.data.model

/**
 * One track, whatever provider it came from.
 *
 * Local files, YouTube streams and (later) downloads are the same type with the same identity rules:
 * a Kaon-owned [TrackId], one explicit [MediaLocator] saying where the audio is, optional provider
 * identity carried by that locator, and [artwork] kept strictly separate from the playback location.
 * There is no `source: String` discriminator and no per-provider nullable field pile — asking "is this
 * a stream?" is asking the locator (ARCHITECTURE.md §3.1).
 *
 * No `android.*` type appears here: the platform layer turns a locator into a concrete URI at the
 * Media3 and MediaStore boundaries, which is what lets identity and format behaviour be unit-tested.
 */
data class Track(
    val id: TrackId,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val locator: MediaLocator,
    val artistId: Long = 0L,
    val albumId: Long = 0L,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val year: Int = 0,
    val audio: AudioSpec = AudioSpec.UNKNOWN,
    val artwork: ArtworkRef? = null,
    val dateModified: Long = 0L,
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false,
    /**
     * The local file backing this track is currently not present.
     *
     * Only meaningful for [MediaLocator.LocalFile]: it marks a row whose media vanished, which keeps
     * the user's favorites, history and playlist membership intact until the file comes back
     * (re-link) or the retention window expires.
     */
    val isMissing: Boolean = false
) {

    /** The catalogue this track belongs to. */
    val provider: ProviderId get() = locator.provider

    /** Provider-scoped id, `null` for device files. Unique only together with [provider]. */
    val externalId: String? get() = locator.externalId

    /** MediaStore id, `null` unless the audio is a device file. Never an identity — see [TrackId]. */
    val mediaStoreId: Long? get() = (locator as? MediaLocator.LocalFile)?.mediaStoreId

    /** Relative path or download file name; the only thing format sniffing needs. */
    val fileName: String?
        get() = when (val l = locator) {
            is MediaLocator.LocalFile -> l.relativePath
            is MediaLocator.Downloaded -> l.fileName
            is MediaLocator.Remote -> null
        }

    val mimeType: String? get() = audio.mimeType

    val sizeBytes: Long get() = audio.sizeBytes

    /** True when producing audio for this track requires the network. */
    val isOnline: Boolean get() = locator.requiresNetwork

    /**
     * Key for lazy lists and maps that must survive an id assignment.
     *
     * A provider-identified track keys on `provider:externalId`, so a search result keeps the same key
     * after it is persisted and given a real [TrackId]. Two different unsaved remote tracks therefore
     * cannot collide the way `TrackId.UNSAVED` would, and the value stays a `String`, which lazy-list
     * keys have to be in order to be saved into instance state.
     */
    val stableKey: String
        get() = externalId?.let { "${provider.value}:$it" } ?: "${ProviderId.LOCAL.value}:${id.value}"

    val displayTitle: String get() = title.ifBlank { "Unknown Title" }

    val displayArtist: String get() = artist.ifBlank { "Unknown Artist" }

    val displayAlbum: String get() = album.ifBlank { "Unknown Album" }

    val audioFormatLabel: String
        get() = if (isOnline) "YouTube" else AudioFormat.label(mimeType, fileName)

    val isLossless: Boolean
        get() = !isOnline && AudioFormat.isLossless(mimeType, fileName)

    val formatBadge: String
        get() = AudioFormat.qualityBadge(mimeType, fileName, isOnline = isOnline)

    val playbackSourceLabel: String
        get() = if (isOnline) "YouTube Music" else AudioFormat.label(mimeType, fileName)
}

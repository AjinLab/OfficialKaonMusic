package com.kaon.music.core.data.model

/**
 * Kaon's own identity for a piece of media.
 *
 * Assigned by Room (`tracks.track_id`) and stable for the lifetime of the row: metadata edits,
 * MediaStore re-scans and re-links change the row's *contents*, never its id (ARCHITECTURE.md §3.1).
 * It is never derived from a hash, a title or a provider's id — a 32-bit `String.hashCode()` collides
 * and cannot be reversed, so it can never serve as an identity.
 *
 * [UNSAVED] is the identity of a track that has not been persisted yet: a search result the user has
 * only looked at. Such a track becomes durable the moment something has to *reference* it (play,
 * favorite, playlist, queue snapshot), at which point the repository upserts it on provider identity
 * and Room assigns the real id.
 */
@JvmInline
value class TrackId(val value: Long) {

    /** True once Room has assigned this id, i.e. a row exists for it. */
    val isSaved: Boolean get() = value > 0L

    override fun toString(): String = value.toString()

    companion object {
        /** Not yet persisted. Room's `autoGenerate` reads 0 as "assign one". */
        val UNSAVED = TrackId(0L)
    }
}

/**
 * The catalogue a [MediaLocator.externalId] belongs to.
 *
 * A string wrapper rather than an enum so a value written by an older or newer install round-trips
 * instead of throwing, and so adding a provider stays a constant rather than a schema change.
 * External ids are only unique *within* a provider, so identity is always the pair.
 */
@JvmInline
value class ProviderId(val value: String) {

    val isLocal: Boolean get() = value == LOCAL_VALUE

    override fun toString(): String = value

    companion object {
        private const val LOCAL_VALUE = "LOCAL"

        /** Media owned by the device and indexed by MediaStore. */
        val LOCAL = ProviderId(LOCAL_VALUE)

        /** YouTube / YouTube Music, identified by video id. */
        val YOUTUBE = ProviderId("YOUTUBE")

        /** Normalises a persisted or provider-supplied value; blank means local. */
        fun of(raw: String?): ProviderId =
            if (raw.isNullOrBlank()) LOCAL else ProviderId(raw.trim().uppercase())
    }
}

/**
 * What a track needs in order to be *decoded*, independent of where its bytes come from.
 *
 * Two fields because two are what the app actually reads: the mime type picks a Media3 extractor and
 * labels the format, the size feeds the deterministic re-link match. Anything else a provider reports
 * is not persisted today and is not invented here.
 */
data class AudioSpec(
    val mimeType: String? = null,
    val sizeBytes: Long = 0L
) {
    companion object {
        val UNKNOWN = AudioSpec()
    }
}

/**
 * Where a track's audio actually lives — the one explicit statement of playback location.
 *
 * Never an artwork URL, and never an `android.net.Uri`: the domain states *what* the bytes are and
 * the platform layer turns that into a concrete URI (`content://…`, `youtube://…`) at the Media3
 * boundary. Keeping framework types out is what lets identity be unit-tested on the JVM.
 */
sealed interface MediaLocator {

    /** The catalogue this media belongs to. Always [ProviderId.LOCAL] for device files. */
    val provider: ProviderId

    /** Provider-scoped id. `null` for device files, which MediaStore already identifies. */
    val externalId: String?

    /** True when producing bytes for this locator requires the network. */
    val requiresNetwork: Boolean

    /**
     * A file indexed by MediaStore.
     *
     * [mediaStoreId] is deliberately *not* the track identity: it changes whenever the media scanner
     * re-inserts a file, which is precisely what re-linking repairs while [TrackId] stays fixed.
     */
    data class LocalFile(
        val mediaStoreId: Long,
        val relativePath: String
    ) : MediaLocator {
        override val provider: ProviderId get() = ProviderId.LOCAL
        override val externalId: String? get() = null
        override val requiresNetwork: Boolean get() = false
    }
    /** Provider media that is streamed; the byte source is resolved per playback. */
    data class Remote(
        override val provider: ProviderId,
        override val externalId: String
    ) : MediaLocator {
        override val requiresNetwork: Boolean get() = true
    }

    /**
     * Provider media with a local copy.
     *
     * Downloads are not implemented in this phase. The case exists so that persisting one later is a
     * locator change rather than another migration: [origin] keeps the provider identity that made
     * the download addressable, so the same row stays the same track before and after downloading.
     */
    data class Downloaded(
        val origin: Remote,
        val fileName: String,
        val audio: AudioSpec = AudioSpec.UNKNOWN
    ) : MediaLocator {
        override val provider: ProviderId get() = origin.provider
        override val externalId: String get() = origin.externalId
        override val requiresNetwork: Boolean get() = false
    }
}

/**
 * Where a track's *picture* comes from.
 *
 * Separate from [MediaLocator] by construction: the defect this phase removes was a thumbnail URL
 * living in the playback-location field, which made "where do I play from" and "what do I draw"
 * the same nullable `Uri`.
 */
sealed interface ArtworkRef {

    /** Album art reachable through MediaStore's album-art path for [albumId]. */
    data class MediaStoreAlbum(val albumId: Long) : ArtworkRef

    /** A provider-supplied image URL. */
    data class RemoteUrl(val url: String) : ArtworkRef
}


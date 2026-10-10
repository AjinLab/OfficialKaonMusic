package com.kaon.music.core.data.db

import com.kaon.music.core.data.db.dao.TrackRow
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.ArtworkRef
import com.kaon.music.core.data.model.AudioSpec
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.ProviderId
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackId

/**
 * The one place a stored row becomes a [Track] and back.
 *
 * Both read paths — a full [TrackEntity] and the paged [TrackRow] projection — go through the same
 * locator reconstruction, so a paged row and an entity read produce an identical `Track`. Two separate
 * mappings is how "remote tracks are special" crept in before.
 *
 * Pure Kotlin by design: no `android.*` type appears here, so identity and locator behaviour are
 * JVM-testable. The platform layer turns a locator into a concrete URI at the Media3 and MediaStore
 * boundaries (see `MediaLocatorUri`).
 */

/**
 * Reconstructs where the audio lives from the stored columns.
 *
 * The three cases are distinguished by data rather than by a discriminator string:
 * - `provider = 'LOCAL'` → a device file, identified by MediaStore.
 * - a provider row **without** a `relative_path` → streamed.
 * - a provider row **with** a `relative_path` → downloaded (the file name lives in that column).
 *
 * That is why adding downloads later needs no new column and no new table.
 *
 * A provider row with a blank `external_id` cannot be addressed remotely; it degrades to a local
 * locator rather than throwing, because a row like that can only come from a v7 database that recorded
 * `source = 'YOUTUBE'` without a video id, and losing the user's favorites over it would be worse than
 * an unplayable row (see `MIGRATION_7_8`).
 */
fun mediaLocatorOf(
    provider: String,
    externalId: String?,
    mediaStoreId: Long?,
    relativePath: String?,
    audio: AudioSpec
): MediaLocator {
    val providerId = ProviderId.of(provider)
    val external = externalId?.takeIf { it.isNotBlank() }
    if (providerId.isLocal || external == null) {
        return MediaLocator.LocalFile(
            mediaStoreId = mediaStoreId ?: 0L,
            relativePath = relativePath.orEmpty()
        )
    }
    val origin = MediaLocator.Remote(provider = providerId, externalId = external)
    return if (relativePath.isNullOrBlank()) {
        origin
    } else {
        MediaLocator.Downloaded(origin = origin, fileName = relativePath, audio = audio)
    }
}

/**
 * Artwork, kept strictly out of the playback locator.
 *
 * A provider thumbnail is a URL; a device file's art is reachable through MediaStore's album-art path.
 * Neither is ever the place the audio comes from — that conflation is the defect this phase removes.
 */
fun artworkRefOf(artworkUrl: String?, albumId: Long, provider: String): ArtworkRef? = when {
    !artworkUrl.isNullOrBlank() -> ArtworkRef.RemoteUrl(artworkUrl)
    ProviderId.of(provider).isLocal && albumId != 0L -> ArtworkRef.MediaStoreAlbum(albumId)
    else -> null
}

fun TrackEntity.toDomain(isFavorite: Boolean): Track {
    val audio = AudioSpec(mimeType = mimeType, sizeBytes = sizeBytes)
    return Track(
        id = TrackId(trackId),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        locator = mediaLocatorOf(provider, externalId, mediaStoreId, relativePath, audio),
        artistId = artistId,
        albumId = albumId,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        audio = audio,
        artwork = artworkRefOf(artworkUrl, albumId, provider),
        dateModified = dateModified,
        dateAdded = dateAdded,
        isFavorite = isFavorite,
        isMissing = isMissing
    )
}

/** [TrackRow] already carries favorite state from SQL, so there is nothing to join here. */
fun TrackRow.toDomain(): Track {
    val audio = AudioSpec(mimeType = mime_type, sizeBytes = size_bytes)
    return Track(
        id = TrackId(track_id),
        title = title,
        artist = artist,
        album = album,
        durationMs = duration_ms,
        locator = mediaLocatorOf(provider, external_id, media_store_id, relative_path, audio),
        artistId = artist_id,
        albumId = album_id,
        trackNumber = track_number,
        discNumber = disc_number,
        year = year,
        audio = audio,
        artwork = artworkRefOf(artwork_url, album_id, provider),
        dateModified = date_modified,
        dateAdded = date_added,
        isFavorite = is_favorite,
        isMissing = is_missing
    )
}

/**
 * Projects a domain track onto storage columns.
 *
 * [TrackId.UNSAVED] stays `0`, which is what Room's `autoGenerate` reads as "assign one" — so
 * persisting a search result for the first time and updating an existing row are the same call.
 */
fun Track.toEntity(lastSeenTimestamp: Long = System.currentTimeMillis()): TrackEntity {
    val locator = this.locator
    return TrackEntity(
        trackId = id.value,
        mediaStoreId = (locator as? MediaLocator.LocalFile)?.mediaStoreId,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        durationMs = durationMs,
        sizeBytes = audio.sizeBytes,
        dateModified = dateModified,
        dateAdded = dateAdded,
        relativePath = when (locator) {
            is MediaLocator.LocalFile -> locator.relativePath
            is MediaLocator.Downloaded -> locator.fileName
            is MediaLocator.Remote -> null
        },
        titleNormalized = title.normalizeForStorage(),
        artistNormalized = artist.normalizeForStorage(),
        albumNormalized = album.normalizeForStorage(),
        isMissing = isMissing,
        provider = locator.provider.value,
        externalId = locator.externalId,
        mimeType = audio.mimeType,
        artworkUrl = (artwork as? ArtworkRef.RemoteUrl)?.url,
        lastSeenTimestamp = lastSeenTimestamp
    )
}

/**
 * Case-folding for the `*_normalized` search columns.
 *
 * Must stay identical to the query-side normalization, or a row is written in a form its own search
 * predicate cannot match.
 */
fun String.normalizeForStorage(): String = trim().lowercase()

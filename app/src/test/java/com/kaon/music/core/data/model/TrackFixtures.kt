package com.kaon.music.core.data.model

/**
 * Track builders for tests.
 *
 * Phase 4 replaced `Track`'s flat `source`/`youtubeVideoId`/`contentUri` fields with a
 * [MediaLocator], so every test that constructed a track by hand had to state a locator. These two
 * builders say which *kind* of media a test means — device file or provider stream — and default
 * everything else, which keeps the tests about behaviour rather than about the model's shape.
 */

fun localTrack(
    id: Long,
    title: String = "Track $id",
    artist: String = "Artist",
    album: String = "Album",
    mediaStoreId: Long = 1000L + id,
    relativePath: String = "Music/track_$id.mp3",
    durationMs: Long = 200_000L,
    sizeBytes: Long = 4_000_000L,
    mimeType: String? = "audio/mpeg",
    albumId: Long = 1L,
    artistId: Long = 1L,
    dateAdded: Long = id,
    isFavorite: Boolean = false,
    isMissing: Boolean = false
): Track = Track(
    id = TrackId(id),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    locator = MediaLocator.LocalFile(mediaStoreId = mediaStoreId, relativePath = relativePath),
    artistId = artistId,
    albumId = albumId,
    audio = AudioSpec(mimeType = mimeType, sizeBytes = sizeBytes),
    artwork = if (albumId != 0L) ArtworkRef.MediaStoreAlbum(albumId) else null,
    dateAdded = dateAdded,
    isFavorite = isFavorite,
    isMissing = isMissing
)

/**
 * A provider (YouTube) track.
 *
 * [id] defaults to [TrackId.UNSAVED], which is what a search result actually is until something
 * references it — tests that assert persistence behaviour depend on that default being honest.
 */
fun remoteTrack(
    externalId: String,
    id: Long = 0L,
    title: String = "Remote $externalId",
    artist: String = "Remote Artist",
    album: String = "Single",
    durationMs: Long = 180_000L,
    artworkUrl: String? = "https://example.invalid/art/$externalId.jpg",
    provider: ProviderId = ProviderId.YOUTUBE,
    isFavorite: Boolean = false
): Track = Track(
    id = TrackId(id),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    locator = MediaLocator.Remote(provider = provider, externalId = externalId),
    audio = AudioSpec.UNKNOWN,
    artwork = artworkUrl?.let { ArtworkRef.RemoteUrl(it) },
    isFavorite = isFavorite
)

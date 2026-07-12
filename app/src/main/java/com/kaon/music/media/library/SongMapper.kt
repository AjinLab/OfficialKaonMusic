package com.kaon.music.media.library

import com.kaon.music.media.library.db.entity.SongEntity
import com.kaon.music.media.model.Song

fun SongEntity.toSong(
    artist: String = "",
    album: String = "",
    isFavorite: Boolean = false
): Song {
    return Song(
        id = id,
        mediaStoreId = id,
        uri = uri,
        path = path,
        title = title,
        artist = artist.takeIf { it.isNotBlank() } ?: "Unknown Artist",
        album = album.takeIf { it.isNotBlank() } ?: "Unknown Album",
        albumArtist = null,
        albumId = albumId,
        artistId = artistId,
        genre = genre,
        composer = composer,
        year = null,
        track = trackNumber,
        disc = discNumber,
        duration = duration,
        bitrate = null,
        sampleRate = null,
        mimeType = mimeType,
        size = size,
        dateAdded = dateAdded,
        dateModified = 0,
        lastScanned = 0,
        artworkPath = null,
        favorite = isFavorite
    )
}

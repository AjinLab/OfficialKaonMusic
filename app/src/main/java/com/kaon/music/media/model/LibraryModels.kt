package com.kaon.music.media.model

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int
) {
    val searchName: String = name.lowercase()
}

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val songCount: Int,
    val year: String?,
    val artworkId: Long? = null
) {
    val searchTitle: String = title.lowercase()
    val searchArtist: String = artistName.lowercase()
}

@Immutable
data class AlbumDetail(
    val album: Album,
    val songs: List<Song>,
    val totalDuration: Long
)

@Immutable
data class ArtistDetail(
    val artist: Artist,
    val albums: List<Album>,
    val songs: List<Song>,
    val totalDuration: Long
)

@Immutable
data class Genre(
    val name: String,
    val songCount: Int
)

@Immutable
data class Folder(
    val path: String,
    val songCount: Int
) {
    val searchPath: String = path.lowercase()
}

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int
)

enum class LibrarySort {
    TITLE,
    ARTIST,
    ALBUM,
    YEAR,
    DATE_ADDED,
    DURATION,
    SONG_COUNT
}

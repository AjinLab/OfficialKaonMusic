package com.kaon.music.media.search

import com.kaon.music.media.model.Album
import com.kaon.music.media.model.Artist
import com.kaon.music.media.model.Folder
import com.kaon.music.media.model.Song

sealed interface SearchItem {
    val score: Int
}

data class SongResult(
    val song: Song,
    override val score: Int
) : SearchItem

data class AlbumResult(
    val album: Album,
    override val score: Int
) : SearchItem

data class ArtistResult(
    val artist: Artist,
    override val score: Int
) : SearchItem

data class FolderResult(
    val folder: Folder,
    override val score: Int
) : SearchItem

data class SearchResults(
    val items: List<SearchItem>
)

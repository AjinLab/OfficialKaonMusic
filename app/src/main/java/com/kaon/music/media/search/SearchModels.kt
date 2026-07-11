package com.kaon.music.media.search

import androidx.compose.runtime.Immutable
import com.kaon.music.media.model.Album
import com.kaon.music.media.model.Artist
import com.kaon.music.media.model.Folder
import com.kaon.music.media.model.Song

@Immutable
sealed interface SearchItem {
    val score: Int
}

@Immutable
data class SongResult(
    val song: Song,
    override val score: Int
) : SearchItem

@Immutable
data class AlbumResult(
    val album: Album,
    override val score: Int
) : SearchItem

@Immutable
data class ArtistResult(
    val artist: Artist,
    override val score: Int
) : SearchItem

@Immutable
data class FolderResult(
    val folder: Folder,
    override val score: Int
) : SearchItem

@Immutable
data class SearchResults(
    val items: List<SearchItem>
)

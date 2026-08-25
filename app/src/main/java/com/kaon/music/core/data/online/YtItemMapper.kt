package com.kaon.music.core.data.online

import android.net.Uri
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem

/**
 * Mapper layer isolating KaonMusic domain models from InnerTube API models.
 */
object YtItemMapper {

    fun songItemToTrack(song: SongItem): Track {
        val stableId = song.id.hashCode().toLong()
        val artistNames = song.artists.joinToString(", ") { it.name }
        val durationMs = (song.duration ?: 0) * 1000L

        return Track(
            id = stableId,
            mediaStoreId = 0L,
            title = song.title,
            artist = artistNames.ifBlank { "Unknown Artist" },
            artistId = 0L,
            album = song.album?.name ?: "Single",
            albumId = 0L,
            durationMs = durationMs,
            sizeBytes = 0L,
            dateModified = System.currentTimeMillis(),
            dateAdded = System.currentTimeMillis(),
            contentUri = song.thumbnail?.let { Uri.parse(it) },
            isFavorite = false,
            isMissing = false,
            source = "YOUTUBE",
            youtubeVideoId = song.id
        )
    }

    fun albumItemToAlbum(album: AlbumItem): Album {
        val stableId = album.id.hashCode().toLong()
        return Album(
            albumId = stableId,
            title = album.title,
            artist = album.artists?.joinToString(", ") { it.name } ?: "Various Artists",
            artistId = 0L,
            year = album.year ?: 0,
            trackCount = 0,
            totalDurationMs = 0L
        )
    }

    fun artistItemToArtist(artist: ArtistItem): Artist {
        val stableId = artist.id.hashCode().toLong()
        return Artist(
            artistId = stableId,
            name = artist.title,
            albumCount = 0,
            trackCount = 0
        )
    }

    fun playlistItemToPlaylist(playlist: PlaylistItem): Playlist {
        val stableId = playlist.id.hashCode().toLong()
        return Playlist(
            id = stableId,
            name = playlist.title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            trackCount = playlist.songCountText?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        )
    }
}

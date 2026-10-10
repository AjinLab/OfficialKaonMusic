package com.kaon.music.core.data.online

import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.ArtworkRef
import com.kaon.music.core.data.model.AudioSpec
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.ProviderId
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackId
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem

/**
 * Mapper isolating Kaon's domain models from InnerTube DTOs.
 *
 * Every track this produces carries [TrackId.UNSAVED] plus a `MediaLocator.Remote` holding the real
 * provider identity. The previous version invented an id from `song.id.hashCode().toLong()` — a 32-bit
 * value that collides and cannot be reversed, and which no `tracks` row ever had. Anything that needs
 * a durable reference (favorite, playlist, history, queue) calls
 * `TrackRepository.ensurePersisted`, and Room assigns the id then (ARCHITECTURE.md §3.1).
 *
 * `Album`/`Artist` still carry derived ids because they are display-only aggregates here with no Room
 * row and no user data pointing at them; that is a Phase 5+ concern, not identity for a playable item.
 */
object YtItemMapper {

    fun songItemToTrack(song: SongItem): Track {
        val artistNames = song.artists.joinToString(", ") { it.name }
        return Track(
            id = TrackId.UNSAVED,
            title = song.title,
            artist = artistNames.ifBlank { "Unknown Artist" },
            album = song.album?.name ?: "Single",
            durationMs = (song.duration ?: 0) * 1000L,
            locator = MediaLocator.Remote(provider = ProviderId.YOUTUBE, externalId = song.id),
            audio = AudioSpec.UNKNOWN,
            // The thumbnail is artwork, never a playback location: that conflation is what Phase 4
            // removes (spec §8).
            artwork = song.thumbnail?.let { ArtworkRef.RemoteUrl(it) },
            dateModified = 0L,
            dateAdded = 0L
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

    fun playlistItemToOnlinePlaylist(playlist: PlaylistItem): com.kaon.music.core.data.model.OnlinePlaylist {
        return com.kaon.music.core.data.model.OnlinePlaylist(
            playlistId = playlist.id,
            title = playlist.title,
            author = playlist.author?.name ?: "YouTube Music",
            songCountText = playlist.songCountText,
            thumbnailUrl = playlist.thumbnail
        )
    }

    fun ytItemToTopResult(item: YTItem): com.kaon.music.core.data.model.TopResultItem? {
        return when (item) {
            is SongItem -> {
                val track = songItemToTrack(item)
                val type = if (item.isVideoSong) com.kaon.music.core.data.model.TopResultType.VIDEO else com.kaon.music.core.data.model.TopResultType.SONG
                val subtitle = "${if (item.isVideoSong) "Video" else "Song"} • ${track.artist}"
                com.kaon.music.core.data.model.TopResultItem(
                    id = item.id,
                    title = track.title,
                    subtitle = subtitle,
                    type = type,
                    thumbnailUrl = (track.artwork as? ArtworkRef.RemoteUrl)?.url,
                    track = track
                )
            }
            is AlbumItem -> {
                val album = albumItemToAlbum(item)
                val subtitle = "Album • ${album.artist}${if (album.year > 0) " • ${album.year}" else ""}"
                com.kaon.music.core.data.model.TopResultItem(
                    id = item.id,
                    title = album.title,
                    subtitle = subtitle,
                    type = com.kaon.music.core.data.model.TopResultType.ALBUM,
                    thumbnailUrl = item.thumbnail,
                    album = album
                )
            }
            is ArtistItem -> {
                val artist = artistItemToArtist(item)
                val subtitle = if (item.isProfile) "Profile" else "Artist"
                com.kaon.music.core.data.model.TopResultItem(
                    id = item.id,
                    title = artist.name,
                    subtitle = subtitle,
                    type = com.kaon.music.core.data.model.TopResultType.ARTIST,
                    thumbnailUrl = item.thumbnail,
                    artist = artist
                )
            }
            is PlaylistItem -> {
                val pl = playlistItemToOnlinePlaylist(item)
                val subtitle = "Playlist • ${pl.author}${if (!pl.songCountText.isNullOrBlank()) " • ${pl.songCountText}" else ""}"
                com.kaon.music.core.data.model.TopResultItem(
                    id = item.id,
                    title = pl.title,
                    subtitle = subtitle,
                    type = com.kaon.music.core.data.model.TopResultType.PLAYLIST,
                    thumbnailUrl = pl.thumbnailUrl,
                    playlist = pl
                )
            }
            else -> null
        }
    }
}

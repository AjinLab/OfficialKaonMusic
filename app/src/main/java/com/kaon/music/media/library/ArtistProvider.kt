package com.kaon.music.media.library

import com.kaon.music.media.library.db.LibraryDatabase
import com.kaon.music.media.model.Album
import com.kaon.music.media.model.Artist
import com.kaon.music.media.model.ArtistDetail
import com.kaon.music.media.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class ArtistProvider(
    private val db: LibraryDatabase
) {
    val artists: Flow<List<Artist>> = combine(
        db.artistDao().getAllArtists(),
        db.songDao().getAllSongs()
    ) { artistEntities, songEntities ->
        val songGroups = songEntities.groupBy { it.artistId }
        val albumGroups = songEntities.groupBy { it.artistId }.mapValues { entry ->
            entry.value.map { it.albumId }.distinct().size
        }
        artistEntities.map { artist ->
            Artist(
                id = artist.id,
                name = artist.name,
                songCount = songGroups[artist.id]?.size ?: 0,
                albumCount = albumGroups[artist.id] ?: 0
            )
        }
    }.flowOn(Dispatchers.Default)

    fun artist(id: Long): Flow<Artist?> {
        return combine(
            db.artistDao().getArtist(id),
            db.songDao().getSongsByArtist(id),
            db.albumDao().getAlbumsByArtist(id)
        ) { artist, songs, albums ->
            artist?.let {
                Artist(
                    id = it.id,
                    name = it.name,
                    songCount = songs.size,
                    albumCount = albums.size
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    fun artistAlbums(artistId: Long): Flow<List<Album>> {
        return combine(
            db.albumDao().getAlbumsByArtist(artistId),
            db.artistDao().getAllArtists(),
            db.songDao().getAllSongs()
        ) { albumEntities, artistEntities, songEntities ->
            val artistsMap = artistEntities.associateBy { it.id }
            val songGroups = songEntities.groupBy { it.albumId }
            albumEntities.map { album ->
                Album(
                    id = album.id,
                    title = album.title,
                    artistName = artistsMap[album.artistId]?.name ?: "Unknown Artist",
                    artistId = album.artistId,
                    year = album.year?.toString(),
                    songCount = songGroups[album.id]?.size ?: 0,
                    artworkId = album.id
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    fun artistSongs(artistId: Long): Flow<List<Song>> {
        return combine(
            db.songDao().getSongsByArtist(artistId),
            db.artistDao().getAllArtists(),
            db.albumDao().getAllAlbums(),
            db.favoriteDao().getFavoriteIdsFlow()
        ) { songEntities, artistEntities, albumEntities, favoriteIds ->
            val artistsMap = artistEntities.associateBy { it.id }
            val albumsMap = albumEntities.associateBy { it.id }
            val favoritesSet = favoriteIds.toSet()
            
            songEntities.map { song ->
                val artistName = artistsMap[song.artistId]?.name ?: "Unknown Artist"
                val albumTitle = albumsMap[song.albumId]?.title ?: "Unknown Album"
                song.toSong(
                    artist = artistName,
                    album = albumTitle,
                    isFavorite = favoritesSet.contains(song.id)
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    fun artistDetail(id: Long): Flow<ArtistDetail> {
        return combine(artist(id), artistAlbums(id), artistSongs(id)) { artist, albums, songs ->
            ArtistDetail(
                artist = artist ?: throw IllegalStateException("Artist not found"),
                albums = albums,
                songs = songs,
                totalDuration = songs.sumOf { it.duration }
            )
        }.flowOn(Dispatchers.Default)
    }
}
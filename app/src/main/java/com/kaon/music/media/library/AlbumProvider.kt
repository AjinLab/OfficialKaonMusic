package com.kaon.music.media.library

import com.kaon.music.media.library.db.LibraryDatabase
import com.kaon.music.media.model.Album
import com.kaon.music.media.model.AlbumDetail
import com.kaon.music.media.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class AlbumProvider(
    private val db: LibraryDatabase
) {
    val albums: Flow<List<Album>> = combine(
        db.albumDao().getAllAlbums(),
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

    fun album(id: Long): Flow<Album?> {
        return combine(
            db.albumDao().getAlbum(id),
            db.artistDao().getAllArtists(),
            db.songDao().getSongsByAlbum(id)
        ) { album, artists, songs ->
            album?.let {
                Album(
                    id = it.id,
                    title = it.title,
                    artistName = artists.firstOrNull { a -> a.id == it.artistId }?.name ?: "Unknown Artist",
                    artistId = it.artistId,
                    year = it.year?.toString(),
                    songCount = songs.size,
                    artworkId = it.id
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    fun albumSongs(albumId: Long): Flow<List<Song>> {
        return combine(
            db.songDao().getSongsByAlbum(albumId),
            db.artistDao().getAllArtists(),
            db.albumDao().getAlbum(albumId),
            db.favoriteDao().getFavoriteIdsFlow()
        ) { songEntities, artistEntities, albumEntity, favoriteIds ->
            val artistsMap = artistEntities.associateBy { it.id }
            val albumTitle = albumEntity?.title ?: "Unknown Album"
            val favoritesSet = favoriteIds.toSet()
            
            songEntities.map { song ->
                val artistName = artistsMap[song.artistId]?.name ?: "Unknown Artist"
                song.toSong(
                    artist = artistName,
                    album = albumTitle,
                    isFavorite = favoritesSet.contains(song.id)
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    fun albumDetail(id: Long): Flow<AlbumDetail> {
        return album(id).combine(albumSongs(id)) { album, songs ->
            AlbumDetail(
                album = album ?: throw IllegalStateException("Album not found"),
                songs = songs,
                totalDuration = songs.sumOf { it.duration }
            )
        }.flowOn(Dispatchers.Default)
    }
}
package com.kaon.music.media.library

import com.kaon.music.media.library.db.LibraryDatabase
import com.kaon.music.media.model.Playlist
import com.kaon.music.media.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlaylistProvider(
    private val db: LibraryDatabase
) {
    private val songs: Flow<List<Song>> = combine(
        db.songDao().getAllSongs(),
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

    fun playlistsFlow(): Flow<List<Playlist>> {
        return combine(
            db.playlistDao().getAllPlaylists(),
            db.playlistDao().getAllPlaylistSongs()
        ) { playlists, playlistSongs ->
            val counts = playlistSongs.groupBy { it.playlistId }.mapValues { it.value.size }
            playlists.map {
                Playlist(
                    id = it.id,
                    name = it.name,
                    songCount = counts[it.id] ?: 0
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        db.playlistDao().upsertPlaylist(com.kaon.music.media.library.db.entity.PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.playlistDao().deletePlaylist(id)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        val currentSongs = db.playlistDao().getPlaylistSongs(playlistId).first()
        val startPos = currentSongs.size
        val entities = songIds.mapIndexed { index, songId ->
            com.kaon.music.media.library.db.entity.PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                position = startPos + index
            )
        }
        db.playlistDao().upsertPlaylistSongs(entities)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        db.playlistDao().deletePlaylistSong(playlistId, songId)
    }

    fun playlistSongs(playlistId: Long): Flow<List<Song>> {
        return combine(
            db.playlistDao().getPlaylistSongs(playlistId),
            songs
        ) { playlistSongs, allSongs ->
            val songMap = allSongs.associateBy { it.id }
            playlistSongs.sortedBy { it.position }.mapNotNull { songMap[it.songId] }
        }.flowOn(Dispatchers.Default)
    }

    fun observePlaylist(id: Long): Flow<Playlist?> {
        return combine(
            db.playlistDao().getPlaylist(id),
            db.playlistDao().getPlaylistSongs(id)
        ) { playlist, playlistSongs ->
            playlist?.let {
                Playlist(
                    id = it.id,
                    name = it.name,
                    songCount = playlistSongs.size
                )
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }
}
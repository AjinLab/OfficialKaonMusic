package com.kaon.music.media.library

import android.content.Context
import com.kaon.music.media.library.db.LibraryDatabase
import com.kaon.music.media.library.db.LibrarySnapshot
import com.kaon.music.media.model.Album
import com.kaon.music.media.model.AlbumDetail
import com.kaon.music.media.model.Artist
import com.kaon.music.media.model.ArtistDetail
import com.kaon.music.media.model.Folder
import com.kaon.music.media.model.Genre
import com.kaon.music.media.model.Playlist
import com.kaon.music.media.model.Song
import com.kaon.music.media.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import com.kaon.music.media.library.db.entity.SongEntity
import com.kaon.music.media.library.db.entity.ArtistEntity
import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.LibraryStateEntity

class MediaRepository(
    private val context: Context,
    private val db: LibraryDatabase,
    private val albumProvider: AlbumProvider,
    private val artistProvider: ArtistProvider,
    private val playlistProvider: PlaylistProvider
) : LibraryController {
    
    private val clock = java.time.Clock.systemDefaultZone()
    private val generationTracker = com.kaon.music.media.library.sync.MediaStoreGenerationTracker(context, clock)
    private val metadataReader = com.kaon.music.media.services.MetadataProvider(context)
    private val scanner = com.kaon.music.media.library.sync.LibraryScanner(context)
    private val mapper = com.kaon.music.media.library.sync.LibraryMapper(metadataReader)
    private val sync = com.kaon.music.media.library.sync.LibrarySync(scanner, mapper, this, generationTracker, clock)

    override val songs: Flow<List<Song>> = combine(
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

    override val artists: Flow<List<Artist>> = artistProvider.artists
    override val albums: Flow<List<Album>> = albumProvider.albums
    override val folders: Flow<List<Folder>> = songs.map { songList ->
        buildFolders(songList)
    }

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            sync.sync()
        }
    }

    override fun search(query: String): Flow<List<SearchResult>> {
        return db.searchDao().search(query).map { entities ->
            entities.map {
                SearchResult(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    subtitle = it.subtitle,
                    albumId = it.albumId,
                    score = it.score
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun searchAll(query: String): com.kaon.music.media.search.SearchResults = withContext(Dispatchers.IO) {
        val results = search(query).first()
        if (results.isEmpty()) {
            return@withContext com.kaon.music.media.search.SearchResults(emptyList())
        }

        val songIds = results.asSequence()
            .filter { it.type == "SONG" }
            .map { it.id }
            .distinct()
            .toList()
        val albumResultIds = results.asSequence()
            .filter { it.type == "ALBUM" }
            .map { it.id }
            .distinct()
            .toList()
        val artistResultIds = results.asSequence()
            .filter { it.type == "ARTIST" }
            .map { it.id }
            .distinct()
            .toList()

        val songEntities = if (songIds.isNotEmpty()) db.songDao().getSongsByIds(songIds) else emptyList()
        val songMap = songEntities.associateBy { it.id }

        val albumIds = (albumResultIds.asSequence() + songEntities.asSequence().map { it.albumId })
            .distinct()
            .toList()
        val albumEntities = if (albumIds.isNotEmpty()) db.albumDao().getAlbumsByIds(albumIds) else emptyList()
        val albumsMap = albumEntities.associateBy { it.id }

        val artistIds = (artistResultIds.asSequence() +
            songEntities.asSequence().map { it.artistId } +
            albumEntities.asSequence().map { it.artistId })
            .distinct()
            .toList()
        val artistEntities = if (artistIds.isNotEmpty()) db.artistDao().getArtistsByIds(artistIds) else emptyList()
        val artistsMap = artistEntities.associateBy { it.id }

        val favoritesSet = if (songIds.isNotEmpty()) db.favoriteDao().getFavoriteIds(songIds).toSet() else emptySet()
        val albumSongCounts = if (albumResultIds.isNotEmpty()) {
            db.songDao().getSongCountsByAlbumIds(albumResultIds).associate { it.id to it.count }
        } else {
            emptyMap()
        }
        val artistSongCounts = if (artistResultIds.isNotEmpty()) {
            db.songDao().getSongCountsByArtistIds(artistResultIds).associate { it.id to it.count }
        } else {
            emptyMap()
        }
        val artistAlbumCounts = if (artistResultIds.isNotEmpty()) {
            db.albumDao().getAlbumCountsByArtistIds(artistResultIds).associate { it.id to it.count }
        } else {
            emptyMap()
        }

        val items = results.mapNotNull { res ->
            when (res.type) {
                "SONG" -> {
                    songMap[res.id]?.let {
                        val artistName = artistsMap[it.artistId]?.name ?: "Unknown Artist"
                        val albumTitle = albumsMap[it.albumId]?.title ?: "Unknown Album"
                        com.kaon.music.media.search.SongResult(
                            song = it.toSong(artistName, albumTitle, favoritesSet.contains(it.id)),
                            score = res.score
                        )
                    }
                }
                "ALBUM" -> {
                    albumsMap[res.id]?.let {
                        com.kaon.music.media.search.AlbumResult(
                            album = Album(
                                id = it.id,
                                title = it.title,
                                artistName = artistsMap[it.artistId]?.name ?: "Unknown Artist",
                                artistId = it.artistId,
                                year = it.year?.toString(),
                                songCount = albumSongCounts[it.id] ?: 0,
                                artworkId = it.id
                            ),
                            score = res.score
                        )
                    }
                }
                "ARTIST" -> {
                    artistsMap[res.id]?.let {
                        com.kaon.music.media.search.ArtistResult(
                            artist = Artist(
                                id = it.id,
                                name = it.name,
                                songCount = artistSongCounts[it.id] ?: 0,
                                albumCount = artistAlbumCounts[it.id] ?: 0
                            ),
                            score = res.score
                        )
                    }
                }
                else -> null
            }
        }
        com.kaon.music.media.search.SearchResults(items)
    }

    override suspend fun getSongsByIds(ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        val songEntities = db.songDao().getSongsByIds(ids)
        val artistEntities = db.artistDao().getAllArtists().first()
        val albumEntities = db.albumDao().getAllAlbums().first()
        val favoriteIds = db.favoriteDao().getFavoriteIdsFlow().first()
        
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
    }

    override suspend fun getAllSongIds(): List<Long> {
        return db.songDao().getAllSongIds()
    }

    override fun album(id: Long): Flow<Album?> = albumProvider.album(id)
    override fun artist(id: Long): Flow<Artist?> = artistProvider.artist(id)
    
    override fun albumSongs(albumId: Long): Flow<List<Song>> = albumProvider.albumSongs(albumId)
    override fun artistAlbums(artistId: Long): Flow<List<Album>> = artistProvider.artistAlbums(artistId)
    override fun artistSongs(artistId: Long): Flow<List<Song>> = artistProvider.artistSongs(artistId)

    override fun albumDetail(id: Long): Flow<AlbumDetail> = albumProvider.albumDetail(id)
    override fun artistDetail(id: Long): Flow<ArtistDetail> = artistProvider.artistDetail(id)

    override suspend fun genres(): List<Genre> = emptyList()

    override suspend fun folders(): List<Folder> = withContext(Dispatchers.IO) {
        buildFolders(songs.first())
    }

    override fun playlistsFlow(): Flow<List<Playlist>> = playlistProvider.playlistsFlow()
    override suspend fun createPlaylist(name: String): Long = playlistProvider.createPlaylist(name)
    override suspend fun deletePlaylist(id: Long) = playlistProvider.deletePlaylist(id)
    override suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = playlistProvider.addSongsToPlaylist(playlistId, songIds)
    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = playlistProvider.removeSongFromPlaylist(playlistId, songId)
    override fun playlistSongs(playlistId: Long): Flow<List<Song>> = playlistProvider.playlistSongs(playlistId)
    override fun observePlaylist(id: Long): Flow<Playlist?> = playlistProvider.observePlaylist(id)

    override fun favorites(): Flow<List<Song>> = songs.map { songList ->
        songList.filter { it.favorite }
    }

    override suspend fun toggleFavorite(songId: Long) {
        withContext(Dispatchers.IO) {
            if (db.favoriteDao().isFavorite(songId)) {
                db.favoriteDao().delete(songId)
            } else {
                db.favoriteDao().insert(com.kaon.music.media.library.db.entity.FavoriteEntity(songId))
            }
        }
    }

    override suspend fun deleteSong(songId: Long): Boolean = withContext(Dispatchers.IO) {
        val song = db.songDao().getSongsByIds(listOf(songId)).firstOrNull() ?: return@withContext false
        try {
            val file = java.io.File(song.path)
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    val uri = android.net.Uri.parse(song.uri)
                    context.contentResolver.delete(uri, null, null)
                }
            } else {
                val uri = android.net.Uri.parse(song.uri)
                context.contentResolver.delete(uri, null, null)
            }
            
            db.songDao().deleteSong(songId)
            db.albumDao().deleteOrphanAlbums()
            db.artistDao().deleteOrphanArtists()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun buildFolders(songList: List<Song>): List<Folder> {
        if (songList.isEmpty()) return emptyList()

        val counts = HashMap<String, Int>()
        for (song in songList) {
            val parent = parentPath(song.path)
            counts[parent] = (counts[parent] ?: 0) + 1
        }

        return counts.entries
            .map { (path, count) -> Folder(path = path, songCount = count) }
            .sortedBy { it.path }
    }

    private fun parentPath(path: String): String {
        val lastSeparator = path.lastIndexOf('/')
        return when {
            lastSeparator > 0 -> path.substring(0, lastSeparator)
            lastSeparator == 0 -> "/"
            else -> "/"
        }
    }

    override suspend fun applyLibrarySnapshot(snapshot: LibrarySnapshot) {
        db.librarySyncDao().applyLibrarySnapshot(snapshot)
    }

    suspend fun getAllSongsList(): List<SongEntity> {
        return db.songDao().getAllSongsList()
    }

    suspend fun getSongEntitiesByIds(ids: List<Long>): List<SongEntity> {
        return if (ids.isEmpty()) emptyList() else db.songDao().getSongsByIds(ids)
    }

    suspend fun getLibraryState(): LibraryStateEntity? {
        return db.libraryStateDao().getState()
    }

    suspend fun applyLibraryDiff(
        addedSongs: List<SongEntity>,
        updatedSongs: List<SongEntity>,
        removedSongIds: List<Long>,
        artists: List<ArtistEntity>,
        albums: List<AlbumEntity>,
        state: LibraryStateEntity
    ) {
        db.librarySyncDao().applyLibraryDiff(addedSongs, updatedSongs, removedSongIds, artists, albums, state)
    }
}

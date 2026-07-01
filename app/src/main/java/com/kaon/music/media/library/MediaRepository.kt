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
import kotlinx.coroutines.withContext

class MediaRepository(
    private val context: Context,
    private val db: LibraryDatabase
) : LibraryController {
    
    // Services for sync (lazy or injected elsewhere ideally, but kept here for now or moved to KaonKernel)
    private val albumArtCache = com.kaon.music.media.cache.AlbumArtCache(context)
    private val metadataReader = com.kaon.music.media.services.MetadataProvider(context)
    private val scanner = com.kaon.music.media.library.sync.LibraryScanner(context)
    private val mapper = com.kaon.music.media.library.sync.LibraryMapper(metadataReader)
    private val sync = com.kaon.music.media.library.sync.LibrarySync(scanner, mapper, this)

    override val songs: Flow<List<Song>> = db.songDao().getAllSongs().map { entities ->
        entities.map { mapEntityToSong(it) }
    }

    override val artists: Flow<List<Artist>> = db.artistDao().getAllArtists().map { entities ->
        entities.map { Artist(id = it.id, name = it.name, songCount = 0, albumCount = 0) }
    }

    override val albums: Flow<List<Album>> = db.albumDao().getAllAlbums().map { entities ->
        entities.map { Album(id = it.id, title = it.title, artistName = "", artistId = it.artistId, year = it.year?.toString(), songCount = 0, artworkId = it.id) }
    }

    override val folders: Flow<List<Folder>> = kotlinx.coroutines.flow.flowOf(emptyList()) // Requires path logic

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
        }
    }

    override suspend fun searchAll(query: String): com.kaon.music.media.search.SearchResults {
        val results = search(query).first()
        val items = results.mapNotNull { res ->
            when (res.type) {
                "SONG" -> {
                    val songEntity = db.songDao().getSongsByIds(listOf(res.id)).firstOrNull()
                    songEntity?.let {
                        com.kaon.music.media.search.SongResult(
                            song = mapEntityToSong(it),
                            score = res.score
                        )
                    }
                }
                "ALBUM" -> {
                    val albumEntity = db.albumDao().getAlbum(res.id).first()
                    albumEntity?.let {
                        com.kaon.music.media.search.AlbumResult(
                            album = Album(id = it.id, title = it.title, artistName = "", artistId = it.artistId, year = it.year?.toString(), songCount = 0, artworkId = it.id),
                            score = res.score
                        )
                    }
                }
                "ARTIST" -> {
                    val artistEntity = db.artistDao().getArtist(res.id).first()
                    artistEntity?.let {
                        com.kaon.music.media.search.ArtistResult(
                            artist = Artist(id = it.id, name = it.name, songCount = 0, albumCount = 0),
                            score = res.score
                        )
                    }
                }
                else -> null
            }
        }
        return com.kaon.music.media.search.SearchResults(items)
    }

    override suspend fun getSongsByIds(ids: List<Long>): List<Song> {
        return withContext(Dispatchers.IO) {
            db.songDao().getSongsByIds(ids).map { it ->
                mapEntityToSong(it)
            }
        }
    }

    override suspend fun getAllSongIds(): List<Long> {
        return db.songDao().getAllSongIds()
    }

    override fun album(id: Long): Flow<Album?> {
        return db.albumDao().getAlbum(id).map { it?.let { Album(id = it.id, title = it.title, artistName = "", artistId = it.artistId, year = it.year?.toString(), songCount = 0, artworkId = it.id) } }
    }

    override fun artist(id: Long): Flow<Artist?> {
        return db.artistDao().getArtist(id).map { it?.let { Artist(id = it.id, name = it.name, songCount = 0, albumCount = 0) } }
    }

    override fun albumSongs(albumId: Long): Flow<List<Song>> {
        return db.songDao().getSongsByAlbum(albumId).map { entities ->
            entities.map { mapEntityToSong(it) }
        }
    }

    override fun artistAlbums(artistId: Long): Flow<List<Album>> {
        return db.albumDao().getAlbumsByArtist(artistId).map { entities ->
            entities.map { Album(id = it.id, title = it.title, artistName = "", artistId = it.artistId, year = it.year?.toString(), songCount = 0, artworkId = it.id) }
        }
    }

    override fun artistSongs(artistId: Long): Flow<List<Song>> {
        return db.songDao().getSongsByArtist(artistId).map { entities ->
            entities.map { mapEntityToSong(it) }
        }
    }

    override fun albumDetail(id: Long): Flow<AlbumDetail> {
        return album(id).combine(albumSongs(id)) { album, songs ->
            AlbumDetail(
                album = album ?: throw IllegalStateException("Album not found"),
                songs = songs,
                totalDuration = songs.sumOf { it.duration }
            )
        }
    }

    override fun artistDetail(id: Long): Flow<ArtistDetail> {
        return combine(artist(id), artistAlbums(id), artistSongs(id)) { artist, albums, songs ->
            ArtistDetail(
                artist = artist ?: throw IllegalStateException("Artist not found"),
                albums = albums,
                songs = songs,
                totalDuration = songs.sumOf { it.duration }
            )
        }
    }

    override suspend fun genres(): List<Genre> = emptyList() // TODO

    override suspend fun folders(): List<Folder> = emptyList() // TODO

    override suspend fun playlists(): List<Playlist> {
        return emptyList() // TODO: implement playlist mapping
    }

    private fun mapEntityToSong(it: com.kaon.music.media.library.db.entity.SongEntity): Song {
        return Song(
            id = it.id,
            mediaStoreId = it.id,
            uri = it.uri,
            path = "",
            title = it.title,
            artist = "",
            album = "",
            albumArtist = null,
            albumId = it.albumId,
            artistId = it.artistId,
            genre = it.genre,
            composer = it.composer,
            year = null,
            track = it.trackNumber,
            disc = it.discNumber,
            duration = it.duration,
            bitrate = null,
            sampleRate = null,
            mimeType = it.mimeType,
            size = it.size,
            dateAdded = it.dateAdded,
            dateModified = 0,
            lastScanned = 0,
            artworkPath = null,
            favorite = false
        )
    }

    override suspend fun applyLibrarySnapshot(snapshot: LibrarySnapshot) {
        db.librarySyncDao().applyLibrarySnapshot(snapshot)
    }
}

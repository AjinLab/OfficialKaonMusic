package com.kaon.music.media.library.sync

import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.ArtistEntity
import com.kaon.music.media.library.db.entity.SongEntity
import com.kaon.music.media.services.MetadataProvider

class LibraryMapper(private val metadataProvider: MetadataProvider) {

    fun map(mediaStoreSongs: List<MediaStoreSong>): MappedLibrary {
        val artists = mutableMapOf<Long, ArtistEntity>()
        val albums = mutableMapOf<Long, AlbumEntity>()
        val songs = mutableListOf<SongEntity>()

        for (msSong in mediaStoreSongs) {
            if (!artists.containsKey(msSong.artistId)) {
                artists[msSong.artistId] = ArtistEntity(
                    id = msSong.artistId,
                    name = msSong.artist
                )
            }

            if (!albums.containsKey(msSong.albumId)) {
                albums[msSong.albumId] = AlbumEntity(
                    id = msSong.albumId,
                    artistId = msSong.artistId,
                    title = msSong.album,
                    year = null // Can be extracted from metadata if needed
                )
            }

            // We read extra metadata directly from the file if needed, or just use MediaStore data
            val metadata = metadataProvider.read(
                com.kaon.music.media.model.Song(
                    id = 0,
                    mediaStoreId = msSong.mediaStoreId,
                    uri = msSong.uri,
                    path = msSong.path,
                    title = msSong.title,
                    artist = msSong.artist,
                    album = msSong.album,
                    albumArtist = null,
                    albumId = msSong.albumId,
                    artistId = msSong.artistId,
                    genre = null,
                    composer = null,
                    year = null,
                    track = msSong.track,
                    disc = 0,
                    duration = msSong.duration,
                    bitrate = null,
                    sampleRate = null,
                    mimeType = msSong.mimeType,
                    size = msSong.size,
                    dateAdded = msSong.dateAdded,
                    dateModified = msSong.dateModified,
                    lastScanned = 0,
                    artworkPath = null,
                    favorite = false
                )
            )

            songs.add(
                SongEntity(
                    id = msSong.mediaStoreId,
                    uri = msSong.uri,
                    title = metadata.title.takeIf { it.isNotBlank() } ?: msSong.title,
                    artistId = msSong.artistId,
                    albumId = msSong.albumId,
                    albumArtistId = null,
                    duration = msSong.duration,
                    trackNumber = metadata.trackNumber,
                    discNumber = metadata.discNumber,
                    composer = metadata.composer,
                    genre = metadata.genre,
                    dateAdded = msSong.dateAdded,
                    size = msSong.size,
                    mimeType = msSong.mimeType
                )
            )
            
            // Optionally update AlbumEntity with year if found in metadata
            if (metadata.year != null && albums[msSong.albumId]?.year == null) {
                metadata.year.toIntOrNull()?.let { year ->
                    albums[msSong.albumId] = albums[msSong.albumId]!!.copy(year = year)
                }
            }
        }

        return MappedLibrary(
            artists = artists.values.toList(),
            albums = albums.values.toList(),
            songs = songs
        )
    }
}

package com.kaon.music.media.library.sync

import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.ArtistEntity
import com.kaon.music.media.library.db.entity.SongEntity
import com.kaon.music.media.services.MetadataProvider

class LibraryMapper(private val metadataProvider: MetadataProvider) {

    fun map(mediaStoreSongs: List<MediaStoreSong>): MappedLibrary {
        val capacity = mediaStoreSongs.size
        val artists = HashMap<Long, ArtistEntity>(capacity / 2 + 1)
        val albums = HashMap<Long, AlbumEntity>(capacity / 2 + 1)
        val songs = ArrayList<SongEntity>(capacity)

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
                    year = null
                )
            }

            songs.add(
                SongEntity(
                    id = msSong.mediaStoreId,
                    uri = msSong.uri,
                    path = msSong.path,
                    title = msSong.title,
                    artistId = msSong.artistId,
                    albumId = msSong.albumId,
                    albumArtistId = null,
                    duration = msSong.duration,
                    trackNumber = msSong.track,
                    discNumber = 0,
                    composer = null,
                    genre = null,
                    dateAdded = msSong.dateAdded,
                    size = msSong.size,
                    mimeType = msSong.mimeType
                )
            )
        }

        return MappedLibrary(
            artists = artists.values.toList(),
            albums = albums.values.toList(),
            songs = songs
        )
    }
}

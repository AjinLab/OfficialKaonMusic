package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.kaon.music.media.library.db.LibrarySnapshot
import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.ArtistEntity
import com.kaon.music.media.library.db.entity.LibraryStateEntity
import com.kaon.music.media.library.db.entity.SongEntity

@Dao
abstract class LibrarySyncDao {
    @Upsert
    abstract suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Upsert
    abstract suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Upsert
    abstract suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    abstract suspend fun deleteSongs(ids: List<Long>)

    @Query("DELETE FROM albums WHERE NOT EXISTS (SELECT 1 FROM songs WHERE songs.albumId = albums.id)")
    abstract suspend fun deleteOrphanAlbums()

    @Query("DELETE FROM artists WHERE NOT EXISTS (SELECT 1 FROM albums WHERE albums.artistId = artists.id)")
    abstract suspend fun deleteOrphanArtists()

    @Upsert
    abstract suspend fun upsertState(state: LibraryStateEntity)

    @Transaction
    open suspend fun applyLibrarySnapshot(snapshot: LibrarySnapshot) {
        if (snapshot.artists.isNotEmpty()) upsertArtists(snapshot.artists)
        if (snapshot.albums.isNotEmpty()) upsertAlbums(snapshot.albums)
        if (snapshot.songs.isNotEmpty()) upsertSongs(snapshot.songs)
        
        if (snapshot.missingSongIds.isNotEmpty()) {
            deleteSongs(snapshot.missingSongIds)
        }
        
        deleteOrphanAlbums()
        deleteOrphanArtists()
        
        upsertState(snapshot.state)
    }
}

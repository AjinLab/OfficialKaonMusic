package com.kaon.music.media.library.db

import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.ArtistEntity
import com.kaon.music.media.library.db.entity.LibraryStateEntity
import com.kaon.music.media.library.db.entity.SongEntity

data class LibrarySnapshot(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val songs: List<SongEntity>,
    val missingSongIds: List<Long>,
    val state: LibraryStateEntity
)

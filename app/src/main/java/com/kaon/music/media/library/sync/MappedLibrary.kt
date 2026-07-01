package com.kaon.music.media.library.sync

import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.ArtistEntity
import com.kaon.music.media.library.db.entity.SongEntity

data class MappedLibrary(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val songs: List<SongEntity>
)

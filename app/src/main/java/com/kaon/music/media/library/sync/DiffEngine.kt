package com.kaon.music.media.library.sync

import com.kaon.music.media.library.db.entity.SongEntity

class SongDiffEngine {
    private val engine = DiffEngine<SongEntity, Long>(
        keySelector = { it.id },
        contentEquals = { a, b ->
            a.title == b.title &&
            a.path == b.path &&
            a.duration == b.duration &&
            a.size == b.size &&
            a.trackNumber == b.trackNumber &&
            a.discNumber == b.discNumber &&
            a.artistId == b.artistId &&
            a.albumId == b.albumId &&
            a.mimeType == b.mimeType
        }
    )

    fun diff(scannedSongs: List<SongEntity>, dbSongs: List<SongEntity>): Diff<SongEntity> =
        engine.diff(scannedSongs, dbSongs)
}

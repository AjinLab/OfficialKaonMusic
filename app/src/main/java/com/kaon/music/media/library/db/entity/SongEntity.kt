package com.kaon.music.media.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true
        ),
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ],
    indices = [
        Index(value = ["uri"], unique = true),
        Index("albumId"),
        Index("artistId"),
        Index("title"),
        Index("dateAdded")
    ]
)
data class SongEntity(
    @PrimaryKey
    val id: Long,
    val uri: String,
    val path: String,
    val title: String,
    val artistId: Long,
    val albumId: Long,
    val albumArtistId: Long?,
    val duration: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val composer: String?,
    val genre: String?,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String?
)

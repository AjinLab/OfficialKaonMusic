package com.kaon.music.media.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true
        )
    ],
    indices = [
        Index("artistId")
    ]
)
data class AlbumEntity(
    @PrimaryKey
    val id: Long,
    val artistId: Long,
    val title: String,
    val year: Int?
)

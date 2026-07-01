package com.kaon.music.media.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_statistics",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaybackStatisticsEntity(
    @PrimaryKey
    val songId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0,
    val totalPlayTime: Long = 0,
    val rating: Int? = null
)

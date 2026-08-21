package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-owned entity for favorited tracks.
 * References the stable [TrackEntity.trackId].
 */
@Entity(
    tableName = "favorite_tracks",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"], unique = true),
        Index(value = ["added_at"])
    ]
)
data class FavoriteTrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

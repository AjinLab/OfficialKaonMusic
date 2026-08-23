package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a user-created playlist (M5-D1).
 *
 * @property playlistId Stable unique identifier for the playlist.
 * @property name User-defined playlist display title.
 * @property createdAt Epoch millisecond timestamp of playlist creation.
 * @property updatedAt Epoch millisecond timestamp of last modification.
 */
@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["name"])
    ]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

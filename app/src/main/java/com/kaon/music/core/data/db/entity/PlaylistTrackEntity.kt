package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table associating tracks with playlists (M5-D1, M5-D2).
 *
 * Foreign Keys:
 * - `playlist_id` references `playlists(playlist_id)` with `onDelete = ForeignKey.CASCADE`.
 * - Note: No Foreign Key on `track_id` to preserve user playlist membership during file orphan/re-linking windows.
 *
 * @property playlistId ID of the parent playlist.
 * @property trackId ID of the member track (references TrackEntity.trackId).
 * @property position Explicit 0-indexed ordering position within the playlist.
 * @property addedAt Epoch millisecond timestamp of addition.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlist_id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id", "position"]),
        Index(value = ["track_id"])
    ]
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

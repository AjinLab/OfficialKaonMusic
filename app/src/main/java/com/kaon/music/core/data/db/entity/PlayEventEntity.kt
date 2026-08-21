package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only user history recording listening events.
 *
 * Designed according to k3.md D6 / ARCHITECTURE_ATTRIBUTED.md §8:
 * - Append-only table: `id, trackId, eventType (play | skip), playedAt, playedMs`
 * - Aggregates (most played, recently played) are queries over this table.
 */
@Entity(
    tableName = "play_events",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["played_at"]),
        Index(value = ["event_type"])
    ]
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "event_type")
    val eventType: String, // "PLAY" or "SKIP"

    @ColumnInfo(name = "played_at")
    val playedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "played_ms")
    val playedMs: Long
)

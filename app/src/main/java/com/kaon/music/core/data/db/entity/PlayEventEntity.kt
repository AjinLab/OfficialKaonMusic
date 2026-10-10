package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only user history recording listening events.
 *
 * - Append-only table: `id, trackId, eventType (play | skip), playedAt, playedMs`
 * - Aggregates (most played, recently played) are queries over this table.
 *
 * **No foreign key on `track_id`.** History is user-owned; `tracks` is rebuildable from MediaStore
 * (ARCHITECTURE.md §6). The previous `ON DELETE CASCADE` meant a purge of orphaned rows, or any future
 * rebuild of the derived table, silently deleted the listening history that referenced them — the user
 * loses data because a *file* moved. `playlist_tracks` already omitted the track foreign key for this
 * exact reason; this brings history in line.
 *
 * A history row whose track was purged is a dangling reference by design: the aggregate queries join
 * against `tracks`, so it contributes nothing until the track returns, and it costs one row.
 */
@Entity(
    tableName = "play_events",
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["played_at"]),
        Index(value = ["event_type"]),
        Index(value = ["event_type", "track_id", "played_at"])
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
    val playedMs: Long = 0L
)

package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton state entity for queue persistence.
 *
 * Operational Table (from ARCHITECTURE_ATTRIBUTED.md §6 & §7):
 * - Persisted restoration snapshot only.
 * - Restored into Media3 Player on cold start, then discarded as live truth.
 */
@Entity(tableName = "queue_snapshot")
data class QueueSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // Singleton row

    @ColumnInfo(name = "serialized_track_ids")
    val serializedTrackIds: String, // Comma-separated or JSON list of stable track IDs

    @ColumnInfo(name = "current_index")
    val currentIndex: Int,

    @ColumnInfo(name = "current_position_ms")
    val currentPositionMs: Long,

    @ColumnInfo(name = "is_shuffle_enabled")
    val isShuffleEnabled: Boolean,

    @ColumnInfo(name = "repeat_mode")
    val repeatMode: Int,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long = System.currentTimeMillis()
)

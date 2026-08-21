package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Derived Room entity for tracks synchronized from Android MediaStore.
 *
 * Identity Strategy (from k3.md / ARCHITECTURE_ATTRIBUTED.md §2):
 * - [trackId] is the stable Kaon-owned primary key that all user data (favorites, playlists, history) references.
 * - [mediaStoreId] is the sync match key used to match against MediaStore changes.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["media_store_id"], unique = false),
        Index(value = ["duration_ms", "size_bytes"]),
        Index(value = ["title_normalized"]),
        Index(value = ["artist_normalized"]),
        Index(value = ["album_normalized"])
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "track_id")
    val trackId: Long = 0,

    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "album")
    val album: String,

    @ColumnInfo(name = "album_id")
    val albumId: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "date_modified")
    val dateModified: Long,

    @ColumnInfo(name = "relative_path")
    val relativePath: String,

    @ColumnInfo(name = "title_normalized")
    val titleNormalized: String,

    @ColumnInfo(name = "artist_normalized")
    val artistNormalized: String,

    @ColumnInfo(name = "album_normalized")
    val albumNormalized: String,

    @ColumnInfo(name = "is_missing", defaultValue = "0")
    val isMissing: Boolean = false,

    @ColumnInfo(name = "last_seen_timestamp")
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

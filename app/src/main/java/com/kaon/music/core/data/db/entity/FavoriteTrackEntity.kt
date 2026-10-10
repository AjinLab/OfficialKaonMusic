package com.kaon.music.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-owned entity for favorited tracks. References the stable [TrackEntity.trackId].
 *
 * **No foreign key on `track_id`.** Favorites are user-owned; `tracks` is rebuildable from MediaStore
 * (ARCHITECTURE.md §6). The previous `ON DELETE CASCADE` made a purge of orphaned rows delete the
 * user's favorites as a side effect — the user loses a curated list because a *file* moved. Referential
 * integrity is enforced in the repository instead, which is where the "does this track exist, and
 * should it be upserted first" decision belongs now that a favorite may point at provider media.
 */
@Entity(
    tableName = "favorite_tracks",
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

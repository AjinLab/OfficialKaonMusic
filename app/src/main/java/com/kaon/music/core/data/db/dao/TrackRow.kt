package com.kaon.music.core.data.db.dao

/**
 * A track row plus its favorite state, resolved in SQL.
 *
 * ARCHITECTURE.md §6. Favorite state used to be joined in Kotlin: every list flow was
 * `combine(tracks, allFavoriteIds)`, so toggling one favorite re-emitted the complete id list and
 * re-mapped every row of the library. `EXISTS` against the indexed `favorite_tracks.track_id` moves
 * that to the database, where a toggle invalidates the query and Paging re-loads only the pages the
 * UI is actually showing.
 *
 * The projection carries everything a `MediaLocator` is rebuilt from — provider, external id,
 * MediaStore id and relative path — so a paged row maps to exactly the same `Track` as a full entity
 * read, local or remote (ARCHITECTURE.md §3.1).
 *
 * Column names match the SQL projection, not Kotlin convention, because Room maps by name.
 */
data class TrackRow(
    val track_id: Long,
    val media_store_id: Long?,
    val title: String,
    val artist: String,
    val artist_id: Long,
    val album: String,
    val album_id: Long,
    val track_number: Int,
    val disc_number: Int,
    val year: Int,
    val duration_ms: Long,
    val size_bytes: Long,
    val date_modified: Long,
    val date_added: Long,
    val relative_path: String?,
    val is_missing: Boolean,
    val provider: String,
    val external_id: String?,
    val mime_type: String?,
    val artwork_url: String?,
    val is_favorite: Boolean
)

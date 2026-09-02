package com.kaon.music.core.data.sync

import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

/**
 * Result metrics for a completed synchronization pass.
 */
data class SyncResult(
    val totalDiscovered: Int,
    val added: Int,
    val updated: Int,
    val reLinked: Int,
    val markedMissing: Int,
    val purgedOrphans: Int
)

/**
 * Reconciliation Sync Engine.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §2, §5, §18 and Milestone 3 (M3-D4):
 * - Match-by-ID + orphan-marking.
 * - 2-tier deterministic re-linking hierarchy (Tier 1: Path/Title/Artist/Duration -> Tier 2: Title/Artist/Album/Duration/Size).
 * - Ambiguity handling (multiple candidate matches abort re-link).
 * - Orphan retention purge (~30-day window).
 * - Sync-safety guard (verifies storage permission before modifying database).
 */
class SyncEngine(
    private val scanner: MediaStoreScanner,
    private val trackDao: TrackDao
) {

    suspend fun synchronize(
        orphanRetentionDays: Long = 30,
        minDurationMs: Long = 5000L
    ): SyncResult = withContext(Dispatchers.IO) {
        // Sync-safety guard (Milestone 2 Failure-Mode Matrix #4):
        // An empty scan is only meaningful when permission is granted.
        // Never reconcile or mark tracks missing if permission is revoked.
        if (!scanner.hasStoragePermission()) {
            Timber.tag("SyncEngine").w("Sync aborted: Storage permission not granted. Database state untouched.")
            return@withContext SyncResult(0, 0, 0, 0, 0, 0)
        }

        val scanItems = if (minDurationMs == 5000L) {
            scanner.scanAudioFiles()
        } else {
            scanner.scanAudioFiles(minDurationMs = minDurationMs)
        }
        val storedTracks = trackDao.getAllStoredTracks()

        val storedByMediaStoreId = storedTracks.associateBy { it.mediaStoreId }.toMutableMap()
        val scannedMediaStoreIds = scanItems.map { it.mediaStoreId }.toSet()
        val reLinkCandidates = storedTracks.filter { it.isMissing || !scannedMediaStoreIds.contains(it.mediaStoreId) }.toMutableList()

        val toInsert = mutableListOf<TrackEntity>()
        val toUpdate = mutableListOf<TrackEntity>()
        var reLinkedCount = 0
        val matchedStoredTrackIds = mutableSetOf<Long>()

        for (item in scanItems) {
            val existing = storedByMediaStoreId[item.mediaStoreId]
            if (existing != null) {
                // Exact MediaStore ID match
                matchedStoredTrackIds.add(existing.trackId)
                val needsBackfill = existing.dateAdded == 0L && item.dateAdded > 0L
                val needsMimeType = existing.mimeType.isNullOrBlank() && !item.mimeType.isNullOrBlank()
                if (existing.dateModified != item.dateModified || existing.isMissing || needsBackfill || needsMimeType) {
                    val resolvedDateAdded = if (existing.dateAdded == 0L && item.dateAdded > 0L) {
                        Timber.tag("SyncEngine").d("Backfilling date_added for track '${existing.title}' (trackId=${existing.trackId}) to ${item.dateAdded}")
                        item.dateAdded
                    } else {
                        existing.dateAdded
                    }

                    toUpdate.add(
                        existing.copy(
                            title = item.title,
                            artist = item.artist,
                            artistId = item.artistId,
                            album = item.album,
                            albumId = item.albumId,
                            trackNumber = item.trackNumber,
                            discNumber = item.discNumber,
                            year = item.year,
                            durationMs = item.durationMs,
                            sizeBytes = item.sizeBytes,
                            dateModified = item.dateModified,
                            dateAdded = resolvedDateAdded,
                            relativePath = item.relativePath,
                            titleNormalized = normalize(item.title),
                            artistNormalized = normalize(item.artist),
                            albumNormalized = normalize(item.album),
                            isMissing = false,
                            mimeType = item.mimeType,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                // MediaStore ID not recognized: Evaluate deterministic re-linking hierarchy
                val verifiedMatch = findDeterministicReLinkMatch(item, reLinkCandidates)

                if (verifiedMatch != null) {
                    // Unambiguous single match: Re-link stored Kaon trackId to new MediaStoreId
                    trackDao.reLinkTrack(verifiedMatch.trackId, item.mediaStoreId)
                    if ((verifiedMatch.dateAdded == 0L && item.dateAdded > 0L) ||
                        (verifiedMatch.mimeType.isNullOrBlank() && !item.mimeType.isNullOrBlank())
                    ) {
                        trackDao.updateTrack(
                            verifiedMatch.copy(
                                dateAdded = if (verifiedMatch.dateAdded == 0L && item.dateAdded > 0L) item.dateAdded else verifiedMatch.dateAdded,
                                mediaStoreId = item.mediaStoreId,
                                mimeType = item.mimeType,
                                isMissing = false
                            )
                        )
                    }
                    matchedStoredTrackIds.add(verifiedMatch.trackId)
                    reLinkCandidates.remove(verifiedMatch)
                    reLinkedCount++
                    Timber.tag("SyncEngine").d(
                        "Re-linked track '${item.title}' (trackId=${verifiedMatch.trackId}) from old mediaStoreId=${verifiedMatch.mediaStoreId} to new mediaStoreId=${item.mediaStoreId}"
                    )
                } else {
                    // No candidate or ambiguous match: Insert as new track with new Kaon trackId
                    toInsert.add(
                        TrackEntity(
                            mediaStoreId = item.mediaStoreId,
                            title = item.title,
                            artist = item.artist,
                            artistId = item.artistId,
                            album = item.album,
                            albumId = item.albumId,
                            trackNumber = item.trackNumber,
                            discNumber = item.discNumber,
                            year = item.year,
                            durationMs = item.durationMs,
                            sizeBytes = item.sizeBytes,
                            dateModified = item.dateModified,
                            dateAdded = item.dateAdded,
                            relativePath = item.relativePath,
                            titleNormalized = normalize(item.title),
                            artistNormalized = normalize(item.artist),
                            albumNormalized = normalize(item.album),
                            isMissing = false,
                            mimeType = item.mimeType,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        // Apply insertions and updates
        if (toInsert.isNotEmpty()) {
            trackDao.insertTracks(toInsert)
        }
        if (toUpdate.isNotEmpty()) {
            trackDao.updateTracks(toUpdate)
        }

        // Mark vanished tracks as missing (preserves user favorites, playlists, and history)
        val vanishedTrackIds = storedTracks
            .filter { !it.isMissing && !matchedStoredTrackIds.contains(it.trackId) }
            .map { it.trackId }

        if (vanishedTrackIds.isNotEmpty()) {
            trackDao.markTracksMissing(vanishedTrackIds)
            Timber.tag("SyncEngine").d("Marked ${vanishedTrackIds.size} tracks as missing/unavailable")
        }

        // Purge orphaned tracks missing past the retention window
        val purgeCutoff = System.currentTimeMillis() - (orphanRetentionDays * 24 * 60 * 60 * 1000L)
        val purgedCount = trackDao.purgeOrphanedTracks(purgeCutoff)

        val result = SyncResult(
            totalDiscovered = scanItems.size,
            added = toInsert.size,
            updated = toUpdate.size,
            reLinked = reLinkedCount,
            markedMissing = vanishedTrackIds.size,
            purgedOrphans = purgedCount
        )

        Timber.tag("SyncEngine").i("Sync completed: $result")
        result
    }

    /**
     * Deterministic Re-Linking Matching Hierarchy (Locked 2-Tier Model):
     *
     * Tier 1: Exact Path + Normalized Title + Normalized Artist + Duration (±1000ms)
     *         (Handles MediaStore ID churn where file location is unchanged)
     * Tier 2: Normalized Title + Normalized Artist + Normalized Album + Duration (±1000ms) + Exact Size
     *         (Handles moved or renamed files)
     *
     * Ambiguity Rule:
     * If more than one missing candidate satisfies the tier criteria, the match is deemed ambiguous.
     * Re-linking is ABORTED and null is returned, forcing a clean new insert to prevent cross-contamination.
     */
    fun findDeterministicReLinkMatch(
        item: MediaStoreAudioItem,
        candidates: List<TrackEntity>
    ): TrackEntity? {
        if (candidates.isEmpty()) return null

        val normTitle = normalize(item.title)
        val normArtist = normalize(item.artist)
        val normAlbum = normalize(item.album)

        // Tier 1: Relative Path + Title + Artist + Duration (±1000ms)
        val tier1Matches = candidates.filter {
            it.relativePath == item.relativePath &&
                    it.titleNormalized == normTitle &&
                    it.artistNormalized == normArtist &&
                    abs(it.durationMs - item.durationMs) <= DURATION_TOLERANCE_MS
        }
        if (tier1Matches.size == 1) return tier1Matches.first()
        if (tier1Matches.size > 1) return null // Ambiguity abort

        // Tier 2: Title + Artist + Album + Duration (±1000ms) + Exact Size
        val tier2Matches = candidates.filter {
            it.titleNormalized == normTitle &&
                    it.artistNormalized == normArtist &&
                    it.albumNormalized == normAlbum &&
                    abs(it.durationMs - item.durationMs) <= DURATION_TOLERANCE_MS &&
                    it.sizeBytes == item.sizeBytes
        }
        if (tier2Matches.size == 1) return tier2Matches.first()
        if (tier2Matches.size > 1) return null // Ambiguity abort

        return null
    }

    private fun normalize(value: String): String = value.trim().lowercase()

    companion object {
        private const val DURATION_TOLERANCE_MS = 1000L
    }
}

package com.kaon.music.core.data.sync

import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import kotlin.math.abs

/**
 * Result metrics from a library synchronization execution.
 */
data class SyncResult(
    val totalDiscovered: Int,
    val added: Int,
    val updated: Int,
    val reLinked: Int,
    val markedMissing: Int,
    val purgedOrphans: Int
)

class SyncEngine(
    private val scanner: MediaStoreScanner,
    private val trackDao: TrackDao
) {

    /**
     * Executes a full query-based reconcile between Android MediaStore and Room.
     *
     * Idempotent by construction (§5 ARCHITECTURE_ATTRIBUTED.md):
     * - Reads fresh MediaStore cursor.
     * - Diffs in-memory against Room.
     * - Applies delta (inserts, updates, re-links, and missing flags) in Room.
     *
     * Hardened Re-linking Hierarchy:
     * - Evaluates candidates against a strict multi-tier hierarchy.
     * - Ambiguous matches with multiple candidates are never silently merged; they insert as new tracks.
     */
    suspend fun synchronize(orphanRetentionDays: Long = 30): SyncResult = withContext(Dispatchers.IO) {
        val scanItems = scanner.scanAudioFiles()
        val storedTracks = trackDao.getAllStoredTracks()

        val storedByMediaStoreId = storedTracks.associateBy { it.mediaStoreId }.toMutableMap()
        val missingCandidates = storedTracks.filter { it.isMissing }.toMutableList()

        val toInsert = mutableListOf<TrackEntity>()
        val toUpdate = mutableListOf<TrackEntity>()
        var reLinkedCount = 0
        val matchedStoredTrackIds = mutableSetOf<Long>()

        for (item in scanItems) {
            val existing = storedByMediaStoreId[item.mediaStoreId]
            if (existing != null) {
                // Exact MediaStore ID match
                matchedStoredTrackIds.add(existing.trackId)
                if (existing.dateModified != item.dateModified || existing.isMissing) {
                    toUpdate.add(
                        existing.copy(
                            title = item.title,
                            artist = item.artist,
                            album = item.album,
                            albumId = item.albumId,
                            durationMs = item.durationMs,
                            sizeBytes = item.sizeBytes,
                            dateModified = item.dateModified,
                            relativePath = item.relativePath,
                            titleNormalized = normalize(item.title),
                            artistNormalized = normalize(item.artist),
                            albumNormalized = normalize(item.album),
                            isMissing = false,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                // MediaStore ID not recognized: Evaluate deterministic re-linking hierarchy
                val verifiedMatch = findDeterministicReLinkMatch(item, missingCandidates)

                if (verifiedMatch != null) {
                    // Unambiguous single match: Re-link stored Kaon trackId to new MediaStoreId
                    trackDao.reLinkTrack(verifiedMatch.trackId, item.mediaStoreId)
                    matchedStoredTrackIds.add(verifiedMatch.trackId)
                    missingCandidates.remove(verifiedMatch)
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
                            album = item.album,
                            albumId = item.albumId,
                            durationMs = item.durationMs,
                            sizeBytes = item.sizeBytes,
                            dateModified = item.dateModified,
                            relativePath = item.relativePath,
                            titleNormalized = normalize(item.title),
                            artistNormalized = normalize(item.artist),
                            albumNormalized = normalize(item.album),
                            isMissing = false,
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
     * Deterministic Re-Linking Matching Hierarchy:
     *
     * Tier 1: Exact Path + Normalized Title + Normalized Artist + Duration (±1000ms)
     * Tier 2: Normalized Title + Normalized Artist + Normalized Album + Duration (±1000ms) + Exact Size
     * Tier 3: Normalized Title + Duration (±1000ms) + Exact Size
     *
     * Ambiguity Rule:
     * If multiple candidates match at the highest qualifying tier, return null (do not guess).
     */
    private fun findDeterministicReLinkMatch(
        item: MediaStoreAudioItem,
        candidates: List<TrackEntity>
    ): TrackEntity? {
        if (candidates.isEmpty()) return null

        val titleNorm = normalize(item.title)
        val artistNorm = normalize(item.artist)
        val albumNorm = normalize(item.album)
        val pathNorm = normalize(item.relativePath)
        val durationToleranceMs = 1000L

        // Filter basic physical envelope (duration)
        val envelopeCandidates = candidates.filter {
            abs(it.durationMs - item.durationMs) <= durationToleranceMs
        }
        if (envelopeCandidates.isEmpty()) return null

        // Tier 1: Path + Title + Artist
        if (pathNorm.isNotBlank()) {
            val tier1Matches = envelopeCandidates.filter {
                normalize(it.relativePath) == pathNorm &&
                        it.titleNormalized == titleNorm &&
                        it.artistNormalized == artistNorm
            }
            if (tier1Matches.size == 1) return tier1Matches.first()
            if (tier1Matches.size > 1) return null // Ambiguity: do not guess
        }

        // Tier 2: Title + Artist + Album + Exact Size
        val tier2Matches = envelopeCandidates.filter {
            it.sizeBytes == item.sizeBytes &&
                    it.titleNormalized == titleNorm &&
                    it.artistNormalized == artistNorm &&
                    it.albumNormalized == albumNorm
        }
        if (tier2Matches.size == 1) return tier2Matches.first()
        if (tier2Matches.size > 1) return null // Ambiguity: do not guess

        // Tier 3: Title + Exact Size
        val tier3Matches = envelopeCandidates.filter {
            it.sizeBytes == item.sizeBytes &&
                    it.titleNormalized == titleNorm
        }
        if (tier3Matches.size == 1) return tier3Matches.first()

        // No unique deterministic match
        return null
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }
}

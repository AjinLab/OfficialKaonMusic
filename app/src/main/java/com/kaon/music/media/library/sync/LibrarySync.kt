package com.kaon.music.media.library.sync

import android.util.Log
import com.kaon.music.media.library.MediaRepository
import com.kaon.music.media.library.db.entity.LibraryStateEntity
import com.kaon.music.media.library.db.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock

class LibrarySync(
    private val scanner: LibraryScanner,
    private val mapper: LibraryMapper,
    private val repository: MediaRepository,
    private val generationTracker: GenerationTracker,
    private val clock: Clock
) {
    private val songDiffEngine = SongDiffEngine()

    suspend fun sync(): ScanResult = withContext(Dispatchers.IO) {
        val storedSongIds = repository.getAllSongIds().toSet()
        val lastState = repository.getLibraryState()
        val currentGen = generationTracker.currentGeneration()
        val now = clock.instant().toEpochMilli()

        val shouldRunIncremental = lastState != null && 
                lastState.lastScanGeneration > 0 && 
                lastState.lastScanVolume == currentGen.volumeName

        val scannedSongs: List<MediaStoreSong>
        val deletedSongIds: List<Long>
        val lastReconciledTime: Long

        if (shouldRunIncremental) {
            val state = requireNotNull(lastState)
            // Incremental Scan: query changes since lastScanGeneration
            scannedSongs = scanner.scanMediaStore(state.lastScanGeneration)
            
            // Check if we should reconcile deletions (every 24 hours, or generation difference > 1000)
            val elapsed = now - state.lastDeletionReconciliation
            val genDiff = currentGen.generation - state.lastScanGeneration
            val forceReconciliation = elapsed > (24L * 60L * 60L * 1000L) || genDiff > 1000L

            if (forceReconciliation) {
                Log.d("KAON", "Running incremental sync deletion reconciliation (elapsed: $elapsed ms, genDiff: $genDiff)")
                val activeIds = scanner.scanActiveMediaStoreIds()
                deletedSongIds = (storedSongIds - activeIds).toList()
                lastReconciledTime = now
            } else {
                deletedSongIds = emptyList()
                lastReconciledTime = state.lastDeletionReconciliation
            }
        } else {
            // Full Scan
            Log.d("KAON", "Running full media library sync scan")
            scannedSongs = scanner.scanMediaStore()
            val activeIds = scannedSongs.map { it.mediaStoreId }.toSet()
            deletedSongIds = (storedSongIds - activeIds).toList()
            lastReconciledTime = now
        }

        // Map scanned songs using LibraryMapper
        val mappedLibrary = mapper.map(scannedSongs)

        // Diff song entities to determine added, updated, removed, unchanged
        val dbSongs = if (shouldRunIncremental) {
            repository.getSongEntitiesByIds(mappedLibrary.songs.map { it.id })
        } else {
            repository.getAllSongsList()
        }
        val songDiff = songDiffEngine.diff(mappedLibrary.songs, dbSongs)

        // Adjust Room state entity
        val finalRemovedIds = if (shouldRunIncremental) {
            deletedSongIds
        } else {
            (songDiff.removed.map { it.id } + deletedSongIds).distinct()
        }

        val totalSongCount = storedSongIds.size + songDiff.added.size - (if (shouldRunIncremental) 0 else songDiff.removed.size) - deletedSongIds.size
        val newState = LibraryStateEntity(
            id = 0,
            lastScan = now,
            songCount = totalSongCount,
            schemaVersion = 4,
            legacyMigrated = true,
            lastScanGeneration = currentGen.generation,
            lastScanVolume = currentGen.volumeName,
            lastDeletionReconciliation = lastReconciledTime
        )

        // Apply Room transaction
        repository.applyLibraryDiff(
            addedSongs = songDiff.added,
            updatedSongs = songDiff.updated,
            removedSongIds = finalRemovedIds,
            artists = mappedLibrary.artists,
            albums = mappedLibrary.albums,
            state = newState
        )

        ScanResult(
            added = songDiff.added.size,
            updated = songDiff.updated.size,
            removed = finalRemovedIds.size,
            unchanged = if (shouldRunIncremental) {
                storedSongIds.size - songDiff.updated.size - finalRemovedIds.size
            } else {
                songDiff.unchanged
            }
        )
    }
}

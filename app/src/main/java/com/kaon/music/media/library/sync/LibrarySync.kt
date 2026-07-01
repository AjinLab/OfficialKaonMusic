package com.kaon.music.media.library.sync

import com.kaon.music.media.library.MediaRepository
import com.kaon.music.media.library.db.LibrarySnapshot
import com.kaon.music.media.library.db.entity.LibraryStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibrarySync(
    private val scanner: LibraryScanner,
    private val mapper: LibraryMapper,
    private val repository: MediaRepository
) {
    suspend fun sync(): ScanResult = withContext(Dispatchers.IO) {
        val existingSongIds = repository.getAllSongIds().toSet()

        // 1. Scan MediaStore
        val rawSongs = scanner.scanMediaStore()
        val rawSongIds = rawSongs.map { it.mediaStoreId }.toSet()

        // 2. Build Hash Maps / Map to Entities (deduplicates automatically in Mapper)
        val mappedLibrary = mapper.map(rawSongs)
        
        // 3. Find Missing Songs
        val missingSongIds = (existingSongIds - rawSongIds).toList()

        // 4. Update LibraryState
        val newState = LibraryStateEntity(
            id = 0,
            lastScan = System.currentTimeMillis(),
            songCount = rawSongs.size,
            schemaVersion = 1,
            legacyMigrated = true // Since we are here, we consider it migrated or active
        )

        val snapshot = LibrarySnapshot(
            artists = mappedLibrary.artists,
            albums = mappedLibrary.albums,
            songs = mappedLibrary.songs,
            missingSongIds = missingSongIds,
            state = newState
        )

        // 5. Apply transaction atomically
        repository.applyLibrarySnapshot(snapshot)

        ScanResult(
            added = 0, // Simplified for now since upsert handles both
            updated = 0,
            removed = missingSongIds.size,
            unchanged = 0
        )
    }
}

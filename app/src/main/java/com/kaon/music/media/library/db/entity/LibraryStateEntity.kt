package com.kaon.music.media.library.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_state")
data class LibraryStateEntity(
    @PrimaryKey
    val id: Int = 0,
    val lastScan: Long = 0,
    val songCount: Int = 0,
    val schemaVersion: Int = 1,
    val legacyMigrated: Boolean = false,
    val lastScanGeneration: Long = 0L,
    val lastScanVolume: String = "",
    val lastDeletionReconciliation: Long = 0L
)

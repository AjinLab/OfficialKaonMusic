package com.kaon.music.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lightweight automated architecture boundary validator.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §18 and Task Constraint §21:
 * Enforces that feature screens/ViewModels/components NEVER directly depend on:
 * - Room entities (TrackEntity, FavoriteTrackEntity, PlayEventEntity, PlaylistEntity, PlaylistTrackEntity, QueueSnapshotEntity)
 * - Room DAOs (TrackDao, FavoriteDao, PlayEventDao, PlaylistDao, QueueSnapshotDao)
 * - MediaStore internals (android.provider.MediaStore, android.database.Cursor)
 * - ExoPlayer runtime implementation (ExoPlayer, MediaItem)
 */
class ArchitectureBoundaryTest {

    private val forbiddenImportPatterns = listOf(
        "com.kaon.music.core.data.db.entity",
        "com.kaon.music.core.data.db.dao",
        "androidx.media3.exoplayer.ExoPlayer",
        "androidx.media3.common.MediaItem",
        "android.database.Cursor",
        "com.landofoz.musicmeta"
    )

    private val forbiddenTypeNames = listOf(
        "TrackEntity",
        "FavoriteTrackEntity",
        "PlayEventEntity",
        "PlaylistEntity",
        "PlaylistTrackEntity",
        "QueueSnapshotEntity",
        "TrackDao",
        "FavoriteDao",
        "PlayEventDao",
        "PlaylistDao",
        "QueueSnapshotDao"
    )

    @Test
    fun featurePackages_doNotContainForbiddenImplementationDependencies() {
        val projectRoot = findProjectRoot()
        val featureDir = File(projectRoot, "app/src/main/java/com/kaon/music/feature")
        assertTrue("Feature directory exists at ${featureDir.absolutePath}", featureDir.exists() && featureDir.isDirectory)

        val violations = mutableListOf<String>()

        featureDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                // Check imports
                if (trimmed.startsWith("import ")) {
                    for (forbidden in forbiddenImportPatterns) {
                        if (trimmed.contains(forbidden)) {
                            violations.add("${file.name}:${index + 1} -> Forbidden import '$forbidden'")
                        }
                    }
                } else if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                    // Check direct type usage outside comments
                    for (forbiddenType in forbiddenTypeNames) {
                        // Word boundary matching
                        val regex = Regex("\\b$forbiddenType\\b")
                        if (regex.containsMatchIn(trimmed)) {
                            violations.add("${file.name}:${index + 1} -> Forbidden entity/DAO reference '$forbiddenType'")
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Boundary Violations found in feature layer:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun findProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists() || File(current, "gradlew").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File(".").canonicalFile
    }
}

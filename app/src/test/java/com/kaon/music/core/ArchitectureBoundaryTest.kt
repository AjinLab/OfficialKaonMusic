package com.kaon.music.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated architecture boundary validator.
 *
 * ARCHITECTURE.md §2.1 defines the dependency direction; this test enforces it. It previously walked
 * only `feature/`, which is why two backward edges into `app` (`core/playback` → `MainActivity`,
 * `core/designsystem` → `KaonApplication`) and the `feature` → `:innertube` layer skip all went
 * undetected.
 */
class ArchitectureBoundaryTest {

    private data class Rule(
        val description: String,
        val packagePath: String,
        val forbiddenSubstrings: List<String>,
        val allowedFiles: Set<String> = emptySet()
    )

    private val roomAndPlatformInternals = listOf(
        "com.kaon.music.core.data.db.entity",
        "com.kaon.music.core.data.db.dao",
        "androidx.media3.exoplayer.ExoPlayer",
        "androidx.media3.common.MediaItem",
        "android.database.Cursor",
        "com.landofoz.musicmeta"
    )

    private val rules = listOf(
        Rule(
            description = "feature/* must not reach persistence, playback internals, or a provider SDK",
            packagePath = "feature",
            forbiddenSubstrings = roomAndPlatformInternals + listOf(
                // Catalog access belongs behind a repository, not in a ViewModel. Still present in
                // SearchViewModel; migration phase 4 introduces CatalogRepository and this
                // exemption is removed then.
                "com.metrolist.innertube.YouTube"
            ),
            allowedFiles = setOf("SearchViewModel.kt")
        ),
        Rule(
            description = "core/playback must not import extraction internals (ARCHITECTURE.md §7)",
            packagePath = "core/playback",
            forbiddenSubstrings = listOf(
                "com.metrolist.innertubex.cipher",
                "com.metrolist.innertubex.extraction.InnerTubeExtractor",
                "com.metrolist.innertubex.extraction.TokenProvider",
                "com.metrolist.innertube.models.response",
                "com.metrolist.innertubex.extraction.ContentHints",
                "com.kaon.music.core.online.potoken"
            )
        ),
        Rule(
            description = "core/* must not depend on the app layer (ARCHITECTURE.md §2.1 rule 1)",
            packagePath = "core",
            forbiddenSubstrings = listOf("com.kaon.music.app."),
            // Both are real violations awaiting later phases:
            //  - KaonPlaybackService builds a PendingIntent for MainActivity (phase 5 injects it)
            //  - ArtworkImage resolves MetadataRepository from the Application (phase 6 moves it)
            allowedFiles = setOf("KaonPlaybackService.kt", "ArtworkImage.kt")
        ),
        Rule(
            description = "core/online must not depend on data, playback, or UI",
            packagePath = "core/online",
            forbiddenSubstrings = listOf(
                "com.kaon.music.core.data",
                "com.kaon.music.core.playback",
                "com.kaon.music.core.designsystem",
                "com.kaon.music.feature"
            )
        ),
        Rule(
            description = "core/policy must stay pure: no Android, no Room, no playback",
            packagePath = "core/policy",
            forbiddenSubstrings = listOf(
                "android.",
                "androidx.",
                "com.kaon.music.core.data.db",
                "com.kaon.music.core.playback"
            )
        )
    )

    @Test
    fun packageDependenciesFollowTheDeclaredDirection() {
        val violations = mutableListOf<String>()

        for (rule in rules) {
            val dir = File(findProjectRoot(), "app/src/main/java/com/kaon/music/${rule.packagePath}")
            assertTrue("Missing package for rule '${rule.description}': ${dir.absolutePath}", dir.isDirectory)

            dir.walkTopDown()
                .filter { it.extension == "kt" && it.name !in rule.allowedFiles }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("import ")) return@forEachIndexed
                        rule.forbiddenSubstrings
                            .filter { trimmed.contains(it) }
                            .forEach { forbidden ->
                                violations += "${file.name}:${index + 1} imports '$forbidden' " +
                                    "— violates: ${rule.description}"
                            }
                    }
                }
        }

        assertTrue("Architecture boundary violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun featureLayerDoesNotReferenceRoomTypesDirectly() {
        val featureDir = File(findProjectRoot(), "app/src/main/java/com/kaon/music/feature")
        val forbiddenTypeNames = listOf(
            "TrackEntity", "FavoriteTrackEntity", "PlayEventEntity", "PlaylistEntity",
            "PlaylistTrackEntity", "QueueSnapshotEntity",
            "TrackDao", "FavoriteDao", "PlayEventDao", "PlaylistDao", "QueueSnapshotDao"
        )
        val patterns = forbiddenTypeNames.associateWith { Regex("\\b$it\\b") }

        val violations = mutableListOf<String>()
        featureDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                    return@forEachIndexed
                }
                patterns.forEach { (name, regex) ->
                    if (regex.containsMatchIn(trimmed)) {
                        violations += "${file.name}:${index + 1} references Room type '$name'"
                    }
                }
            }
        }

        assertTrue("Room types leaked into the feature layer:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    /**
     * ARCHITECTURE.md §3.2: progress must not be observed at screen level. Screen state objects that
     * fold in the 500 ms position tick are what made full-library sorting run twice per second.
     */
    @Test
    fun screenStateDoesNotCarryPlaybackPosition() {
        val featureDir = File(findProjectRoot(), "app/src/main/java/com/kaon/music/feature")
        val forbidden = listOf("PlaybackProgress", "playbackPositionMs", "bufferedPositionMs")
        val playerOwned = setOf("PlayerViewModel.kt", "FullPlayerOverlay.kt", "MiniPlayer.kt")

        val violations = mutableListOf<String>()
        featureDir.walkTopDown()
            .filter { it.extension == "kt" && it.name !in playerOwned }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    forbidden.filter { line.contains(it) }.forEach { term ->
                        violations += "${file.name}:${index + 1} references '$term' outside the player"
                    }
                }
            }

        assertTrue(
            "Playback progress observed outside the player (ARCHITECTURE.md §3.2):\n${violations.joinToString("\n")}",
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

package com.kaon.music.core.extraction

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Module boundary rules for `:core:extraction` (ARCHITECTURE.md §2.1, Phase 5 §22).
 *
 * These are enforced by reading sources rather than by compilation, because the dangerous imports
 * would *compile* — that is what makes them drift. Reading the tree is the same mechanism
 * `ArchitectureBoundaryTest` uses for package rules inside `:app`.
 */
class ExtractionModuleBoundaryTest {

    // Gradle runs this module's tests with cwd = the module dir; :app's runner uses the repo root.
    private val sourceRoot: File =
        sequenceOf(File("src/main/kotlin"), File("core-extraction/src/main/kotlin"))
            .firstOrNull { it.isDirectory }
            ?: File("src/main/kotlin")

    private fun sources(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun assertNoImport(forbidden: String, because: String) {
        val offenders = sources()
            .map { it.relativeTo(sourceRoot).path to it.readText() }
            .filter { (_, text) -> text.contains("import $forbidden") }
            .map { (path, _) -> path }
        assertTrue("Forbidden import '$forbidden' ($because) in: $offenders", offenders.isEmpty())
    }

    @Test
    fun extractionNeverImportsTheApp() {
        assertNoImport("com.kaon.music.app", "extraction must not see the application graph")
    }

    @Test
    fun extractionNeverImportsPlaybackOrDataOrFeature() {
        assertNoImport("com.kaon.music.core.playback", "playback depends on extraction, never the reverse")
        assertNoImport("com.kaon.music.core.data", "Room belongs to :app, not to extraction")
        assertNoImport("com.kaon.music.core.designsystem", "UI belongs to :app")
        assertNoImport("com.kaon.music.feature", "feature code belongs to :app")
    }

    @Test
    fun extractionNeverReachesForAppGlobals() {
        val offenders = sources()
            .filter { it.readText().contains("KaonApplication") || it.readText().contains("AppContainer") }
            .map { it.relativeTo(sourceRoot).path }
        assertTrue("extraction must not touch app service locators: $offenders", offenders.isEmpty())
    }
}

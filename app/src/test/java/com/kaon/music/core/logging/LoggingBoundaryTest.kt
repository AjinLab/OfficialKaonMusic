package com.kaon.music.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards ARCHITECTURE.md §5.3: no secret material may be interpolated into a log statement, in any
 * build. Four categories used to ship live in release — the full poToken (in two encodings), the raw
 * GenerateIT integrity-token response, the BotGuard attestation response, and visitorData.
 */
class LoggingBoundaryTest {

    /** Identifiers whose value must never be interpolated directly into a log message. */
    private val secretIdentifiers = listOf(
        "poToken", "poTokenU8", "integrityToken", "botguardResponse",
        "visitorData", "sessionId", "cookie", "signatureCipher"
    )

    private val logCall = Regex("""Timber\s*(\.tag\([^)]*\))?\s*\.[vdiwe]\s*\(""")

    @Test
    fun noSecretIsInterpolatedIntoALogStatement() {
        val violations = mutableListOf<String>()

        mainSources().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (!logCall.containsMatchIn(line)) return@forEachIndexed
                secretIdentifiers.forEach { secret ->
                    // A bare `$secret` or `${secret...}` that is not routed through Redact.
                    val direct = Regex("""\$\{?$secret\b(?![^}]*Redact)""")
                    if (direct.containsMatchIn(line) && !line.contains("Redact.")) {
                        violations += "${file.name}:${index + 1} logs '$secret' without redaction"
                    }
                }
            }
        }

        assertTrue(
            "Secret material reachable from a log statement (ARCHITECTURE.md §5.3):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun productionCodeDoesNotUsePrintln() {
        // Bare println only. `Log.println` is the platform sink ReleaseTree writes to.
        val barePrintln = Regex("""(^|[^.\w])println\(""")
        val violations = mainSources()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (barePrintln.containsMatchIn(line)) "${file.name}:${index + 1}" else null
                }
            }
            .toList()

        assertTrue(
            "println() bypasses Timber and cannot be stripped from release builds:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun debugTreeIsGatedOnBuildConfig() {
        val application = File(projectRoot, "app/src/main/java/com/kaon/music/app/KaonApplication.kt").readText()
        assertTrue(
            "Timber.plant must select a Tree based on BuildConfig.DEBUG",
            application.contains("BuildConfig.DEBUG") && application.contains("ReleaseTree()")
        )

        val proguard = File(projectRoot, "app/proguard-rules.pro").readText()
        assertTrue(
            "proguard-rules.pro must strip Timber.v/d from release builds",
            proguard.contains("-assumenosideeffects") && proguard.contains("timber.log.Timber")
        )
    }

    @Test
    fun redactNeverEmitsTheInputValue() {
        val token = "AbCdEf0123456789SECRETVALUE"
        val redacted = Redact.secret(token)

        assertFalse("Redact.secret must not contain the input", redacted.contains(token))
        assertFalse(
            "Redact.secret must not leak a usable prefix",
            redacted.contains(token.take(8))
        )
        assertTrue("Redact.secret should report the length for correlation", redacted.contains("len=${token.length}"))

        assertEquals("<null>", Redact.secret(null))
        assertEquals("<empty>", Redact.secret(""))
    }

    @Test
    fun redactUrlKeepsHostAndDropsQuery() {
        val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1700000000&sig=SECRET&n=TOKEN"
        val redacted = Redact.url(url)

        assertEquals("https://rr3---sn-abc.googlevideo.com/<redacted>", redacted)
        assertFalse(redacted.contains("SECRET"))
        assertFalse(redacted.contains("TOKEN"))
        assertFalse(redacted.contains("expire"))
    }

    private fun mainSources(): Sequence<File> =
        File(projectRoot, "app/src/main/java")
            .walkTopDown()
            .filter { it.extension == "kt" }
            // The rules are declared here; matching them against this file's own literals is noise.
            .filterNot { it.name == "Redact.kt" }

    private val projectRoot: File
        get() {
            var current: File? = File(".").canonicalFile
            while (current != null) {
                if (File(current, "settings.gradle.kts").exists()) return current
                current = current.parentFile
            }
            return File(".").canonicalFile
        }
}

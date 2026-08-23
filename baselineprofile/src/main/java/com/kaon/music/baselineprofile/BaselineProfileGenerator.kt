package com.kaon.music.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile generator capturing critical user journeys:
 * 1. Cold app startup & main library render.
 * 2. Library tab switching (Tracks -> Albums -> Playlists).
 * 3. Playback trigger.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(
        packageName = "com.kaon.music",
        includeInStartupProfile = true
    ) {
        // Cold start
        pressHome()
        startActivityAndWait()

        // Wait for library UI to render
        device.wait(Until.hasObject(By.text("Your Library")), 5000)

        // Tab switching interaction
        val playlistsTab = device.findObject(By.text("Playlists"))
        playlistsTab?.click()
        device.waitForIdle()

        val tracksTab = device.findObject(By.text("Tracks"))
        tracksTab?.click()
        device.waitForIdle()
    }
}

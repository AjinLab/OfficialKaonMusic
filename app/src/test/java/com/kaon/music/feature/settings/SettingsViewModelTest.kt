package com.kaon.music.feature.settings

import com.kaon.music.core.data.db.dao.FakeFavoriteDaoBase
import com.kaon.music.core.data.db.dao.FakePlayEventDaoBase
import com.kaon.music.core.data.db.dao.FakeTrackDaoBase
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.repository.HistoryRepository
import com.kaon.music.core.data.repository.SettingsRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.repository.UserSettings
import com.kaon.music.core.data.sync.MediaStoreScanner
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.extraction.model.AudioQuality
import com.kaon.music.core.extraction.model.AudioType
import com.kaon.music.feature.library.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSettingsRepository {
        val userSettingsState = MutableStateFlow(UserSettings())

        fun setStreamingQuality(quality: AudioQuality) {
            userSettingsState.value = userSettingsState.value.copy(streamingQuality = quality)
        }

        fun setPreferredAudioType(audioType: com.kaon.music.core.extraction.model.AudioType) {
            userSettingsState.value = userSettingsState.value.copy(preferredAudioType = audioType)
        }

        fun setThemeMode(mode: String) {
            userSettingsState.value = userSettingsState.value.copy(themeMode = mode)
        }

        fun setAccentColor(color: String) {
            userSettingsState.value = userSettingsState.value.copy(accentColor = color)
        }

        fun setMinDurationSeconds(seconds: Int) {
            userSettingsState.value = userSettingsState.value.copy(minDurationSeconds = seconds)
        }

        fun setShowFormatBadges(enabled: Boolean) {
            userSettingsState.value = userSettingsState.value.copy(showFormatBadges = enabled)
        }

        fun setShowLosslessBadges(enabled: Boolean) {
            userSettingsState.value = userSettingsState.value.copy(showLosslessBadges = enabled)
        }

        fun setPreResolveNextTracks(enabled: Boolean) {
            userSettingsState.value = userSettingsState.value.copy(preResolveNextTracks = enabled)
        }

        fun setWifiOnlyStreaming(enabled: Boolean) {
            userSettingsState.value = userSettingsState.value.copy(wifiOnlyStreaming = enabled)
        }

        fun setRecordHistory(enabled: Boolean) {
            userSettingsState.value = userSettingsState.value.copy(recordHistory = enabled)
        }

        fun setPauseOnFocusLoss(enabled: Boolean) {
            userSettingsState.value = userSettingsState.value.copy(pauseOnFocusLoss = enabled)
        }

        fun setSkipSilence(enabled: Boolean) {
            userSettingsState.value = userSettingsState.value.copy(skipSilence = enabled)
        }

        fun setCrossfadeSeconds(seconds: Int) {
            userSettingsState.value = userSettingsState.value.copy(crossfadeSeconds = seconds)
        }

        fun setLastFmApiKey(key: String) {
            userSettingsState.value = userSettingsState.value.copy(lastFmApiKey = key)
        }
    }

    private class FakeTestTrackDao : FakeTrackDaoBase() {
        override suspend fun insertTrack(track: TrackEntity): Long = track.trackId
        override suspend fun insertTracks(tracks: List<TrackEntity>): List<Long> = tracks.map { it.trackId }
    }

    private class FakeTestFavoriteDao : FakeFavoriteDaoBase()

    private class FakeTestPlayEventDao : FakePlayEventDaoBase() {
        var clearCount = 0
        override suspend fun insertEvent(event: PlayEventEntity): Long = 1L
        override suspend fun clearAllEvents(): Int {
            clearCount++
            return 10
        }
    }

    private class FakeMediaStoreScanner : MediaStoreScanner(object : android.content.ContextWrapper(null) {}) {
        override fun hasStoragePermission(): Boolean = true
        override fun scanAudioFiles(): List<com.kaon.music.core.data.sync.MediaStoreAudioItem> = emptyList()
    }

    @Test
    fun sectionNavigation_navigatesToSectionAndBack() {
        val dummyContext = object : android.content.ContextWrapper(null) {}
        val fakeTrackDao = FakeTestTrackDao()
        val fakeFavDao = FakeTestFavoriteDao()
        val fakePlayDao = FakeTestPlayEventDao()
        val trackRepository = TrackRepository(
            trackDao = fakeTrackDao,
            favoriteDao = fakeFavDao,
            syncEngine = SyncEngine(
                scanner = FakeMediaStoreScanner(),
                trackDao = fakeTrackDao
            ),
            playEventDao = fakePlayDao
        )
        val historyRepository = HistoryRepository(fakePlayDao)

        // Test section navigation flow
        val sectionState = MutableStateFlow<SettingsSection?>(null)
        assertNull(sectionState.value)

        sectionState.value = SettingsSection.AUDIO_PLAYBACK
        assertEquals(SettingsSection.AUDIO_PLAYBACK, sectionState.value)

        sectionState.value = SettingsSection.LIBRARY_STORAGE
        assertEquals(SettingsSection.LIBRARY_STORAGE, sectionState.value)

        sectionState.value = SettingsSection.APPEARANCE_THEME
        assertEquals(SettingsSection.APPEARANCE_THEME, sectionState.value)

        sectionState.value = SettingsSection.ONLINE_STREAMING
        assertEquals(SettingsSection.ONLINE_STREAMING, sectionState.value)

        sectionState.value = SettingsSection.HISTORY_PRIVACY
        assertEquals(SettingsSection.HISTORY_PRIVACY, sectionState.value)

        sectionState.value = SettingsSection.ABOUT_INFO
        assertEquals(SettingsSection.ABOUT_INFO, sectionState.value)

        sectionState.value = null
        assertNull(sectionState.value)
    }

    @Test
    fun userSettings_updatesValuesCorrectly() {
        val fakeRepo = FakeSettingsRepository()

        assertEquals(AudioQuality.AUTO, fakeRepo.userSettingsState.value.streamingQuality)
        fakeRepo.setStreamingQuality(AudioQuality.HIGH)
        assertEquals(AudioQuality.HIGH, fakeRepo.userSettingsState.value.streamingQuality)

        assertEquals(com.kaon.music.core.extraction.model.AudioType.AUTO, fakeRepo.userSettingsState.value.preferredAudioType)
        fakeRepo.setPreferredAudioType(com.kaon.music.core.extraction.model.AudioType.OPUS)
        assertEquals(com.kaon.music.core.extraction.model.AudioType.OPUS, fakeRepo.userSettingsState.value.preferredAudioType)

        assertEquals("DARK", fakeRepo.userSettingsState.value.themeMode)
        fakeRepo.setThemeMode("AMOLED")
        assertEquals("AMOLED", fakeRepo.userSettingsState.value.themeMode)

        assertEquals("CORAL", fakeRepo.userSettingsState.value.accentColor)
        fakeRepo.setAccentColor("VIOLET")
        assertEquals("VIOLET", fakeRepo.userSettingsState.value.accentColor)

        assertEquals(5, fakeRepo.userSettingsState.value.minDurationSeconds)
        fakeRepo.setMinDurationSeconds(30)
        assertEquals(30, fakeRepo.userSettingsState.value.minDurationSeconds)

        assertEquals(true, fakeRepo.userSettingsState.value.showFormatBadges)
        fakeRepo.setShowFormatBadges(false)
        assertEquals(false, fakeRepo.userSettingsState.value.showFormatBadges)

        assertEquals(true, fakeRepo.userSettingsState.value.showLosslessBadges)
        fakeRepo.setShowLosslessBadges(false)
        assertEquals(false, fakeRepo.userSettingsState.value.showLosslessBadges)

        assertEquals(0, fakeRepo.userSettingsState.value.crossfadeSeconds)
        fakeRepo.setCrossfadeSeconds(5)
        assertEquals(5, fakeRepo.userSettingsState.value.crossfadeSeconds)

        assertEquals(true, fakeRepo.userSettingsState.value.preResolveNextTracks)
        fakeRepo.setPreResolveNextTracks(false)
        assertEquals(false, fakeRepo.userSettingsState.value.preResolveNextTracks)

        assertEquals(false, fakeRepo.userSettingsState.value.wifiOnlyStreaming)
        fakeRepo.setWifiOnlyStreaming(true)
        assertEquals(true, fakeRepo.userSettingsState.value.wifiOnlyStreaming)

        assertEquals(true, fakeRepo.userSettingsState.value.recordHistory)
        fakeRepo.setRecordHistory(false)
        assertEquals(false, fakeRepo.userSettingsState.value.recordHistory)

        assertEquals(true, fakeRepo.userSettingsState.value.pauseOnFocusLoss)
        fakeRepo.setPauseOnFocusLoss(false)
        assertEquals(false, fakeRepo.userSettingsState.value.pauseOnFocusLoss)

        assertEquals(false, fakeRepo.userSettingsState.value.skipSilence)
        fakeRepo.setSkipSilence(true)
        assertEquals(true, fakeRepo.userSettingsState.value.skipSilence)

        assertEquals("", fakeRepo.userSettingsState.value.lastFmApiKey)
        fakeRepo.setLastFmApiKey("abc123lastfm")
        assertEquals("abc123lastfm", fakeRepo.userSettingsState.value.lastFmApiKey)
    }

    @Test
    fun getKaonColors_returnsCorrectColorsForThemesAndAccents() {
        val darkCoral = com.kaon.music.core.designsystem.theme.getKaonColors("DARK", "CORAL")
        assertEquals(com.kaon.music.core.designsystem.theme.accentSwatchFor("CORAL").accent, darkCoral.primary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0B0C0F), darkCoral.background)

        val amoledViolet = com.kaon.music.core.designsystem.theme.getKaonColors("AMOLED", "VIOLET")
        assertEquals(com.kaon.music.core.designsystem.theme.accentSwatchFor("VIOLET").accent, amoledViolet.primary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledViolet.background)

        val darkBlue = com.kaon.music.core.designsystem.theme.getKaonColors("DARK", "BLUE")
        assertEquals(com.kaon.music.core.designsystem.theme.accentSwatchFor("BLUE").accent, darkBlue.primary)

        val amoledEmerald = com.kaon.music.core.designsystem.theme.getKaonColors("AMOLED", "EMERALD")
        assertEquals(com.kaon.music.core.designsystem.theme.accentSwatchFor("EMERALD").accent, amoledEmerald.primary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledEmerald.background)
    }
}

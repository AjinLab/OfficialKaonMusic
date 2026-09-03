package com.kaon.music.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary
import com.kaon.music.feature.settings.page.AboutSettingsPage
import com.kaon.music.feature.settings.page.AppearanceSettingsPage
import com.kaon.music.feature.settings.page.AudioSettingsPage
import com.kaon.music.feature.settings.page.LibrarySettingsPage
import com.kaon.music.feature.settings.page.OnlineSettingsPage
import com.kaon.music.feature.settings.page.PrivacySettingsPage

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    // Detail Sub-pages Navigation
    when (selectedSection) {
        SettingsSection.AUDIO_PLAYBACK -> {
            AudioSettingsPage(
                settings = settings,
                onBack = { viewModel.navigateToSection(null) },
                onSetStreamingQuality = viewModel::setStreamingQuality,
                onSetPreferredAudioType = viewModel::setPreferredAudioType,
                onSetPauseOnFocusLoss = viewModel::setPauseOnFocusLoss,
                onSetSkipSilence = viewModel::setSkipSilence,
                onSetCrossfadeSeconds = viewModel::setCrossfadeSeconds,
                onClearCache = viewModel::clearStreamCache,
                bottomPadding = bottomPadding,
                modifier = modifier
            )
            return
        }
        SettingsSection.LIBRARY_STORAGE -> {
            LibrarySettingsPage(
                settings = settings,
                isSyncing = isSyncing,
                onBack = { viewModel.navigateToSection(null) },
                onRescanClick = viewModel::triggerRescan,
                onSetMinDurationSeconds = viewModel::setMinDurationSeconds,
                bottomPadding = bottomPadding,
                modifier = modifier
            )
            return
        }
        SettingsSection.APPEARANCE_THEME -> {
            AppearanceSettingsPage(
                settings = settings,
                onBack = { viewModel.navigateToSection(null) },
                onSetThemeMode = viewModel::setThemeMode,
                onSetAccentColor = viewModel::setAccentColor,
                onSetShowFormatBadges = viewModel::setShowFormatBadges,
                onSetShowLosslessBadges = viewModel::setShowLosslessBadges,
                bottomPadding = bottomPadding,
                modifier = modifier
            )
            return
        }
        SettingsSection.ONLINE_STREAMING -> {
            OnlineSettingsPage(
                settings = settings,
                onBack = { viewModel.navigateToSection(null) },
                onSetPreResolveNextTracks = viewModel::setPreResolveNextTracks,
                onSetWifiOnlyStreaming = viewModel::setWifiOnlyStreaming,
                onClearStreamCache = viewModel::clearStreamCache,
                onSetLastFmApiKey = viewModel::setLastFmApiKey,
                onSetFanartTvApiKey = viewModel::setFanartTvApiKey,
                onSetDiscogsToken = viewModel::setDiscogsToken,
                bottomPadding = bottomPadding,
                modifier = modifier
            )
            return
        }
        SettingsSection.HISTORY_PRIVACY -> {
            PrivacySettingsPage(
                settings = settings,
                onBack = { viewModel.navigateToSection(null) },
                onSetRecordHistory = viewModel::setRecordHistory,
                onClearHistory = viewModel::clearListeningHistory,
                bottomPadding = bottomPadding,
                modifier = modifier
            )
            return
        }
        SettingsSection.ABOUT_INFO -> {
            AboutSettingsPage(
                onBack = { viewModel.navigateToSection(null) },
                bottomPadding = bottomPadding,
                modifier = modifier
            )
            return
        }
        null -> {
            // Render main settings menu
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding.calculateBottomPadding())
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = KaonTextPrimary
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = KaonTextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SettingsSection.entries.toTypedArray()) { section ->
                    SettingsSectionCard(
                        section = section,
                        onClick = { viewModel.navigateToSection(section) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding.calculateBottomPadding() + 8.dp)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    section: SettingsSection,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = KaonSurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(KaonPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = KaonPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = KaonTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KaonTextSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${section.title}",
                tint = KaonTextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

package com.kaon.music.feature.settings.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.data.repository.UserSettings
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary

@Composable
fun AppearanceSettingsPage(
    settings: UserSettings,
    onBack: () -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetAccentColor: (String) -> Unit,
    onSetShowFormatBadges: (Boolean) -> Unit,
    onSetShowLosslessBadges: (Boolean) -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
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
                text = "Appearance & Theme",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = KaonTextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Group 1: Theme Mode
            item {
                SettingsGroupHeader(title = "Theme & Darkness")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        listOf(
                            "DARK" to ("Kaon Dark (Default)" to "Refined deep navy and obsidian aesthetic"),
                            "AMOLED" to ("Pure Black (AMOLED)" to "Absolute black background for battery saving on OLED"),
                            "SYSTEM" to ("System Default" to "Follows Android OS system theme settings")
                        ).forEach { (modeKey, info) ->
                            val (title, subtitle) = info
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetThemeMode(modeKey) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.themeMode == modeKey,
                                    onClick = { onSetThemeMode(modeKey) },
                                    colors = RadioButtonDefaults.colors(selectedColor = KaonPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (settings.themeMode == modeKey) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = KaonTextPrimary
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KaonTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Group 2: Accent Color Palette
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Accent Color Palette")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Choose your personalized player and button accent color:",
                            style = MaterialTheme.typography.bodySmall,
                            color = KaonTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colors = listOf(
                                "CORAL" to ("Coral Blush" to Color(0xFFF0A89C)),
                                "VIOLET" to ("Violet Glow" to Color(0xFF8B5CF6)),
                                "BLUE" to ("Ocean Blue" to Color(0xFF38BDF8)),
                                "EMERALD" to ("Emerald Mint" to Color(0xFF34D399))
                            )

                            colors.forEach { (colorKey, data) ->
                                val (name, color) = data
                                val isSelected = settings.accentColor == colorKey

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onSetAccentColor(colorKey) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isSelected) KaonTextPrimary else KaonTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Group 3: Playback Badges
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Audio Badges & Indicators")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Default.Audiotrack,
                            title = "Show Audio Format Badges",
                            subtitle = "Display format tags (FLAC, MP3, WAV, OPUS) in track lists and player",
                            checked = settings.showFormatBadges,
                            onCheckedChange = onSetShowFormatBadges
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.HighQuality,
                            title = "Highlight Lossless / Hi-Res",
                            subtitle = "Display glowing Lossless indicators on studio-quality audio files",
                            checked = settings.showLosslessBadges,
                            onCheckedChange = onSetShowLosslessBadges
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

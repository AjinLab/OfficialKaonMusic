package com.kaon.music.feature.settings.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.data.repository.UserSettings
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonDivider
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.AudioType

@Composable
fun AudioSettingsPage(
    settings: UserSettings,
    onBack: () -> Unit,
    onSetStreamingQuality: (AudioQuality) -> Unit,
    onSetPauseOnFocusLoss: (Boolean) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit,
    onClearCache: () -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onSetPreferredAudioType: (AudioType) -> Unit = {}
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
                text = "Audio & Playback",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = KaonTextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Group 1: Streaming Audio Quality
            item {
                SettingsGroupHeader(title = "Streaming Quality")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        QualityOptionRow(
                            title = "Auto (Recommended)",
                            subtitle = "Adapts automatically based on network bandwidth",
                            selected = settings.streamingQuality == AudioQuality.AUTO,
                            onClick = { onSetStreamingQuality(AudioQuality.AUTO) }
                        )
                        QualityOptionRow(
                            title = "High Quality (256 kbps)",
                            subtitle = "Highest bitrate streaming with pristine audio clarity",
                            selected = settings.streamingQuality == AudioQuality.HIGH,
                            onClick = { onSetStreamingQuality(AudioQuality.HIGH) }
                        )
                        QualityOptionRow(
                            title = "Data Saver (128 kbps)",
                            subtitle = "Uses less mobile data and optimizes for weak signal",
                            selected = settings.streamingQuality == AudioQuality.LOW,
                            onClick = { onSetStreamingQuality(AudioQuality.LOW) }
                        )
                    }
                }
            }

            // Group 1b: Audio Type & Codec Preference
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Audio Format & Codec")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        QualityOptionRow(
                            title = "Auto (Best Compatibility)",
                            subtitle = "Selects the optimal codec supported by the stream",
                            selected = settings.preferredAudioType == AudioType.AUTO,
                            onClick = { onSetPreferredAudioType(AudioType.AUTO) }
                        )
                        QualityOptionRow(
                            title = "Opus (WebM Container)",
                            subtitle = "Modern, high-efficiency open-source audio codec",
                            selected = settings.preferredAudioType == AudioType.OPUS,
                            onClick = { onSetPreferredAudioType(AudioType.OPUS) }
                        )
                        QualityOptionRow(
                            title = "AAC (M4A Container)",
                            subtitle = "Standard advanced audio coding format with wide hardware support",
                            selected = settings.preferredAudioType == AudioType.AAC,
                            onClick = { onSetPreferredAudioType(AudioType.AAC) }
                        )
                    }
                }
            }

            // Group 2: Audio Focus & Playback Control
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Playback Behaviors")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Default.Headphones,
                            title = "Pause on Focus Loss",
                            subtitle = "Pause audio playback when another app requests audio focus (e.g. phone calls or notifications)",
                            checked = settings.pauseOnFocusLoss,
                            onCheckedChange = onSetPauseOnFocusLoss
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Skip Silence",
                            subtitle = "Automatically skip silent intros and gaps between songs",
                            checked = settings.skipSilence,
                            onCheckedChange = onSetSkipSilence
                        )
                    }
                }
            }

            // Group 3: Crossfade
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Transitions & Gapless")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = KaonPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Crossfade Duration",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = KaonTextPrimary
                                )
                            }
                            Text(
                                text = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds}s",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = KaonPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = settings.crossfadeSeconds.toFloat(),
                            onValueChange = { onSetCrossfadeSeconds(it.toInt()) },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = KaonPrimary,
                                activeTrackColor = KaonPrimary,
                                inactiveTrackColor = KaonDivider
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Group 4: Maintenance & Cache
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Engine & Maintenance")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onClearCache)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = KaonPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clear Playback Cache",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = KaonTextPrimary
                            )
                            Text(
                                text = "Purge cached stream chunks and stream URLs",
                                style = MaterialTheme.typography.bodySmall,
                                color = KaonTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        ),
        color = KaonPrimary,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun QualityOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = KaonPrimary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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

@Composable
fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) KaonPrimary else KaonTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = KaonTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = KaonTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KaonPrimary,
                checkedTrackColor = KaonPrimary.copy(alpha = 0.3f)
            )
        )
    }
}

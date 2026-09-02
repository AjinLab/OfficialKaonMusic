package com.kaon.music.feature.settings.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.data.repository.UserSettings
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibrarySettingsPage(
    settings: UserSettings,
    isSyncing: Boolean,
    onBack: () -> Unit,
    onRescanClick: () -> Unit,
    onSetMinDurationSeconds: (Int) -> Unit,
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
                text = "Library & Storage",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = KaonTextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Group 1: Media Scanner
            item {
                SettingsGroupHeader(title = "MediaStore Scanner")
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = KaonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Synchronize Music Library",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = KaonTextPrimary
                                )
                                Text(
                                    text = "Rescan local device storage for new, modified, or deleted audio files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KaonTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onRescanClick,
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = KaonTextPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning Device...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rescan Library Now")
                            }
                        }
                    }
                }
            }

            // Group 2: Filter Thresholds
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Audio Filter Threshold")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Filter out audio files shorter than this threshold (useful for excluding ringtones, notification chimes, and voice memos):",
                            style = MaterialTheme.typography.bodySmall,
                            color = KaonTextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        listOf(
                            5 to "5 Seconds (Include short interludes)",
                            15 to "15 Seconds (Exclude short sound effects)",
                            30 to "30 Seconds (Standard music filter)",
                            60 to "60 Seconds (Long tracks only)"
                        ).forEach { (seconds, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetMinDurationSeconds(seconds) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.minDurationSeconds == seconds,
                                    onClick = { onSetMinDurationSeconds(seconds) },
                                    colors = RadioButtonDefaults.colors(selectedColor = KaonPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (settings.minDurationSeconds == seconds) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = KaonTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Group 3: Supported Audio Formats
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Supported Audio Formats")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Kaon Music supports high-fidelity decoding for all common audio formats:",
                            style = MaterialTheme.typography.bodySmall,
                            color = KaonTextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val formats = listOf(
                                "FLAC" to true,
                                "ALAC" to true,
                                "WAV" to true,
                                "AIFF" to true,
                                "DSD" to true,
                                "APE" to true,
                                "MP3" to false,
                                "M4A" to false,
                                "AAC" to false,
                                "OPUS" to false,
                                "OGG" to false,
                                "WMA" to false,
                                "MKA" to false,
                                "WEBM" to false,
                                "AMR" to false,
                                "MIDI" to false
                            )

                            formats.forEach { (format, isLossless) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLossless) KaonPrimary.copy(alpha = 0.2f) else KaonBackground,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = format,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp
                                            ),
                                            color = if (isLossless) KaonPrimary else KaonTextPrimary
                                        )
                                        if (isLossless) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "• Hi-Res",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = KaonPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

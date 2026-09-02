package com.kaon.music.feature.settings.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaon.music.core.data.repository.UserSettings
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary

@Composable
fun OnlineSettingsPage(
    settings: UserSettings,
    onBack: () -> Unit,
    onSetPreResolveNextTracks: (Boolean) -> Unit,
    onSetWifiOnlyStreaming: (Boolean) -> Unit,
    onClearStreamCache: () -> Unit,
    onSetLastFmApiKey: (String) -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onSetFanartTvApiKey: (String) -> Unit = {},
    onSetDiscogsToken: (String) -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var lastFmKeyInput by remember(settings.lastFmApiKey) { mutableStateOf(settings.lastFmApiKey) }
    var fanartTvKeyInput by remember(settings.fanartTvApiKey) { mutableStateOf(settings.fanartTvApiKey) }
    var discogsTokenInput by remember(settings.discogsToken) { mutableStateOf(settings.discogsToken) }

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
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                text = "Streaming & Online Services",
                style = MaterialTheme.typography.titleMedium,
                color = KaonTextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Group 1: Streaming Preferences
            item {
                SettingsGroupHeader(title = "Playback & Network")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Default.Speed,
                            title = "Pre-resolve Next Track",
                            subtitle = "Warm up YouTube stream URLs before track end for zero-gap playback",
                            checked = settings.preResolveNextTracks,
                            onCheckedChange = onSetPreResolveNextTracks
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.Wifi,
                            title = "Wi-Fi Only Streaming",
                            subtitle = "Prevent streaming over mobile cellular data",
                            checked = settings.wifiOnlyStreaming,
                            onCheckedChange = onSetWifiOnlyStreaming
                        )
                    }
                }
            }

            // Group 2: Metadata & Enrichment (MusicMeta / Last.fm / Fanart.tv / Discogs)
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Metadata Enrichment & Providers")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = KaonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Provider API Keys (Optional)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = KaonTextPrimary
                                )
                                Text(
                                    text = "Enables extended metadata, artist artwork, and charts enrichment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KaonTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Last.fm API Key",
                            style = MaterialTheme.typography.labelMedium,
                            color = KaonTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = lastFmKeyInput,
                            onValueChange = {
                                lastFmKeyInput = it
                                onSetLastFmApiKey(it)
                            },
                            placeholder = {
                                Text(
                                    text = "Enter Last.fm API Key (last.fm/api)",
                                    color = KaonTextTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1B1B1F),
                                unfocusedContainerColor = Color(0xFF1B1B1F),
                                focusedIndicatorColor = KaonPrimary,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = KaonTextPrimary,
                                unfocusedTextColor = KaonTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Fanart.tv Project Key",
                            style = MaterialTheme.typography.labelMedium,
                            color = KaonTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = fanartTvKeyInput,
                            onValueChange = {
                                fanartTvKeyInput = it
                                onSetFanartTvApiKey(it)
                            },
                            placeholder = {
                                Text(
                                    text = "Enter Fanart.tv Project Key (fanart.tv)",
                                    color = KaonTextTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1B1B1F),
                                unfocusedContainerColor = Color(0xFF1B1B1F),
                                focusedIndicatorColor = KaonPrimary,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = KaonTextPrimary,
                                unfocusedTextColor = KaonTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Discogs Personal Token",
                            style = MaterialTheme.typography.labelMedium,
                            color = KaonTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = discogsTokenInput,
                            onValueChange = {
                                discogsTokenInput = it
                                onSetDiscogsToken(it)
                            },
                            placeholder = {
                                Text(
                                    text = "Enter Discogs Personal Token (discogs.com/settings/developers)",
                                    color = KaonTextTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1B1B1F),
                                unfocusedContainerColor = Color(0xFF1B1B1F),
                                focusedIndicatorColor = KaonPrimary,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = KaonTextPrimary,
                                unfocusedTextColor = KaonTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Core stack (MusicBrainz, CAA, LRCLIB, Deezer, iTunes, Wikidata, Wikipedia) works automatically without any keys.",
                            style = MaterialTheme.typography.labelSmall,
                            color = KaonTextTertiary
                        )
                    }
                }
            }

            // Group 3: Stream Resolver Status
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Extractor Pipeline")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = KaonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "InnerTube Resolver Status",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = KaonTextPrimary
                                )
                                Text(
                                    text = "Dual WebRemix + Android client pipeline with automated 403/410 recovery",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KaonTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Group 4: Cache Maintenance
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsGroupHeader(title = "Stream Cache")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KaonSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onClearStreamCache)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cached,
                            contentDescription = null,
                            tint = KaonPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clear Stream URL Cache",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = KaonTextPrimary
                            )
                            Text(
                                text = "Invalidate expired audio URLs and force fresh resolution",
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

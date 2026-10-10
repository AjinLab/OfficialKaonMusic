package com.kaon.music.ui.minimal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaon.music.core.kernel.PluginMetadata
import com.kaon.music.ui.plugin.KaonUiPlugin
import com.kaon.music.ui.plugin.UiControllers

/**
 * Throwaway minimal UI plugin demonstrating compile-time swappability of UI implementations.
 *
 * Implements [KaonUiPlugin] and operates strictly through capability controllers.
 */
class MinimalUiPlugin : KaonUiPlugin {

    override val metadata: PluginMetadata = PluginMetadata(
        id = "com.kaon.music.ui.minimal",
        name = "Minimal UI",
        version = "1.0.0",
        description = "Streamlined, distraction-free player interface proving swappability"
    )

    @Composable
    override fun Content(controllers: UiControllers, modifier: Modifier) {
        val nowPlaying by controllers.playerController.nowPlaying.collectAsStateWithLifecycle()
        val queue by controllers.playerController.queue.collectAsStateWithLifecycle()
        val trackCount by controllers.libraryController.observeTrackCount().collectAsStateWithLifecycle(initialValue = 0)

        val track = nowPlaying.currentTrack

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Minimal UI Plugin (Active)",
                        color = Color(0xFFFF8A65),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Center Track Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF262626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (nowPlaying.isPlaying) "▶ Playing" else "❚❚ Paused",
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = track?.displayTitle ?: "Nothing Playing",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = track?.displayArtist ?: "Library: $trackCount tracks",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Transport Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { controllers.playerController.skipPrevious() },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF242424), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { controllers.playerController.togglePlayPause() },
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFFFF8A65), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { controllers.playerController.skipNext() },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF242424), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer Queue info
                Text(
                    text = "Queue: ${queue.tracks.size} tracks",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

package com.kaon.music.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonCardBackground
import com.kaon.music.core.designsystem.theme.KaonDivider
import com.kaon.music.core.designsystem.theme.KaonHeartRed
import com.kaon.music.core.designsystem.theme.KaonOnAccent
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.LocalUserSettings
import com.kaon.music.core.playback.model.PlaybackProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * Docked mini player — a floating capsule above the navigation bar.
 *
 * The 3 dp progress line runs along the capsule's bottom edge, inside the clip, so playback state
 * is visible without spending a second control on it.
 *
 * ARCHITECTURE.md §3.2: [progress] is passed as a flow rather than a float so the 500 ms tick is
 * collected inside [MiniPlayerProgressBar] only. Passing the value in caused this entire composable —
 * artwork, text, badges, icon buttons — to recompose twice per second.
 */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: StateFlow<PlaybackProgress>,
    isFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = KaonSurfaceElevated,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    track = track,
                    sizeBucket = SizeBucket.THUMBNAIL,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 12.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = KaonTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.displayArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = KaonTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val userSettings = LocalUserSettings.current
                        if (!track.isOnline && userSettings.showFormatBadges) {
                            val isLosslessHighlight = track.isLossless && userSettings.showLosslessBadges
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isLosslessHighlight) KaonPrimary.copy(alpha = 0.2f) else KaonCardBackground,
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Text(
                                    text = track.audioFormatLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = if (isLosslessHighlight) KaonPrimary else KaonTextSecondary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) KaonHeartRed else KaonTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                PlayPauseCapsule(isPlaying = isPlaying, onClick = onPlayPauseClick)
            }

            MiniPlayerProgressBar(progress = progress)
        }
    }
}

/**
 * Filled accent pill for play/pause — the mini player's one loud element, matching the expanded
 * player's transport button.
 */
@Composable
private fun PlayPauseCapsule(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = KaonPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = KaonOnAccent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * The only composable in the mini player that observes playback position, so a tick invalidates a
 * 3 dp progress line rather than the whole row.
 */
@Composable
private fun MiniPlayerProgressBar(progress: StateFlow<PlaybackProgress>) {
    val playbackProgress by progress.collectAsStateWithLifecycle()
    val fraction by animateFloatAsState(
        targetValue = playbackProgress.fraction.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "MiniProgress"
    )
    // Read the theme colours in composition; the draw phase only uses the captured values.
    val progressColor = KaonPrimary
    val trackColor = KaonDivider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(trackColor)
            .drawWithContent {
                drawContent()
                drawRect(
                    color = progressColor,
                    size = Size(size.width * fraction, size.height),
                    topLeft = Offset.Zero
                )
            }
    )
}

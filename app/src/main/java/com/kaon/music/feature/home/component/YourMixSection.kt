package com.kaon.music.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.theme.KaonCardDark
import com.kaon.music.core.designsystem.theme.KaonHeartRed
import com.kaon.music.core.designsystem.theme.KaonOnAccent
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonPrimaryVariant
import com.kaon.music.core.designsystem.theme.KaonSurfaceHighlight
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary

/**
 * "Your Mix" — the home tab's hero. The pool is a radial wash of the active accent over a dark
 * card, so the app's one big gesture plays in the palette the user picked; the quick-mix chips
 * stay quiet in surface tones.
 */
@Composable
fun YourMixSection(
    yourMixTracks: List<Track>,
    heavyRotationCount: Int,
    recentlyAddedCount: Int,
    favoriteCount: Int,
    onPlayYourMix: () -> Unit,
    onPlayHeavyRotation: () -> Unit,
    onPlayRecentlyAdded: () -> Unit,
    onPlayFavorites: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (yourMixTracks.isEmpty() && heavyRotationCount == 0 && recentlyAddedCount == 0 && favoriteCount == 0) {
        return
    }

    val totalDurationMs = yourMixTracks.sumOf { it.durationMs }
    val durationText = formatMixDuration(totalDurationMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Made for you",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Hero Card: "Your Mix" — accent pool over a dark base.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            KaonPrimary.copy(alpha = 0.55f),
                            KaonPrimaryVariant.copy(alpha = 0.28f),
                            KaonCardDark
                        ),
                        radius = 700f
                    )
                )
                .clickable(onClick = onPlayYourMix)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart mix",
                        style = MaterialTheme.typography.labelMedium,
                        color = KaonTextSecondary
                    )

                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = KaonPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Your Mix",
                            tint = KaonOnAccent,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "Your Mix",
                        style = MaterialTheme.typography.headlineLarge,
                        color = KaonTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${yourMixTracks.size} tracks — $durationText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaonTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Quick Mix Chips — quiet surface tiles with an accent-coloured icon each.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (heavyRotationCount > 0) {
                QuickMixCard(
                    title = "Heavy Rotation",
                    subtitle = "$heavyRotationCount tracks",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = KaonPrimary,
                    onClick = onPlayHeavyRotation,
                    modifier = Modifier.weight(1f)
                )
            }

            if (recentlyAddedCount > 0) {
                QuickMixCard(
                    title = "Recently Added",
                    subtitle = "$recentlyAddedCount tracks",
                    icon = Icons.Default.FiberNew,
                    iconTint = KaonPrimaryVariant,
                    onClick = onPlayRecentlyAdded,
                    modifier = Modifier.weight(1f)
                )
            }

            if (favoriteCount > 0) {
                QuickMixCard(
                    title = "Favorites",
                    subtitle = "$favoriteCount songs",
                    icon = Icons.Default.Favorite,
                    iconTint = KaonHeartRed,
                    onClick = onPlayFavorites,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickMixCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(KaonSurfaceHighlight)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = KaonTextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KaonTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatMixDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "$hours hr $minutes min"
    } else {
        "$minutes min"
    }
}

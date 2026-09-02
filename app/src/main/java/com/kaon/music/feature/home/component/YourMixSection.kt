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
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.unit.sp
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.theme.KaonCardDark
import com.kaon.music.core.designsystem.theme.KaonHeartRed
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary

/**
 * Dynamic "Your Mix" and Smart Mixes section.
 * Backed by real play_events and Room query aggregations.
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
            text = "Made For You",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = KaonTextPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Hero Card: "Your Mix"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6B1824),
                            Color(0xFF330D13),
                            KaonCardDark
                        ),
                        radius = 600f
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = KaonPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SMART MIX",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = KaonPrimary
                        )
                    }

                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = KaonPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Your Mix",
                            tint = Color.Black,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "Your Mix",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = KaonTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${yourMixTracks.size} tracks • $durationText",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = KaonTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Quick Mix Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (heavyRotationCount > 0) {
                QuickMixCard(
                    title = "Heavy Rotation",
                    subtitle = "$heavyRotationCount tracks",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = Color(0xFFFF8A65),
                    gradient = Brush.verticalGradient(
                        listOf(Color(0xFF2C1E1B), Color(0xFF191210))
                    ),
                    onClick = onPlayHeavyRotation,
                    modifier = Modifier.weight(1f)
                )
            }

            if (recentlyAddedCount > 0) {
                QuickMixCard(
                    title = "Recently Added",
                    subtitle = "$recentlyAddedCount tracks",
                    icon = Icons.Default.FiberNew,
                    accentColor = Color(0xFF64B5F6),
                    gradient = Brush.verticalGradient(
                        listOf(Color(0xFF162330), Color(0xFF0F151C))
                    ),
                    onClick = onPlayRecentlyAdded,
                    modifier = Modifier.weight(1f)
                )
            }

            if (favoriteCount > 0) {
                QuickMixCard(
                    title = "Favorites",
                    subtitle = "$favoriteCount songs",
                    icon = Icons.Default.Favorite,
                    accentColor = KaonHeartRed,
                    gradient = Brush.verticalGradient(
                        listOf(Color(0xFF2E1720), Color(0xFF1A0E13))
                    ),
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
    accentColor: Color,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
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
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
        "${hours} hr ${minutes} min"
    } else {
        "${minutes} min"
    }
}

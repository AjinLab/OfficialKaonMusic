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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.designsystem.theme.KaonCardDark
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary

@Composable
fun MadeForYouSection(
    onDiscoverWeeklyClick: () -> Unit,
    onChillMixClick: () -> Unit,
    onWorkoutMixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        // Hero Card: Discover Weekly
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF5A1A22),
                            Color(0xFF2E0D12),
                            KaonCardDark
                        ),
                        radius = 500f
                    )
                )
                .clickable(onClick = onDiscoverWeeklyClick)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "UPDATED DAILY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = KaonPrimary
                )

                Column {
                    Text(
                        text = "Discover Weekly",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = KaonTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "New music personalized for you. Catch up on the latest tracks we think you'll love.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KaonTextSecondary,
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2 Sub-mix cards: Chill Mix & Workout Mix
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SubMixCard(
                title = "Chill Mix",
                subtitle = "Lo-fi & Downtempo",
                gradient = Brush.verticalGradient(
                    listOf(Color(0xFF1E2638), Color(0xFF121620))
                ),
                onClick = onChillMixClick,
                modifier = Modifier.weight(1f)
            )

            SubMixCard(
                title = "Workout Mix",
                subtitle = "High BPM & Bass",
                gradient = Brush.verticalGradient(
                    listOf(Color(0xFF38231E), Color(0xFF1E1412))
                ),
                onClick = onWorkoutMixClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SubMixCard(
    title: String,
    subtitle: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = KaonTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = KaonTextSecondary
            )
        }
    }
}

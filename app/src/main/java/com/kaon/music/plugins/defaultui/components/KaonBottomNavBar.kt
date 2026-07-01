package com.kaon.music.plugins.defaultui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kaon.music.plugins.defaultui.theme.KaonColors

enum class NavTab(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DISCOVER("Discover", "discover", Icons.Filled.Home, Icons.Outlined.Home),
    MUSIC("Music", "music", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
    SEARCH("Search", "search", Icons.Filled.Search, Icons.Outlined.Search),
    ACCOUNT("Account", "account", Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * 4-tab bottom navigation bar with:
 *  - Optional badge counts
 *  - Animated icon scale on selection
 *  - Haptic feedback on tap
 */
@Composable
fun KaonBottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<NavTab, Int> = emptyMap()
) {
    val haptic = LocalHapticFeedback.current

    NavigationBar(
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavTab.entries.forEach { tab ->
            val selected = tab == currentTab
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1f,
                animationSpec = tween(220),
                label = "navIconScale"
            )

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(tab)
                    }
                },
                icon = {
                    BadgedBox(
                        badge = {
                            badges[tab]?.takeIf { it > 0 }?.let { count ->
                                Badge { Text(if (count > 99) "99+" else count.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            modifier = Modifier.scale(scale)
                        )
                    }
                },
                label = {
                    Text(text = tab.label, style = MaterialTheme.typography.labelMedium)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = KaonColors.NavActive,
                    unselectedIconColor = KaonColors.NavInactive,
                    selectedTextColor = KaonColors.NavActive,
                    unselectedTextColor = KaonColors.NavInactive,
                    indicatorColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}
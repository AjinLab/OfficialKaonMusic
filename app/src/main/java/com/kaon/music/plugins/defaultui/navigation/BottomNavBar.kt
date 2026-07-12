package com.kaon.music.plugins.defaultui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class NavItem(
    val destination: Destination,
    val title: String,
    val icon: ImageVector
)

val BottomNavItems = listOf(
    NavItem(Destination.Library, "Library", Icons.Rounded.LibraryMusic),
    NavItem(Destination.Music, "Music", Icons.Rounded.MusicNote),
    NavItem(Destination.Search, "Search", Icons.Rounded.Search),
    NavItem(Destination.Settings, "Settings", Icons.Rounded.Settings)
)

@Composable
fun AppNavigation(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass,
    currentRoute: String?,
    isTopLevel: Boolean,
    showNowPlaying: Boolean,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cleanRoute = currentRoute?.substringBefore('?')
    val isDrawerLayout = widthSizeClass == WindowWidthSizeClass.Expanded && heightSizeClass != WindowHeightSizeClass.Compact

    if (isDrawerLayout && isTopLevel && !showNowPlaying) {
        PermanentNavigationDrawer(
            modifier = modifier,
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(240.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Kaon Music",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BottomNavItems.forEach { item ->
                        val selected = cleanRoute == item.destination.route
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = selected,
                            onClick = { onNavigate(item.destination) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun DefaultNavigationRail(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanRoute = currentRoute?.substringBefore('?')
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Spacer(modifier = Modifier.weight(1f))
        BottomNavItems.forEach { item ->
            val selected = cleanRoute == item.destination.route
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(item.destination) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanRoute = currentRoute?.substringBefore('?')
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        BottomNavItems.forEach { item ->
            val selected = cleanRoute == item.destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.destination) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

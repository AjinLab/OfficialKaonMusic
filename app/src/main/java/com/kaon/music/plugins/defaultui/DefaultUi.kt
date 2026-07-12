package com.kaon.music.plugins.defaultui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.core.plugin.api.UiPlugin
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.library.LibraryController
import com.kaon.music.plugins.defaultui.motion.MotionTokens
import com.kaon.music.plugins.defaultui.navigation.AppNavigation
import com.kaon.music.plugins.defaultui.navigation.BottomNavBar
import com.kaon.music.plugins.defaultui.navigation.DefaultNavHost
import com.kaon.music.plugins.defaultui.navigation.DefaultNavigationRail
import com.kaon.music.plugins.defaultui.navigation.Destination
import com.kaon.music.plugins.defaultui.navigation.Navigator
import com.kaon.music.plugins.defaultui.navigation.isTopLevelRoute
import com.kaon.music.plugins.defaultui.screens.player.MiniPlayer
import com.kaon.music.plugins.defaultui.screens.player.NowPlayingSheet
import com.kaon.music.plugins.defaultui.theme.KaonTheme

class DefaultUi : UiPlugin {
    override val id: String = "com.kaon.music.ui.default"

    override fun initialize() {}
    override fun destroy() {}

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    override fun MainScreen(kernel: Kernel) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val playerController = kernel.get<PlayerController>()
        val libraryController = kernel.get<LibraryController>()
        val artworkLoader = kernel.get<ArtworkLoader>()
        val artworkRepository = kernel.get<ArtworkRepository>()

        val currentSong by playerController.currentSong.collectAsState()
        var showNowPlaying by remember { mutableStateOf(false) }

        // Safely extract the Activity context
        val context = LocalContext.current
        val activity = remember(context) {
            var currentContext = context
            while (currentContext is ContextWrapper) {
                if (currentContext is Activity) break
                currentContext = currentContext.baseContext
            }
            currentContext as? Activity
        }

        val widthSizeClass = if (activity != null) {
            calculateWindowSizeClass(activity).widthSizeClass
        } else {
            WindowWidthSizeClass.Compact
        }

        val heightSizeClass = if (activity != null) {
            calculateWindowSizeClass(activity).heightSizeClass
        } else {
            WindowHeightSizeClass.Medium
        }

        val navigator = remember(navController) { Navigator(navController) }
        val isTopLevel = currentRoute == null || isTopLevelRoute(currentRoute)

        LaunchedEffect(Unit) {
            playerController.errorEvents.collect { error ->
                val message = when (error) {
                    is com.kaon.music.media.state.PlaybackError.CodecUnsupported -> "This audio format isn't supported on your device."
                    else -> "Playback failed."
                }
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        // Decide adaptive layout types
        val useNavigationRail = widthSizeClass == WindowWidthSizeClass.Medium ||
                heightSizeClass == WindowHeightSizeClass.Compact
        val usePermanentDrawer = widthSizeClass == WindowWidthSizeClass.Expanded &&
                heightSizeClass != WindowHeightSizeClass.Compact

        KaonTheme(artworkColors = currentSong?.artworkColors) {
            AppNavigation(
                widthSizeClass = widthSizeClass,
                heightSizeClass = heightSizeClass,
                currentRoute = currentRoute,
                isTopLevel = isTopLevel,
                showNowPlaying = showNowPlaying,
                onNavigate = { destination -> navigator.navigate(destination) }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Navigation Rail
                    val showNavRail = useNavigationRail && isTopLevel && !showNowPlaying
                    val navRailWidth by animateDpAsState(
                        targetValue = if (showNavRail) 80.dp else 0.dp,
                        animationSpec = tween(MotionTokens.Normal),
                        label = "NavRailWidth"
                    )

                    if (navRailWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(navRailWidth)
                                .graphicsLayer {
                                    alpha = if (showNavRail) 1f else 0f
                                }
                        ) {
                            DefaultNavigationRail(
                                currentRoute = currentRoute,
                                onNavigate = { destination -> navigator.navigate(destination) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Main App Content Container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // NavHost fills screen area
                        DefaultNavHost(
                            navController = navController,
                            libraryController = libraryController,
                            playerController = playerController,
                            artworkLoader = artworkLoader,
                            artworkRepository = artworkRepository,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Bottom navigation and MiniPlayer (Compact only)
                        val density = LocalDensity.current
                        val systemInsets = WindowInsets.navigationBars.getBottom(density)
                        val systemInsetsDp = with(density) { systemInsets.toDp() }

                        val showBottomBar = !useNavigationRail && !usePermanentDrawer && isTopLevel && !showNowPlaying
                        
                        // Dynamically measure the bottom bar height to offset it
                        val defaultHeightPx = with(density) { 80.dp.roundToPx() }
                        var bottomBarHeightPx by remember { mutableIntStateOf(defaultHeightPx) }
                        val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }

                        // translationY is bottomBarHeight when hidden, sliding off the bottom
                        val translationY by animateDpAsState(
                            targetValue = if (showBottomBar) 0.dp else bottomBarHeightDp,
                            animationSpec = tween(MotionTokens.Normal),
                            label = "BottomBarTranslation"
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .graphicsLayer {
                                    this.translationY = with(density) { translationY.toPx() }
                                }
                        ) {
                            MiniPlayer(
                                playerController = playerController,
                                onExpand = { showNowPlaying = true },
                                isVisible = !showNowPlaying,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        bottomBarHeightPx = coordinates.size.height
                                    }
                            ) {
                                BottomNavBar(
                                    currentRoute = currentRoute,
                                    onNavigate = { destination -> navigator.navigate(destination) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // Safe space to cover navigation bar area at the bottom
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(systemInsetsDp)
                            )
                        }
                    }
                }
            }

            if (showNowPlaying) {
                NowPlayingSheet(
                    controller = playerController,
                    artworkRepository = artworkRepository,
                    libraryController = libraryController,
                    onDismiss = { showNowPlaying = false },
                    onGoToAlbum = { id ->
                        navigator.navigate(Destination.Album(id))
                        showNowPlaying = false
                    },
                    onGoToArtist = { id ->
                        navigator.navigate(Destination.Artist(id))
                        showNowPlaying = false
                    }
                )
            }
        }
    }
}

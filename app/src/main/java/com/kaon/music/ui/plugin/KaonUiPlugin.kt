package com.kaon.music.ui.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kaon.music.core.kernel.UiPlugin
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.PlayerController
import com.kaon.music.core.model.SettingsController
import com.kaon.music.core.model.UserMessageBus

/**
 * Encapsulated bundle of capability controllers passed to compile-time UI plugins.
 */
data class UiControllers(
    val playerController: PlayerController,
    val libraryController: LibraryController,
    val settingsController: SettingsController,
    val userMessageBus: UserMessageBus? = null,
    val onRequestPermission: () -> Unit = {}
)

/**
 * Compile-time UI plugin contract implemented by UI modules/packages.
 *
 * Plugs directly into [com.kaon.music.core.kernel.KaonKernel] to provide swappable user interfaces.
 */
interface KaonUiPlugin : UiPlugin {
    @Composable
    fun Content(controllers: UiControllers, modifier: Modifier = Modifier)
}

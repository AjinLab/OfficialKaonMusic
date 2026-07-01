package com.kaon.music.plugins.defaultui

import androidx.compose.runtime.Composable
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.plugin.api.UiPlugin
import com.kaon.music.plugins.defaultui.theme.KaonTheme

class DefaultUi : UiPlugin {
    override val id: String = "com.kaon.music.plugins.defaultui"

    override fun initialize() {
        // No-op
    }

    override fun destroy() {
        // No-op
    }

    @Composable
    override fun MainScreen(kernel: Kernel) {
        KaonTheme {
            com.kaon.music.plugins.defaultui.screens.MainScreen(kernel)
        }
    }
}

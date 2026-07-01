package com.kaon.music.core.plugin.api

import androidx.compose.runtime.Composable
import com.kaon.music.core.plugin.Plugin
import com.kaon.music.core.kernel.Kernel

interface UiPlugin : Plugin {
    @Composable
    fun MainScreen(
        kernel: Kernel
    )
}
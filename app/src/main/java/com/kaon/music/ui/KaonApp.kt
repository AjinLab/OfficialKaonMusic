package com.kaon.music.ui

import androidx.compose.runtime.Composable
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.plugin.registry.PluginRegistry
import com.kaon.music.core.plugin.api.UiPlugin
import com.kaon.music.plugins.defaultui.DefaultUi

@Composable
fun KaonApp(kernel: Kernel) {
    val pluginRegistry = kernel.get<PluginRegistry>()
    val uiPlugin = pluginRegistry.plugins().filterIsInstance<UiPlugin>().firstOrNull() ?: DefaultUi()

    uiPlugin.MainScreen(kernel = kernel)
}

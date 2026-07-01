package com.kaon.music.core.plugin.impl

import com.kaon.music.core.plugin.PluginLoader
import com.kaon.music.core.plugin.registry.PluginRegistry
import com.kaon.music.plugins.defaultui.DefaultUi

class KaonPluginLoader(
    private val registry: PluginRegistry
) : PluginLoader {

    override fun loadBuiltInPlugins() {
        val defaultUi = DefaultUi()
        defaultUi.initialize()
        registry.register(defaultUi)
    }
}
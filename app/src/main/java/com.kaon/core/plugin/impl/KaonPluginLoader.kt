package com.kaon.core.plugin.impl

import com.kaon.core.plugin.Plugin
import com.kaon.core.plugin.PluginLoader

class KaonPluginLoader : PluginLoader {

    private val plugins =
        mutableMapOf<String, Plugin>()

    override fun register(plugin: Plugin) {
        plugin.initialize()
        plugins[plugin.id] = plugin
    }

    override fun unload(id: String) {
        plugins[id]?.destroy()
        plugins.remove(id)
    }

    override fun plugins(): List<Plugin> {
        return plugins.values.toList()
    }
}
package com.kaon.music.core.plugin.registry.impl

import com.kaon.music.core.plugin.Plugin
import com.kaon.music.core.plugin.registry.PluginRegistry
import java.util.concurrent.ConcurrentHashMap

class KaonPluginRegistry : PluginRegistry {

    private val pluginsMap = ConcurrentHashMap<String, Plugin>()

    override fun register(plugin: Plugin) {
        pluginsMap[plugin.id] = plugin
    }

    override fun unload(id: String) {
        pluginsMap.remove(id)
    }

    override fun plugins(): List<Plugin> {
        return pluginsMap.values.toList()
    }
}

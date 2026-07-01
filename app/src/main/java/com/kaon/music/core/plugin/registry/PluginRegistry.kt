package com.kaon.music.core.plugin.registry

import com.kaon.music.core.plugin.Plugin

interface PluginRegistry {
    fun register(plugin: Plugin)
    fun unload(id: String)
    fun plugins(): List<Plugin>
}

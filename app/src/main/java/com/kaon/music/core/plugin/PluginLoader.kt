package com.kaon.music.core.plugin

interface PluginLoader {

    fun register(plugin: Plugin)

    fun unload(id: String)

    fun plugins(): List<Plugin>
}
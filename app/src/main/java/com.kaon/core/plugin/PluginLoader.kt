package com.kaon.core.plugin

interface PluginLoader {

    fun register(plugin: Plugin)

    fun unload(id: String)

    fun plugins(): List<Plugin>
}
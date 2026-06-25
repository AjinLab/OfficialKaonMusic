package com.kaon.core.event

data class PluginLoadedEvent(
    val pluginId: String
) : Event

package com.kaon.music.core.event

data class PluginLoadedEvent(
    val pluginId: String
) : Event

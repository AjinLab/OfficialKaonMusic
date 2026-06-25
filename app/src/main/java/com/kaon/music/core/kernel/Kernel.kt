package com.kaon.music.core.kernel

import com.kaon.music.core.event.EventBus
import com.kaon.music.core.logger.Logger
import com.kaon.music.core.plugin.PluginLoader

interface Kernel {

    val eventBus: EventBus

    val logger: Logger

    val pluginLoader: PluginLoader

    suspend fun start()

    suspend fun stop()
}

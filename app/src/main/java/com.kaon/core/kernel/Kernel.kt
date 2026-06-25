package com.kaon.core.kernel

import com.kaon.core.event.EventBus
import com.kaon.core.logger.Logger
import com.kaon.core.plugin.PluginLoader

interface Kernel {

    val eventBus: EventBus

    val logger: Logger

    val pluginLoader: PluginLoader

    suspend fun start()

    suspend fun stop()
}

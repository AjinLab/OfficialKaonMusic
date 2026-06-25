package com.kaon.music.core.kernel.impl

import com.kaon.music.core.event.impl.KaonEventBus
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.logger.impl.AndroidLogger
import com.kaon.music.core.plugin.impl.KaonPluginLoader

class KaonKernel : Kernel {

    override val eventBus =
        KaonEventBus()

    override val logger =
        AndroidLogger()

    override val pluginLoader =
        KaonPluginLoader()

    override suspend fun start() {

        logger.info(
            "Kernel",
            "Kernel Started"
        )
    }

    override suspend fun stop() {

        logger.info(
            "Kernel",
            "Kernel Stopped"
        )
    }
}

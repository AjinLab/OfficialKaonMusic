package com.kaon.music.core.plugin

import android.util.Log
import com.kaon.music.core.event.EventBus
import com.kaon.music.core.event.PluginLoadedEvent

class TestPlugin(
    private val eventBus: EventBus
) : Plugin {

    override val id = "test"

    override fun initialize() {

        Log.d("KAON", "TestPlugin initialized")

        eventBus.publish(
            PluginLoadedEvent(id)
        )
    }

    override fun destroy() {

    }
}
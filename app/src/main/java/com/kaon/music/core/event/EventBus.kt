package com.kaon.music.core.event

import kotlin.reflect.KClass

interface EventBus {

    fun publish(event: Event)

    fun <T : Event> subscribe(
        type: KClass<T>,
        listener: (T) -> Unit
    )
}

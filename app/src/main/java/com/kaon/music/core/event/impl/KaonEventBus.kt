package com.kaon.music.core.event.impl

import android.util.Log
import com.kaon.music.core.event.Event
import com.kaon.music.core.event.EventBus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass

class KaonEventBus : EventBus {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    private val flow =
        MutableSharedFlow<Event>(
            extraBufferCapacity = 128
        )

    override fun publish(event: Event) {
        Log.d("KAON", "Publishing ${event::class.simpleName}")
        flow.tryEmit(event)
    }

    override fun <T : Event> subscribe(
        type: KClass<T>,
        listener: (T) -> Unit
    ): com.kaon.music.core.event.Subscription {
        Log.d("KAON", "Subscribed to ${type.simpleName}")

        val job = scope.launch {
            flow.collect { event ->

                Log.d(
                    "KAON",
                    "Received ${event::class.simpleName}"
                )

                @Suppress("UNCHECKED_CAST")
                if (type.isInstance(event)) {
                    listener(event as T)
                }
            }
        }
        return com.kaon.music.core.event.Subscription { job.cancel() }
    }
}
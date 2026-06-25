package com.kaon.music.core.lifecycle

interface LifecycleManager {
    fun register(aware: LifecycleAware)
    fun unregister(aware: LifecycleAware)
}

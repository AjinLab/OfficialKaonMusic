package com.kaon.core.lifecycle

interface LifecycleManager {
    fun register(aware: LifecycleAware)
    fun unregister(aware: LifecycleAware)
}

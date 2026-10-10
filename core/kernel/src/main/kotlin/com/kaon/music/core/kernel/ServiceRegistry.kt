package com.kaon.music.core.kernel

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Thrown when a requested service is not registered in [ServiceRegistry].
 */
class ServiceNotFoundException(val serviceClass: KClass<*>) :
    IllegalStateException("Required service not registered: ${serviceClass.qualifiedName ?: serviceClass.simpleName}")

/**
 * Thread-safe service registry with typed keys.
 *
 * Core microkernel service container backing [KaonKernel].
 */
class ServiceRegistry {
    private val services = ConcurrentHashMap<KClass<*>, Any>()

    fun <T : Any> register(serviceClass: KClass<T>, instance: T) {
        services[serviceClass] = instance
    }

    inline fun <reified T : Any> register(instance: T) {
        register(T::class, instance)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(serviceClass: KClass<T>): T {
        return (services[serviceClass] as? T)
            ?: throw ServiceNotFoundException(serviceClass)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(serviceClass: KClass<T>): T? {
        return services[serviceClass] as? T
    }

    inline fun <reified T : Any> get(): T = get(T::class)

    inline fun <reified T : Any> getOrNull(): T? = getOrNull(T::class)

    fun contains(serviceClass: KClass<*>): Boolean = services.containsKey(serviceClass)

    inline fun <reified T : Any> contains(): Boolean = contains(T::class)

    fun unregister(serviceClass: KClass<*>) {
        services.remove(serviceClass)
    }

    inline fun <reified T : Any> unregister() {
        unregister(T::class)
    }

    fun clear() {
        services.clear()
    }

    val size: Int get() = services.size
}

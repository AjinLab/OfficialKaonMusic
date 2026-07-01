package com.kaon.music.core.kernel

import kotlin.reflect.KClass

interface Kernel {

    fun <T : Any> register(type: KClass<T>, instance: T)

    fun <T : Any> get(type: KClass<T>): T

    fun contains(type: KClass<*>): Boolean

    suspend fun start()

    suspend fun stop()
}

inline fun <reified T : Any> Kernel.register(instance: T) = register(T::class, instance)
inline fun <reified T : Any> Kernel.get(): T = get(T::class)
inline fun <reified T : Any> Kernel.contains(): Boolean = contains(T::class)

package com.kaon.music.core.kernel

/**
 * Metadata identifying a Kaon plugin.
 */
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = ""
)

/**
 * Lifecycle contract for plugins managed by [KaonKernel].
 */
interface PluginLifecycle {
    fun initialize(kernel: KaonKernel) {}
    fun start() {}
    fun stop() {}
}

/**
 * Base contract for all Kaon plugins.
 */
interface KaonPlugin : PluginLifecycle {
    val metadata: PluginMetadata
}

/**
 * Marker interface for compile-time UI plugins.
 */
interface UiPlugin : KaonPlugin

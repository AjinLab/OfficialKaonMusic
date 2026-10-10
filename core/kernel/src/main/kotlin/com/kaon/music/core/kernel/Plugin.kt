package com.kaon.music.core.kernel

/**
 * Lifecycle states of a plugin managed by the kernel.
 */
enum class PluginState {
    /** Plugin has been discovered and registered in the registry, but not yet initialized. */
    REGISTERED,
    /** Plugin dependencies have been wired and initialize() completed successfully. */
    INITIALIZED,
    /** Plugin has been started and is actively participating in the application. */
    ACTIVE,
    /** Plugin failed during initialization or activation and has been isolated. */
    FAILED,
    /** Plugin is deliberately disabled or inactive (e.g., an alternate UI plugin). */
    DISABLED
}

/**
 * Log levels for kernel operations.
 */
enum class KernelLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Pluggable logger interface for [KaonKernel].
 */
fun interface KernelLogger {
    fun log(level: KernelLogLevel, message: String, throwable: Throwable?)
}

object NoOpKernelLogger : KernelLogger {
    override fun log(level: KernelLogLevel, message: String, throwable: Throwable?) {}
}

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

package com.kaon.music.core.kernel

import java.util.concurrent.ConcurrentHashMap

/**
 * Thrown when trying to register a plugin with an already existing ID.
 */
class DuplicatePluginException(val id: String) :
    IllegalStateException("Plugin with id '$id' is already registered in PluginRegistry")

/**
 * Thread-safe registry for Kaon plugins.
 */
class PluginRegistry {
    private val plugins = ConcurrentHashMap<String, KaonPlugin>()

    fun register(plugin: KaonPlugin) {
        val existing = plugins.putIfAbsent(plugin.metadata.id, plugin)
        if (existing != null) {
            throw DuplicatePluginException(plugin.metadata.id)
        }
    }

    fun get(id: String): KaonPlugin? = plugins[id]

    @Suppress("UNCHECKED_CAST")
    fun <T : KaonPlugin> getAs(id: String): T? = plugins[id] as? T

    fun all(): List<KaonPlugin> = plugins.values.toList()

    fun contains(id: String): Boolean = plugins.containsKey(id)

    fun unregister(id: String): KaonPlugin? = plugins.remove(id)

    fun clear() {
        plugins.clear()
    }

    val size: Int get() = plugins.size
}

/**
 * Loader that instantiates and registers plugins using a build-time declared supplier list.
 *
 * Avoids runtime reflection or dynamic code loading.
 */
class PluginLoader(
    private val staticPluginSuppliers: List<() -> KaonPlugin> = emptyList()
) {
    fun loadPlugins(registry: PluginRegistry, kernel: KaonKernel): List<KaonPlugin> {
        val loaded = mutableListOf<KaonPlugin>()
        for (supplier in staticPluginSuppliers) {
            val plugin = supplier()
            registry.register(plugin)
            plugin.initialize(kernel)
            plugin.start()
            loaded.add(plugin)
        }
        return loaded
    }
}

/**
 * Central microkernel coordinating service registration, plugin lifecycles, and capabilities.
 */
class KaonKernel(
    val services: ServiceRegistry = ServiceRegistry(),
    val plugins: PluginRegistry = PluginRegistry()
) {
    var isInitialized: Boolean = false
        private set

    /**
     * Bootstrap the kernel with core services and build-time plugin suppliers.
     * Startup order:
     * 1. Core services already registered in [services]
     * 2. Plugins loaded via [PluginLoader], initialized with this kernel, and started.
     */
    fun bootstrap(pluginSuppliers: List<() -> KaonPlugin> = emptyList()) {
        check(!isInitialized) { "KaonKernel is already initialized" }
        isInitialized = true
        val loader = PluginLoader(pluginSuppliers)
        loader.loadPlugins(plugins, this)
    }

    /**
     * Shuts down all plugins and cleans up registries.
     */
    fun shutdown() {
        for (plugin in plugins.all()) {
            try {
                plugin.stop()
            } catch (e: Exception) {
                // Keep stopping remaining plugins even if one throws
            }
        }
        plugins.clear()
        services.clear()
        isInitialized = false
    }
}

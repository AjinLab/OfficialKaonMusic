package com.kaon.music.core.kernel

import java.util.concurrent.ConcurrentHashMap

/**
 * Thrown when trying to register a plugin with an already existing ID.
 */
class DuplicatePluginException(val id: String) :
    IllegalStateException("Plugin with id '$id' is already registered in PluginRegistry")

/**
 * Thrown when a designated active UI plugin fails to initialize or activate.
 */
class UiPluginActivationException(val id: String, cause: Throwable?) :
    IllegalStateException("Designated UI plugin '$id' failed to activate", cause)

/**
 * Internal record tracking a plugin, its current lifecycle state, and any encountered failure.
 */
data class PluginRecord(
    val plugin: KaonPlugin,
    @Volatile var state: PluginState = PluginState.REGISTERED,
    @Volatile var failure: Throwable? = null
)

/**
 * Thread-safe registry for Kaon plugins with state tracking.
 */
class PluginRegistry {
    private val records = ConcurrentHashMap<String, PluginRecord>()

    fun register(plugin: KaonPlugin, initialState: PluginState = PluginState.REGISTERED) {
        val record = PluginRecord(plugin = plugin, state = initialState)
        val existing = records.putIfAbsent(plugin.metadata.id, record)
        if (existing != null) {
            throw DuplicatePluginException(plugin.metadata.id)
        }
    }

    fun get(id: String): KaonPlugin? = records[id]?.plugin

    fun getRecord(id: String): PluginRecord? = records[id]

    @Suppress("UNCHECKED_CAST")
    fun <T : KaonPlugin> getAs(id: String): T? = records[id]?.plugin as? T

    fun stateOf(id: String): PluginState? = records[id]?.state

    fun failureOf(id: String): Throwable? = records[id]?.failure

    fun updateState(id: String, state: PluginState, failure: Throwable? = null) {
        val record = records[id] ?: return
        record.state = state
        record.failure = failure
    }

    fun all(): List<KaonPlugin> = records.values.map { it.plugin }

    fun activePlugins(): List<KaonPlugin> =
        records.values.filter { it.state == PluginState.ACTIVE }.map { it.plugin }

    fun initializedPlugins(): List<KaonPlugin> =
        records.values.filter { it.state == PluginState.INITIALIZED }.map { it.plugin }

    fun failedPlugins(): List<KaonPlugin> =
        records.values.filter { it.state == PluginState.FAILED }.map { it.plugin }

    fun contains(id: String): Boolean = records.containsKey(id)

    fun unregister(id: String): KaonPlugin? = records.remove(id)?.plugin

    fun clear() {
        records.clear()
    }

    val size: Int get() = records.size
}

/**
 * Loader that orchestrates the lifecycle stages of plugins without runtime reflection.
 *
 * Distinct lifecycle phases:
 * 1. Discover: instantiate from build-time declared suppliers
 * 2. Register: add to [PluginRegistry] in [PluginState.REGISTERED]
 * 3. Initialize: invoke initialize(kernel), transition to [PluginState.INITIALIZED] (or [PluginState.FAILED])
 * 4. Activate: invoke start(), transition to [PluginState.ACTIVE] (or [PluginState.FAILED])
 */
class PluginLoader(
    private val staticPluginSuppliers: List<() -> KaonPlugin> = emptyList()
) {
    fun discover(): List<KaonPlugin> = staticPluginSuppliers.map { it() }

    fun registerAll(registry: PluginRegistry, plugins: List<KaonPlugin> = discover()) {
        for (plugin in plugins) {
            registry.register(plugin, PluginState.REGISTERED)
        }
    }

    fun initializeAll(registry: PluginRegistry, kernel: KaonKernel) {
        for (plugin in registry.all()) {
            val id = plugin.metadata.id
            if (registry.stateOf(id) != PluginState.REGISTERED) continue
            try {
                plugin.initialize(kernel)
                registry.updateState(id, PluginState.INITIALIZED)
            } catch (t: Throwable) {
                registry.updateState(id, PluginState.FAILED, t)
                kernel.logger.log(
                    KernelLogLevel.ERROR,
                    "Plugin '$id' failed during initialize(): ${t.message}",
                    t
                )
            }
        }
    }

    fun activate(id: String, registry: PluginRegistry, kernel: KaonKernel): Boolean {
        val record = registry.getRecord(id) ?: return false
        if (record.state != PluginState.INITIALIZED && record.state != PluginState.REGISTERED) {
            return record.state == PluginState.ACTIVE
        }
        val plugin = record.plugin
        return try {
            plugin.start()
            registry.updateState(id, PluginState.ACTIVE)
            true
        } catch (t: Throwable) {
            registry.updateState(id, PluginState.FAILED, t)
            kernel.logger.log(
                KernelLogLevel.ERROR,
                "Plugin '$id' failed during start(): ${t.message}",
                t
            )
            false
        }
    }

    fun activateAll(registry: PluginRegistry, kernel: KaonKernel) {
        for (plugin in registry.initializedPlugins()) {
            activate(plugin.metadata.id, registry, kernel)
        }
    }
}

/**
 * Central microkernel coordinating service registration, plugin lifecycles, and capabilities.
 */
class KaonKernel(
    val services: ServiceRegistry = ServiceRegistry(),
    val plugins: PluginRegistry = PluginRegistry(),
    val logger: KernelLogger = NoOpKernelLogger
) {
    var isInitialized: Boolean = false
        private set

    /**
     * Bootstrap the kernel with core services and build-time plugin suppliers.
     *
     * @param pluginSuppliers Build-time plugin factory list
     * @param activeUiPluginId Optional ID of the primary UI plugin that MUST activate successfully.
     *                         If specified and it fails, [UiPluginActivationException] is thrown.
     *                         Other plugins that fail are isolated without crashing the kernel.
     */
    fun bootstrap(
        pluginSuppliers: List<() -> KaonPlugin> = emptyList(),
        activeUiPluginId: String? = null
    ) {
        check(!isInitialized) { "KaonKernel is already initialized" }
        isInitialized = true
        val loader = PluginLoader(pluginSuppliers)

        // 1. Discover & Register
        loader.registerAll(plugins)

        // 2. Initialize
        loader.initializeAll(plugins, this)

        // 3. Activate plugins
        loader.activateAll(plugins, this)

        // 4. If a designated UI plugin was requested, verify it is ACTIVE
        if (activeUiPluginId != null) {
            val record = plugins.getRecord(activeUiPluginId)
            if (record == null) {
                throw UiPluginActivationException(
                    activeUiPluginId,
                    IllegalArgumentException("Plugin '$activeUiPluginId' not found in registry")
                )
            }
            if (record.state != PluginState.ACTIVE) {
                throw UiPluginActivationException(activeUiPluginId, record.failure)
            }
        }
    }

    /**
     * Shuts down all active plugins and cleans up registries.
     * Any failure during shutdown is recorded to [logger] instead of silently swallowed.
     */
    fun shutdown() {
        for (plugin in plugins.activePlugins()) {
            val id = plugin.metadata.id
            try {
                plugin.stop()
                plugins.updateState(id, PluginState.INITIALIZED)
            } catch (t: Throwable) {
                logger.log(
                    KernelLogLevel.ERROR,
                    "Error during stop() for plugin '$id': ${t.message}",
                    t
                )
            }
        }
        plugins.clear()
        services.clear()
        isInitialized = false
    }
}

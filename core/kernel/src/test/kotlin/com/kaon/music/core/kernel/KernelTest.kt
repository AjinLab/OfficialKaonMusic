package com.kaon.music.core.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class KernelTest {

    interface TestService {
        fun greet(): String
    }

    class TestServiceImpl : TestService {
        override fun greet(): String = "Hello from TestService"
    }

    interface AnotherService

    class AnotherServiceImpl : AnotherService

    @Test
    fun serviceRegistryRegistersAndRetrievesServices() {
        val registry = ServiceRegistry()
        val service = TestServiceImpl()

        registry.register<TestService>(service)

        assertTrue(registry.contains<TestService>())
        assertEquals("Hello from TestService", registry.get<TestService>().greet())
        assertEquals(service, registry.getOrNull<TestService>())
    }

    @Test
    fun serviceRegistryThrowsOnMissingService() {
        val registry = ServiceRegistry()

        try {
            registry.get<TestService>()
            fail("Expected ServiceNotFoundException")
        } catch (e: ServiceNotFoundException) {
            assertEquals(TestService::class, e.serviceClass)
        }
    }

    @Test
    fun serviceRegistryThreadSafety() {
        val registry = ServiceRegistry()
        val numThreads = 10
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)

        for (i in 0 until numThreads) {
            executor.submit {
                try {
                    registry.register(TestService::class, TestServiceImpl())
                    assertNotNull(registry.get(TestService::class))
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()
        assertTrue(registry.contains<TestService>())
    }

    @Test
    fun pluginRegistryDetectsDuplicates() {
        val registry = PluginRegistry()
        val plugin1 = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "test.plugin", name = "Test Plugin")
        }
        val plugin2 = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "test.plugin", name = "Test Plugin 2")
        }

        registry.register(plugin1)
        try {
            registry.register(plugin2)
            fail("Expected DuplicatePluginException")
        } catch (e: DuplicatePluginException) {
            assertEquals("test.plugin", e.id)
        }
    }

    @Test
    fun kernelBootstrapEnforcesStartupOrder() {
        val executionLog = mutableListOf<String>()

        val kernel = KaonKernel()
        kernel.services.register<TestService>(object : TestService {
            override fun greet(): String {
                executionLog.add("service_greet")
                return "ok"
            }
        })

        val plugin = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "order.plugin", name = "Order Plugin")

            override fun initialize(kernel: KaonKernel) {
                executionLog.add("plugin_initialize")
                val svc = kernel.services.get<TestService>()
                svc.greet()
            }

            override fun start() {
                executionLog.add("plugin_start")
            }

            override fun stop() {
                executionLog.add("plugin_stop")
            }
        }

        kernel.bootstrap(listOf { plugin })

        assertTrue(kernel.isInitialized)
        assertEquals(
            listOf("plugin_initialize", "service_greet", "plugin_start"),
            executionLog
        )
        assertEquals(PluginState.ACTIVE, kernel.plugins.stateOf("order.plugin"))

        kernel.shutdown()
        assertFalse(kernel.isInitialized)
        assertEquals(
            listOf("plugin_initialize", "service_greet", "plugin_start", "plugin_stop"),
            executionLog
        )
    }

    @Test
    fun onePluginFailingIsIsolatedAndOthersStillActivate() {
        val loggedErrors = mutableListOf<String>()
        val testLogger = KernelLogger { level, msg, _ ->
            if (level == KernelLogLevel.ERROR) loggedErrors.add(msg)
        }

        val kernel = KaonKernel(logger = testLogger)

        val failingPlugin = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "broken.plugin", name = "Broken Plugin")
            override fun initialize(kernel: KaonKernel) {
                error("Plugin crashed on init")
            }
        }

        val healthyPlugin = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "healthy.plugin", name = "Healthy Plugin")
            var started = false
            override fun start() {
                started = true
            }
        }

        kernel.bootstrap(listOf({ failingPlugin }, { healthyPlugin }))

        assertTrue(kernel.isInitialized)
        assertEquals(PluginState.FAILED, kernel.plugins.stateOf("broken.plugin"))
        assertEquals("Plugin crashed on init", kernel.plugins.failureOf("broken.plugin")?.message)
        assertEquals(PluginState.ACTIVE, kernel.plugins.stateOf("healthy.plugin"))
        assertEquals(1, loggedErrors.size)
        assertTrue(loggedErrors[0].contains("broken.plugin"))
    }

    @Test
    fun pluginCanBeRegisteredAndInitializedWithoutBecomingActive() {
        val registry = PluginRegistry()
        val kernel = KaonKernel(plugins = registry)
        val loader = PluginLoader()

        val alternatePlugin = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "alt.ui", name = "Alternate UI")
            var initialized = false
            var started = false

            override fun initialize(kernel: KaonKernel) {
                initialized = true
            }

            override fun start() {
                started = true
            }
        }

        loader.registerAll(registry, listOf(alternatePlugin))
        assertEquals(PluginState.REGISTERED, registry.stateOf("alt.ui"))

        loader.initializeAll(registry, kernel)
        assertEquals(PluginState.INITIALIZED, registry.stateOf("alt.ui"))
        assertTrue(alternatePlugin.initialized)
        assertFalse(alternatePlugin.started)

        // Does not activate until explicitly requested
        assertEquals(0, registry.activePlugins().size)
        loader.activate("alt.ui", registry, kernel)
        assertEquals(PluginState.ACTIVE, registry.stateOf("alt.ui"))
        assertTrue(alternatePlugin.started)
    }

    @Test
    fun designatedUiPluginFailureThrowsFast() {
        val kernel = KaonKernel()
        val failingUi = object : UiPlugin {
            override val metadata = PluginMetadata(id = "ui.primary", name = "Primary UI")
            override fun start() {
                error("Primary UI failed to start")
            }
        }

        try {
            kernel.bootstrap(
                pluginSuppliers = listOf { failingUi },
                activeUiPluginId = "ui.primary"
            )
            fail("Expected UiPluginActivationException")
        } catch (e: UiPluginActivationException) {
            assertEquals("ui.primary", e.id)
            assertEquals("Primary UI failed to start", e.cause?.message)
        }
    }

    @Test
    fun shutdownLogsPluginErrorsRatherThanSwallowingSilently() {
        val errorLogs = mutableListOf<String>()
        val testLogger = KernelLogger { level, msg, _ ->
            if (level == KernelLogLevel.ERROR) errorLogs.add(msg)
        }

        val kernel = KaonKernel(logger = testLogger)
        val crashingOnStopPlugin = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "crash.stop", name = "Crash On Stop")
            override fun stop() {
                throw RuntimeException("Explosion on stop")
            }
        }

        kernel.bootstrap(listOf { crashingOnStopPlugin })
        assertEquals(PluginState.ACTIVE, kernel.plugins.stateOf("crash.stop"))

        kernel.shutdown()

        assertEquals(1, errorLogs.size)
        assertTrue(errorLogs[0].contains("crash.stop"))
        assertTrue(errorLogs[0].contains("Explosion on stop"))
    }
}

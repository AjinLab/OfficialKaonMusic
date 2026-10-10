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
                // Verify service is available during plugin init
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

        kernel.shutdown()
        assertFalse(kernel.isInitialized)
        assertEquals(
            listOf("plugin_initialize", "service_greet", "plugin_start", "plugin_stop"),
            executionLog
        )
    }

    @Test
    fun kernelFailsFastOnPluginInitError() {
        val kernel = KaonKernel()
        val failingPlugin = object : KaonPlugin {
            override val metadata = PluginMetadata(id = "broken.plugin", name = "Broken Plugin")
            override fun initialize(kernel: KaonKernel) {
                error("Simulated plugin crash during init")
            }
        }

        try {
            kernel.bootstrap(listOf { failingPlugin })
            fail("Expected IllegalStateException from plugin init")
        } catch (e: IllegalStateException) {
            assertEquals("Simulated plugin crash during init", e.message)
        }
    }
}

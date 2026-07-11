package com.kaon.music.core.kernel.impl

import android.content.Context
import com.kaon.music.core.config.ConfigStore
import com.kaon.music.core.config.impl.DataStoreConfigStore
import com.kaon.music.core.event.EventBus
import com.kaon.music.core.event.impl.KaonEventBus
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.logger.Logger
import com.kaon.music.core.logger.impl.AndroidLogger
import com.kaon.music.core.permission.PermissionManager
import com.kaon.music.core.permission.impl.AndroidPermissionManager
import com.kaon.music.core.plugin.PluginLoader
import com.kaon.music.core.plugin.impl.KaonPluginLoader
import com.kaon.music.core.plugin.registry.PluginRegistry
import com.kaon.music.core.plugin.registry.impl.KaonPluginRegistry
import com.kaon.music.core.playback.PlaybackEngine
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.engine.ExoPlaybackEngine
import com.kaon.music.media.manager.MediaManager
import com.kaon.music.media.manager.QueueManager
import com.kaon.music.media.manager.QueuePersistence
import com.kaon.music.media.manager.DataStoreQueuePersistence
import com.kaon.music.media.services.MetadataProvider
import com.kaon.music.media.cache.AlbumArtCache
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkPaletteCache
import com.kaon.music.media.library.MediaRepository
import com.kaon.music.media.service.PlaybackService
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.library.db.LibraryDatabase
import android.view.Choreographer
import com.kaon.music.media.search.SearchResult
import com.kaon.music.core.diagnostics.DiagnosticsRegistry
import com.kaon.music.core.metrics.JankStatsMonitor
import com.kaon.music.core.metrics.RollingWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

class KaonKernel(private val context: Context) : Kernel {

    private val registry = ConcurrentHashMap<KClass<*>, Any>()
    private val started = AtomicBoolean(false)
    private var deferredScope: CoroutineScope? = null

    private val libraryDatabase by lazy {
        androidx.room.Room.databaseBuilder(
            context,
            LibraryDatabase::class.java,
            LibraryDatabase.DATABASE_NAME
        )
            .addMigrations(LibraryDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    init {
        registerCoreServices()
    }

    private fun registerCoreServices() {
        registerInfrastructure()
        registerPlayback()
        registerMedia()
        registerPlugins()
    }

    private fun registerInfrastructure() {
        register(EventBus::class, KaonEventBus())
        register(Logger::class, AndroidLogger())
        register(ConfigStore::class, DataStoreConfigStore(context))
        register(PermissionManager::class, AndroidPermissionManager(context))
        register(DiagnosticsRegistry::class, DiagnosticsRegistry(java.time.Clock.systemDefaultZone()))
        register(JankStatsMonitor::class, JankStatsMonitor(RollingWindow(300)))
    }

    private fun registerPlayback() {
        register(PlaybackEngine::class, ExoPlaybackEngine(context))
    }

    private fun registerMedia() {
        val queuePersistence = DataStoreQueuePersistence(get(ConfigStore::class))
        val queueManager = QueueManager()
        val metadataReader = MetadataProvider(context)
        val albumArtCache = AlbumArtCache(context)
        val artworkRepository = ArtworkRepository(context, albumArtCache, metadataReader)
        val paletteCache = ArtworkPaletteCache(albumArtCache)
        val artworkLoader = ArtworkLoader(paletteCache, artworkRepository)

        val mediaRepository = MediaRepository(context, libraryDatabase)
        val mediaManager = MediaManager(context, get(PlaybackEngine::class), queueManager, metadataReader, artworkLoader, queuePersistence, mediaRepository)

        register(AlbumArtCache::class, albumArtCache)
        register(ArtworkRepository::class, artworkRepository)
        register(ArtworkPaletteCache::class, paletteCache)
        register(ArtworkLoader::class, artworkLoader)
        register(MetadataProvider::class, metadataReader)
        register(QueuePersistence::class, queuePersistence)
        register(QueueManager::class, queueManager)
        register(MediaManager::class, mediaManager)
        register(PlayerController::class, mediaManager)
        register(LibraryDatabase::class, libraryDatabase)
        register(MediaRepository::class, mediaRepository)
        register(LibraryController::class, mediaRepository)
    }

    private fun registerPlugins() {
        val pluginRegistry = KaonPluginRegistry()
        register(PluginRegistry::class, pluginRegistry)
        register(PluginLoader::class, KaonPluginLoader(pluginRegistry))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> register(type: KClass<T>, instance: T) {
        registry[type] = instance
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(type: KClass<T>): T {
        return (registry[type] as? T)
            ?: error("Service not registered: ${type.qualifiedName}")
    }

    override fun contains(type: KClass<*>): Boolean {
        return registry.containsKey(type)
    }

    override suspend fun start() {
        if (!started.compareAndSet(false, true)) return

        val registry = get(DiagnosticsRegistry::class)
        registry.register(get(ArtworkRepository::class))
        registry.register(get(JankStatsMonitor::class))
        get(Logger::class).info(
            "Kernel",
            "Kernel Started"
        )

        val mediaManager = get(MediaManager::class)
        val library = get(LibraryController::class)
        
        // Critical: Restore playback state and start service immediately
        mediaManager.restoreState(library)
        mediaManager.refreshQueueState()
        context.startService(android.content.Intent(context, PlaybackService::class.java))

        // Defer non-critical work until after first frame
        Choreographer.getInstance().postFrameCallback {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            deferredScope = scope
            scope.launch {
                val db = get(LibraryDatabase::class)
                val state = db.libraryStateDao().getState()
                if (state == null || !state.legacyMigrated) {
                    val legacyDbFile = context.getDatabasePath("kaon_library.db")
                    if (legacyDbFile.exists()) {
                        legacyDbFile.delete()
                        context.getDatabasePath("kaon_library.db-journal").delete()
                        context.getDatabasePath("kaon_library.db-wal").delete()
                        context.getDatabasePath("kaon_library.db-shm").delete()
                    }
                }
                library.refresh()
                
                withContext(Dispatchers.Main) {
                    get(PluginLoader::class).loadBuiltInPlugins()
                }
            }
        }
    }

    override suspend fun stop() {
        deferredScope?.cancel()
        deferredScope = null

        if (contains(DiagnosticsRegistry::class)) {
            val registry = get(DiagnosticsRegistry::class)
            if (contains(ArtworkRepository::class)) {
                registry.unregister(get(ArtworkRepository::class))
            }
            if (contains(JankStatsMonitor::class)) {
                val monitor = get(JankStatsMonitor::class)
                monitor.stopTracking()
                registry.unregister(monitor)
            }
        }
        get(Logger::class).info(
            "Kernel",
            "Kernel Stopped"
        )
        if (contains(com.kaon.music.core.playback.PlaybackEngine::class)) {
            get(com.kaon.music.core.playback.PlaybackEngine::class).release()
        }

        started.set(false)
    }
}

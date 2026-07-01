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
import com.kaon.music.media.artwork.ArtworkPaletteCache
import com.kaon.music.media.library.MediaRepository
import com.kaon.music.media.service.PlaybackService
import com.kaon.music.media.library.LibraryController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class KaonKernel(private val context: Context) : Kernel {

    private val registry = ConcurrentHashMap<KClass<*>, Any>()

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
    }

    private fun registerPlayback() {
        register(PlaybackEngine::class, ExoPlaybackEngine(context))
    }

    private fun registerMedia() {
        val queuePersistence = DataStoreQueuePersistence(get(ConfigStore::class))
        val queueManager = QueueManager()
        val metadataReader = MetadataProvider(context)
        val albumArtCache = AlbumArtCache(context)
        val paletteCache = ArtworkPaletteCache()
        val artworkLoader = ArtworkLoader(context, albumArtCache, metadataReader, paletteCache)
        val libraryDatabase = androidx.room.Room.databaseBuilder(
            context,
            com.kaon.music.media.library.db.LibraryDatabase::class.java,
            com.kaon.music.media.library.db.LibraryDatabase.DATABASE_NAME
        ).build()

        val mediaRepository = MediaRepository(context, libraryDatabase)
        val mediaManager = MediaManager(context, get(PlaybackEngine::class), queueManager, metadataReader, artworkLoader, queuePersistence, mediaRepository)

        register(AlbumArtCache::class, albumArtCache)
        register(ArtworkPaletteCache::class, paletteCache)
        register(ArtworkLoader::class, artworkLoader)
        register(MetadataProvider::class, metadataReader)
        register(QueuePersistence::class, queuePersistence)
        register(QueueManager::class, queueManager)
        register(MediaManager::class, mediaManager)
        register(PlayerController::class, mediaManager)
        register(com.kaon.music.media.library.db.LibraryDatabase::class, libraryDatabase)
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
        get(Logger::class).info(
            "Kernel",
            "Kernel Started"
        )

        val library = get(LibraryController::class)
        val db = get(com.kaon.music.media.library.db.LibraryDatabase::class)
        CoroutineScope(Dispatchers.IO).launch {
            val state = db.libraryStateDao().getState()
            if (state == null || !state.legacyMigrated) {
                val legacyDbFile = context.getDatabasePath("kaon_library.db")
                if (legacyDbFile.exists()) {
                    legacyDbFile.delete()
                    context.getDatabasePath("kaon_library.db-journal").delete()
                    context.getDatabasePath("kaon_library.db-wal").delete()
                    context.getDatabasePath("kaon_library.db-shm").delete()
                }
                library.refresh()
            } else {
                library.refresh()
            }
        }

        val mediaManager = get(MediaManager::class)
        mediaManager.restoreState(library)
        mediaManager.refreshQueueState()

        context.startService(android.content.Intent(context, PlaybackService::class.java))

        get(PluginLoader::class).loadBuiltInPlugins()
    }

    override suspend fun stop() {
        get(Logger::class).info(
            "Kernel",
            "Kernel Stopped"
        )
    }
}

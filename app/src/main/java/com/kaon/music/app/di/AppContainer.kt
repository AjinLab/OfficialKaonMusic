package com.kaon.music.app.di

import android.content.Context
import com.kaon.music.core.artwork.ArtworkResolver
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.model.UserMessageBus
import com.kaon.music.core.data.online.YouTubeSessionManager
import com.kaon.music.core.data.repository.HistoryRepository
import com.kaon.music.core.data.repository.MetadataRepository
import com.kaon.music.core.data.repository.MetadataRepositoryImpl
import com.kaon.music.core.data.repository.PlaylistRepository
import com.kaon.music.core.data.repository.SettingsRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.sync.MediaStoreScanner
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.network.NetworkConnectivityMonitor
import com.kaon.music.core.network.NetworkMonitor
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.core.data.repository.DefaultLibraryController
import com.kaon.music.core.kernel.KaonKernel
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.PlayerController
import com.kaon.music.core.model.SettingsController
import com.kaon.music.ui.default.DefaultUiPlugin
import com.kaon.music.ui.minimal.MinimalUiPlugin
import com.kaon.music.ui.plugin.KaonUiPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application dependency container.
 *
 * ARCHITECTURE.md §4: manual constructor injection. 11 singletons; revisit a DI framework at ~15.
 * Every process-scoped collaborator is obtained from here — no component constructs its own copy of
 * a shared dependency (that produced four independent SettingsRepository instances reading the same
 * DataStore file).
 */
class AppContainer(private val context: Context) {

    /** Central microkernel coordinating service registration and build-time plugins. */
    val kernel: KaonKernel = KaonKernel()

    /**
     * The single application-lifetime coroutine scope (ARCHITECTURE.md §5.4).
     *
     * Fire-and-forget work that must outlive any screen or the playback service belongs here rather
     * than in a scope owned by an `object`, which can never be cancelled or observed.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The one app-level transient message channel (ARCHITECTURE.md §5.2). Collected once by the
     * root scaffold, so a message emitted anywhere — a screen, the playback facade, a sync failure —
     * reaches the user on whatever destination they are viewing.
     */
    val userMessageBus: UserMessageBus = UserMessageBus()

    val youtubeSessionManager: YouTubeSessionManager by lazy {
        YouTubeSessionManager(context)
    }

    val database: KaonDatabase by lazy {
        KaonDatabase.getInstance(context)
    }

    val mediaStoreScanner: MediaStoreScanner by lazy {
        MediaStoreScanner(context)
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            scanner = mediaStoreScanner,
            trackDao = database.trackDao()
        )
    }

    val trackRepository: TrackRepository by lazy {
        TrackRepository(
            trackDao = database.trackDao(),
            favoriteDao = database.favoriteDao(),
            syncEngine = syncEngine,
            playEventDao = database.playEventDao()
        )
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(
            playEventDao = database.playEventDao(),
            trackDao = database.trackDao(),
            trackRepository = trackRepository
        )
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(
            playlistDao = database.playlistDao(),
            trackDao = database.trackDao(),
            favoriteDao = database.favoriteDao(),
            trackRepository = trackRepository
        )
    }

    val artworkResolver: ArtworkResolver by lazy {
        ArtworkResolver(context, metadataRepository)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }

    val metadataRepository: MetadataRepository by lazy {
        MetadataRepositoryImpl(settingsRepository = settingsRepository)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkConnectivityMonitor(context)
    }

    val playbackFacade: PlaybackFacade by lazy {
        PlaybackFacade(
            context = context,
            trackRepository = trackRepository,
            settingsRepository = settingsRepository
        )
    }

    val playerController: PlayerController by lazy {
        playbackFacade
    }

    val libraryController: LibraryController by lazy {
        DefaultLibraryController(
            trackRepository = trackRepository,
            playlistRepository = playlistRepository,
            metadataRepository = metadataRepository,
            syncEngine = syncEngine,
            historyRepository = historyRepository
        )
    }

    val settingsController: SettingsController by lazy {
        settingsRepository
    }

    var activeUiPluginId: String = "com.kaon.music.ui.default"

    fun getActiveUiPlugin(): KaonUiPlugin {
        return kernel.plugins.getAs<KaonUiPlugin>(activeUiPluginId)
            ?: kernel.plugins.all().filterIsInstance<KaonUiPlugin>().firstOrNull()
            ?: DefaultUiPlugin()
    }

    fun bootstrapKernel() {
        // 1. Core services registration
        kernel.services.register(PlayerController::class, playerController)
        kernel.services.register(LibraryController::class, libraryController)
        kernel.services.register(SettingsController::class, settingsController)
        kernel.services.register(UserMessageBus::class, userMessageBus)

        // 2. Build-time compile plugins bootstrap
        kernel.bootstrap(
            listOf(
                { DefaultUiPlugin() },
                { MinimalUiPlugin() }
            )
        )
    }
}

package com.kaon.music.app.di

import android.content.Context
import com.kaon.music.core.artwork.ArtworkResolver
import com.kaon.music.core.data.db.KaonDatabase
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

    /**
     * The single application-lifetime coroutine scope (ARCHITECTURE.md §5.4).
     *
     * Fire-and-forget work that must outlive any screen or the playback service belongs here rather
     * than in a scope owned by an `object`, which can never be cancelled or observed.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            trackDao = database.trackDao()
        )
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(
            playlistDao = database.playlistDao(),
            trackDao = database.trackDao(),
            favoriteDao = database.favoriteDao()
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
}

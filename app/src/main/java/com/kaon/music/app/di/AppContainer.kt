package com.kaon.music.app.di

import android.content.Context
import com.kaon.music.core.artwork.ArtworkResolver
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.online.YouTubeSessionManager
import com.kaon.music.core.data.repository.HistoryRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.sync.MediaStoreScanner
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.playback.PlaybackFacade

/**
 * Clean Application Container managing singleton dependencies.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §1.3 & §16:
 * - Direct constructor injection avoiding speculative framework overhead.
 * - Centralizes database, sync engine, repositories, and the process-scoped PlaybackFacade.
 */
class AppContainer(private val context: Context) {

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

    val playlistRepository: com.kaon.music.core.data.repository.PlaylistRepository by lazy {
        com.kaon.music.core.data.repository.PlaylistRepository(
            playlistDao = database.playlistDao(),
            trackDao = database.trackDao(),
            favoriteDao = database.favoriteDao()
        )
    }

    val artworkResolver: ArtworkResolver by lazy {
        ArtworkResolver(context, metadataRepository)
    }

    val settingsRepository: com.kaon.music.core.data.repository.SettingsRepository by lazy {
        com.kaon.music.core.data.repository.SettingsRepository(context)
    }

    val metadataRepository: com.kaon.music.core.data.repository.MetadataRepository by lazy {
        com.kaon.music.core.data.repository.MetadataRepositoryImpl(
            settingsRepository = settingsRepository
        )
    }

    val playbackFacade: PlaybackFacade by lazy {
        PlaybackFacade(
            context = context,
            trackRepository = trackRepository
        )
    }
}

package com.kaon.music.core.online

import android.content.Context
import android.net.ConnectivityManager
import com.kaon.music.core.logging.Redact
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertubex.InnerTubeLogEvent
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult as InnerTubeXPoTokenResult
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.kaon.music.core.online.potoken.PoTokenGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * The single YouTube stream-extraction entry point.
 *
 * ARCHITECTURE.md §7. This replaces roughly 4,300 lines of vendored cipher machinery — a WebView
 * that executed YouTube's `player.js`, regex heuristics over minified JavaScript, a brute-force
 * `window` property scan, and a hand-rolled remote config table. That engine now lives upstream in
 * innertubex (`YouTubeCipherService` + `InnerTubeExtractor`), which solves the cipher with a QuickJS
 * engine instead of a WebView and is maintained against YouTube's roughly monthly player rotation.
 *
 * Kaon keeps what it owns: the resolver policy in
 * [com.kaon.music.core.playback.YouTubeStreamResolver] (deadline budget, retry classification,
 * generation-guarded caching, request coalescing, rate limiting) and the PoToken WebView, which is
 * the one part upstream also keeps app-side because it needs an Android WebView.
 */
object YouTubeStreamExtractor {

    private const val TAG = "resolve"
    private const val WEB_REMIX_FAILURE_TTL_MS = 5 * 60 * 1000L
    private const val DEFAULT_STREAM_TTL_SECONDS = 5 * 60

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var currentBundle: ExtractionBundle? = null

    private val bundleMutex = Mutex()
    private val webRemixFailures = ConcurrentHashMap<String, Long>()

    val isInitialized: Boolean get() = applicationContext != null

    /**
     * Records the application context. Cheap and non-blocking: no disk, no parsing, no network. The
     * previous initializer did three synchronous file reads and a 440-entry JSON parse on the main
     * thread during `Application.onCreate` (ARCHITECTURE.md §5.4).
     */
    @Synchronized
    fun initialize(context: Context) {
        if (applicationContext == null) applicationContext = context.applicationContext
    }

    /** Warms the cipher engine so the first playback does not pay for it. */
    suspend fun prewarm() {
        try {
            bundle().extractor.prewarm()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Cipher prewarm skipped")
        }
    }

    /**
     * Extracts a playable audio stream.
     *
     * Returns a [Result] rather than throwing so the caller's retry classification stays in one
     * place. [StreamResolveException] carries a typed [StreamResolveException.Reason]; network
     * reasons are unwrapped to their cause so the resolver's `IOException` check still applies.
     */
    suspend fun resolve(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        contentHints: ContentHints = ContentHints()
    ): Result<PlaybackData> {
        // Callers above this layer pass no hints; ContentHints is an innertubex type and must not
        // become part of the resolver's vocabulary (ARCHITECTURE.md §7).
        return try {
            extractInternal(videoId, playlistId, audioQuality, connectivityManager, contentHints)
        } catch (ce: CancellationException) {
            throw ce
        } catch (error: StreamResolveException) {
            val cause = error.cause
            Result.failure(
                if (error.reason == StreamResolveException.Reason.NETWORK && cause != null) cause else error
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun extractInternal(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        contentHints: ContentHints
    ): Result<PlaybackData> {
        val hints = contentHints
            .copy(
                isUploaded = contentHints.isUploaded == true ||
                    playlistId == "MLPT" ||
                    playlistId?.contains("MLPT") == true
            )
            .withStreamCapabilities(
                allowHls = false,
                // SABR needs a different playback pipeline than the progressive DataSource Kaon uses.
                allowSabr = false,
                allowBoundedRange = true
            )

        val excludedClients = buildSet {
            if (hasRecentWebRemixFailure(videoId)) add("WEB_REMIX")
        }

        val stream = bundle().extractor.extract(
            videoId = videoId,
            hints = hints,
            excludedClients = excludedClients,
            audioQuality = audioQuality.toInnerTubeX(connectivityManager),
            clientPlaybackNonce = generateClientPlaybackNonce()
        ) ?: return Result.failure(StreamExtractionException("No playable stream for $videoId"))

        if (stream.sabrBootstrap != null) {
            return Result.failure(StreamExtractionException("SABR stream is not playable by this engine"))
        }

        Timber.tag(TAG).d(
            "Extracted stream videoId=$videoId client=${stream.clientName} " +
                "itag=${stream.itag} url=${Redact.url(stream.audioUrl)}"
        )
        return Result.success(stream.toPlaybackData())
    }

    /**
     * Marks WEB_REMIX as failing for [videoId] so the next extraction skips it.
     *
     * Called on a 403/410 from the CDN, which usually means the URL that client produced was rejected.
     */
    fun markWebRemixFailed(videoId: String) {
        webRemixFailures[videoId] = System.currentTimeMillis()
    }

    fun clearWebRemixFailures() {
        webRemixFailures.clear()
    }

    /**
     * Asks the cipher service to re-fetch its player config after a stream rejection.
     *
     * Returns true when the config actually changed, in which case the WEB_REMIX skip list is stale
     * and is cleared.
     */
    suspend fun refreshAfterStreamRejection(): Boolean {
        val changed = bundle().cipherService.refreshAfterStreamRejection()
        if (changed) clearWebRemixFailures()
        return changed
    }

    private fun hasRecentWebRemixFailure(videoId: String): Boolean {
        val failedAt = webRemixFailures[videoId] ?: return false
        if ((System.currentTimeMillis() - failedAt) !in 0 until WEB_REMIX_FAILURE_TTL_MS) {
            webRemixFailures.remove(videoId, failedAt)
            return false
        }
        return true
    }

    /**
     * The extractor is rebuilt when the InnerTube transport generation advances — a proxy change
     * closes the old HTTP client, so anything holding it would fail on its next request.
     */
    private suspend fun bundle(): ExtractionBundle {
        val transport = YouTube.extractionTransport()
        currentBundle?.takeIf { it.transportGeneration == transport.generation }?.let { return it }

        return bundleMutex.withLock {
            val latest = YouTube.extractionTransport()
            currentBundle?.takeIf { it.transportGeneration == latest.generation }?.let { return@withLock it }

            try {
                currentBundle?.cipherService?.dispose()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "Previous cipher service disposal failed")
            }

            val remoteStore = RemotePlayerConfigStore(latest.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(latest.httpClient, remoteStore, logger)
            val extractor = InnerTubeExtractor(
                configParser = YtConfigParserImpl(latest.httpClient, latest.innerTube, remoteStore, logger),
                cipherService = cipherService,
                innerTube = latest.innerTube,
                tokenProvider = tokenProvider,
                logger = logger
            )
            ExtractionBundle(latest.generation, cipherService, extractor).also { currentBundle = it }
        }
    }

    private val configRepository: PlayerConfigRepository by lazy {
        SharedPreferencesPlayerConfigRepository(
            requireNotNull(applicationContext) { "YouTubeStreamExtractor is not initialized" }
        )
    }

    private val poTokenGenerator: PoTokenGenerator by lazy {
        PoTokenGenerator(requireNotNull(applicationContext) { "YouTubeStreamExtractor is not initialized" })
    }

    /** BotGuard attestation requires an Android WebView, so this stays app-side. */
    private val tokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(
            providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
            usesWebView = true
        )

        override suspend fun getPoToken(
            videoId: String,
            visitorData: String,
            cookie: String?
        ): InnerTubeXPoTokenResult? =
            poTokenGenerator.getWebClientPoToken(videoId, visitorData)?.let { token ->
                InnerTubeXPoTokenResult(
                    playerRequestToken = token.playerRequestPoToken,
                    streamingDataToken = token.streamingDataPoToken,
                    visitorData = visitorData
                )
            }

        override suspend fun close() {
            poTokenGenerator.close()
        }
    }

    /**
     * Bridges innertubex logging into Timber under the `resolve` tag (ARCHITECTURE.md §5.3), so
     * extraction diagnostics correlate with the resolver's own lines and are stripped from release.
     */
    private val logger = InnerTubeLogger { event -> log(event) }

    private fun log(event: InnerTubeLogEvent) {
        val details = if (event.details.isEmpty()) {
            ""
        } else {
            event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        }
        val message = event.message + details
        when (event.level) {
            InnerTubeLogLevel.DEBUG -> Timber.tag(TAG).d(message)
            InnerTubeLogLevel.INFO -> Timber.tag(TAG).i(message)
            InnerTubeLogLevel.WARN -> Timber.tag(TAG).w(message)
            InnerTubeLogLevel.ERROR -> Timber.tag(TAG).e(message)
        }
    }

    private data class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor
    )

    /**
     * Player-config cache backed by SharedPreferences.
     *
     * The values are a remote JSON blob plus its ETag and fetch timestamp — small, non-sensitive, and
     * read once per cipher-service construction, so DataStore's async API would buy nothing here.
     */
    private class SharedPreferencesPlayerConfigRepository(context: Context) : PlayerConfigRepository {
        private val preferences =
            context.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL

        override var cachedJson: String
            get() = preferences.getString(KEY_JSON, "").orEmpty()
            set(value) = preferences.edit().putString(KEY_JSON, value).apply()

        override var cachedAtMs: Long
            get() = preferences.getLong(KEY_CACHED_AT, 0L)
            set(value) = preferences.edit().putLong(KEY_CACHED_AT, value).apply()

        override var cachedSourceUrl: String
            get() = preferences.getString(KEY_SOURCE_URL, "").orEmpty()
            set(value) = preferences.edit().putString(KEY_SOURCE_URL, value).apply()

        override var cachedEtag: String
            get() = preferences.getString(KEY_ETAG, "").orEmpty()
            set(value) = preferences.edit().putString(KEY_ETAG, value).apply()

        private companion object {
            const val KEY_JSON = "json"
            const val KEY_CACHED_AT = "cached_at_ms"
            const val KEY_SOURCE_URL = "source_url"
            const val KEY_ETAG = "etag"
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }

    /**
     * AUTO downgrades on a metered network. A real bandwidth-aware policy is migration phase 5
     * (ARCHITECTURE.md §3.3); this preserves current behaviour.
     */
    private fun AudioQuality.toInnerTubeX(
        connectivityManager: ConnectivityManager
    ): InnerTubeXAudioQuality = when (this) {
        AudioQuality.HIGH -> InnerTubeXAudioQuality.HIGH
        AudioQuality.LOW -> InnerTubeXAudioQuality.LOW
        AudioQuality.AUTO ->
            if (connectivityManager.isActiveNetworkMetered) {
                InnerTubeXAudioQuality.LOW
            } else {
                InnerTubeXAudioQuality.AUTO
            }
    }

    private fun ExtractedStream.toPlaybackData(): PlaybackData {
        val fullMimeType = if (codecs.isNullOrBlank()) {
            mimeType.orEmpty()
        } else {
            "${mimeType.orEmpty()}; codecs=\"$codecs\""
        }
        return PlaybackData(
            streamUrl = audioUrl,
            streamHeaders = headers,
            streamClient = clientName,
            streamExpiresInSeconds = expiresInSecondsOrDefault(),
            itag = itag,
            mimeType = fullMimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            contentLengthBytes = contentLengthBytes,
            loudnessDb = loudnessDb,
            requireBoundedRange = requireBoundedRange,
            rangeChunkSizeBytes = rangeChunkSizeBytes,
            useRangeChunks = useRangeChunks,
            audioConfig = if (loudnessDb != null || perceptualLoudnessDb != null) {
                PlayerResponse.PlayerConfig.AudioConfig(loudnessDb, perceptualLoudnessDb)
            } else {
                null
            }
        )
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun ExtractedStream.expiresInSecondsOrDefault(): Int {
        val expiry = expiresAt ?: return DEFAULT_STREAM_TTL_SECONDS
        val remainingMs = expiry.toEpochMilliseconds() - System.currentTimeMillis()
        return (remainingMs / 1000L).toInt().coerceAtLeast(1)
    }
}

/** Extraction failed for a reason that is not worth retrying. */
class StreamExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A resolved audio stream plus the format facts the player and UI need.
 *
 * Deliberately Kaon-owned rather than a re-export of innertubex or InnerTube DTO types: this is the
 * boundary the resolver and playback layer see (ARCHITECTURE.md §7).
 */
data class PlaybackData(
    val streamUrl: String,
    val streamHeaders: Map<String, String>,
    val streamClient: String,
    val streamExpiresInSeconds: Int,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int?,
    val sampleRate: Int?,
    val contentLengthBytes: Long?,
    val loudnessDb: Double?,
    val requireBoundedRange: Boolean,
    val rangeChunkSizeBytes: Long,
    val useRangeChunks: Boolean,
    val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?
)

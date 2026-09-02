package com.kaon.music.core.data.repository

import android.util.LruCache
import com.kaon.music.core.data.model.AlbumMetadata
import com.kaon.music.core.data.model.ArtistMetadata
import com.kaon.music.core.data.model.LyricLine
import com.kaon.music.core.data.model.LyricsResult
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackMetadata
import com.landofoz.musicmeta.ApiKeyConfig
import com.landofoz.musicmeta.EnrichmentData
import com.landofoz.musicmeta.EnrichmentEngine
import com.landofoz.musicmeta.EnrichmentIdentifiers
import com.landofoz.musicmeta.EnrichmentRequest
import com.landofoz.musicmeta.EnrichmentType
import com.landofoz.musicmeta.http.DefaultHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class MetadataRepositoryImpl(
    private val settingsRepository: SettingsRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MetadataRepository {

    private val lyricsCache = LruCache<String, LyricsResult>(100)
    private val trackMetadataCache = LruCache<String, TrackMetadata>(100)
    private val albumMetadataCache = LruCache<String, AlbumMetadata>(100)
    private val artistMetadataCache = LruCache<String, ArtistMetadata>(100)
    private val albumArtUrlCache = LruCache<String, String>(200)
    private val artistPhotoUrlCache = LruCache<String, String>(200)
    private val trackArtworkUrlCache = LruCache<String, String>(200)

    private val httpClient = DefaultHttpClient(userAgent = "KaonMusic/1.0.0 (https://github.com/KaonMusic)")
    private val engineMutex = Mutex()
    private var currentLastFmKey: String = ""
    private var currentFanartTvKey: String = ""
    private var currentDiscogsToken: String = ""
    private var cachedEngine: EnrichmentEngine? = null

    private suspend fun getEngine(): EnrichmentEngine {
        val settings = settingsRepository?.userSettingsFlow?.firstOrNull()
        val lastFmKey = settings?.lastFmApiKey.orEmpty().trim()
        val fanartTvKey = settings?.fanartTvApiKey.orEmpty().trim()
        val discogsToken = settings?.discogsToken.orEmpty().trim()

        return engineMutex.withLock {
            if (cachedEngine == null ||
                currentLastFmKey != lastFmKey ||
                currentFanartTvKey != fanartTvKey ||
                currentDiscogsToken != discogsToken
            ) {
                currentLastFmKey = lastFmKey
                currentFanartTvKey = fanartTvKey
                currentDiscogsToken = discogsToken

                val apiConfig = ApiKeyConfig(
                    lastFmKey = lastFmKey.ifBlank { null },
                    fanartTvProjectKey = fanartTvKey.ifBlank { null },
                    discogsPersonalToken = discogsToken.ifBlank { null }
                )

                cachedEngine = EnrichmentEngine.Builder()
                    .httpClient(httpClient)
                    .apiKeys(apiConfig)
                    .withDefaultProviders()
                    .build()
            }
            cachedEngine!!
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""(?i)[\(\[](?:official\s+(?:music\s+)?video|official\s+audio|audio|lyrics|lyric\s+video|full\s+song|remaster(?:ed)?|video|dance\s+cover|cover|4k|hd|visualizer|live)[\)\]]"""), "")
            .replace(Regex("""\|.*$"""), "")
            .replace(Regex("""\s+-\s+.*$"""), "")
            .trim()
            .ifBlank { title.trim() }
    }

    private fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("""(?i)[\(\[](?:topic|vevo|official|music)[\)\]]"""), "")
            .replace(Regex("""(?i)\s+-\s+topic$"""), "")
            .trim()
            .ifBlank { artist.trim() }
    }

    override suspend fun getLyrics(track: Track): LyricsResult? = withContext(ioDispatcher) {
        val qArtist = cleanArtist(track.artist)
        val qTitle = cleanTitle(track.title)
        val cacheKey = "lyrics_${qArtist.lowercase()}_${qTitle.lowercase()}"
        lyricsCache.get(cacheKey)?.let { return@withContext it }

        try {
            val engine = getEngine()
            val request = EnrichmentRequest.ForTrack(
                identifiers = EnrichmentIdentifiers(),
                title = qTitle,
                artist = qArtist,
                album = track.album.trim(),
                durationMs = track.durationMs
            )

            val results = engine.enrich(
                request = request,
                types = setOf(EnrichmentType.LYRICS_SYNCED, EnrichmentType.LYRICS_PLAIN),
                forceRefresh = false
            )

            val lyricsData = results.lyrics()
            if (lyricsData != null) {
                val syncedLines = parseLrc(lyricsData.syncedLyrics)
                val plain = lyricsData.plainLyrics
                val isInstrumental = lyricsData.isInstrumental

                val lyricsResult = LyricsResult(
                    syncedLyrics = syncedLines,
                    plainLyrics = plain,
                    isInstrumental = isInstrumental,
                    source = "LRCLIB"
                )

                lyricsCache.put(cacheKey, lyricsResult)
                return@withContext lyricsResult
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Failed to retrieve lyrics for track: ${track.title} by ${track.artist}")
        }
        null
    }

    override suspend fun getTrackMetadata(track: Track): TrackMetadata? = withContext(ioDispatcher) {
        val qArtist = cleanArtist(track.artist)
        val qTitle = cleanTitle(track.title)
        val cacheKey = "track_${qArtist.lowercase()}_${qTitle.lowercase()}"
        trackMetadataCache.get(cacheKey)?.let { return@withContext it }

        try {
            val engine = getEngine()
            val request = EnrichmentRequest.ForTrack(
                identifiers = EnrichmentIdentifiers(),
                title = qTitle,
                artist = qArtist,
                album = track.album.trim(),
                durationMs = track.durationMs
            )

            val results = engine.enrich(
                request = request,
                types = setOf(
                    EnrichmentType.GENRE,
                    EnrichmentType.RELEASE_DATE,
                    EnrichmentType.LABEL,
                    EnrichmentType.CREDITS,
                    EnrichmentType.TRACK_PREVIEW,
                    EnrichmentType.TRACK_POPULARITY,
                    EnrichmentType.LYRICS_SYNCED,
                    EnrichmentType.LYRICS_PLAIN
                ),
                forceRefresh = false
            )

            val genres = results.genres()
            val releaseDate = results.releaseDate()
            val label = results.label()
            val metadataItem = results.get<EnrichmentData.Metadata>(EnrichmentType.ALBUM_METADATA)
            val isrc = metadataItem?.isrc
            val credits = results.credits()?.credits?.map { it.toString() }.orEmpty()
            val previewUrl = results.trackPreview()?.url
            val lyricsData = results.lyrics()
            val lyricsResult = if (lyricsData != null) {
                LyricsResult(
                    syncedLyrics = parseLrc(lyricsData.syncedLyrics),
                    plainLyrics = lyricsData.plainLyrics,
                    isInstrumental = lyricsData.isInstrumental,
                    source = "LRCLIB"
                )
            } else null

            val metadata = TrackMetadata(
                title = track.title,
                artist = track.artist,
                album = track.album,
                lyrics = lyricsResult,
                genres = genres,
                releaseDate = releaseDate,
                label = label,
                isrc = isrc,
                credits = credits,
                previewUrl = previewUrl
            )

            trackMetadataCache.put(cacheKey, metadata)
            return@withContext metadata
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Failed to retrieve track metadata for: ${track.title} by ${track.artist}")
        }
        null
    }

    override suspend fun getAlbumMetadata(albumTitle: String, artistName: String): AlbumMetadata? = withContext(ioDispatcher) {
        val cleanAlbum = albumTitle.trim()
        val cleanArtist = artistName.trim()
        val cacheKey = "album_${cleanArtist.lowercase()}_${cleanAlbum.lowercase()}"
        albumMetadataCache.get(cacheKey)?.let { return@withContext it }

        try {
            val engine = getEngine()
            val request = EnrichmentRequest.ForAlbum(
                identifiers = EnrichmentIdentifiers(),
                title = cleanAlbum,
                artist = cleanArtist,
                year = null,
                trackCount = null
            )

            val results = engine.enrich(
                request = request,
                types = setOf(
                    EnrichmentType.ALBUM_ART,
                    EnrichmentType.ALBUM_ART_BACK,
                    EnrichmentType.CD_ART,
                    EnrichmentType.ALBUM_BOOKLET,
                    EnrichmentType.GENRE,
                    EnrichmentType.RELEASE_DATE,
                    EnrichmentType.RELEASE_TYPE,
                    EnrichmentType.LABEL,
                    EnrichmentType.COUNTRY,
                    EnrichmentType.ALBUM_METADATA,
                    EnrichmentType.ALBUM_TRACKS
                ),
                forceRefresh = false
            )

            val coverArtUrl = results.albumArt()?.url ?: results.get<EnrichmentData.Artwork>(EnrichmentType.ALBUM_ART)?.url
            val backCoverUrl = results.get<EnrichmentData.Artwork>(EnrichmentType.ALBUM_ART_BACK)?.url
            val cdArtUrl = results.get<EnrichmentData.Artwork>(EnrichmentType.CD_ART)?.url
            val bookletUrl = results.get<EnrichmentData.Artwork>(EnrichmentType.ALBUM_BOOKLET)?.url
            val genres = results.genres()
            val label = results.label()
            val releaseDate = results.releaseDate()
            val releaseType = results.releaseType()
            val country = results.country()
            val trackCount = results.get<EnrichmentData.Metadata>(EnrichmentType.ALBUM_METADATA)?.trackCount

            val metadata = AlbumMetadata(
                title = cleanAlbum,
                artist = cleanArtist,
                coverArtUrl = coverArtUrl,
                backCoverUrl = backCoverUrl,
                cdArtUrl = cdArtUrl,
                bookletUrl = bookletUrl,
                genres = genres,
                label = label,
                releaseDate = releaseDate,
                releaseType = releaseType,
                country = country,
                trackCount = trackCount
            )

            albumMetadataCache.put(cacheKey, metadata)
            if (!coverArtUrl.isNullOrBlank()) {
                albumArtUrlCache.put(cacheKey, coverArtUrl)
            }
            return@withContext metadata
        } catch (e: Exception) {
            Timber.w(e, "Failed to retrieve album metadata for: $cleanAlbum by $cleanArtist")
        }
        null
    }

    override suspend fun getArtistMetadata(artistName: String): ArtistMetadata? = withContext(ioDispatcher) {
        val cleanArtist = artistName.trim()
        val cacheKey = "artist_${cleanArtist.lowercase()}"
        artistMetadataCache.get(cacheKey)?.let { return@withContext it }

        try {
            val engine = getEngine()
            val request = EnrichmentRequest.ForArtist(
                identifiers = EnrichmentIdentifiers(),
                name = cleanArtist
            )

            val results = engine.enrich(
                request = request,
                types = setOf(
                    EnrichmentType.ARTIST_PHOTO,
                    EnrichmentType.ARTIST_BACKGROUND,
                    EnrichmentType.ARTIST_LOGO,
                    EnrichmentType.ARTIST_BANNER,
                    EnrichmentType.ARTIST_BIO,
                    EnrichmentType.GENRE,
                    EnrichmentType.SIMILAR_ARTISTS,
                    EnrichmentType.ARTIST_TOP_TRACKS,
                    EnrichmentType.ARTIST_POPULARITY
                ),
                forceRefresh = false
            )

            val photoUrl = results.artistPhoto()?.url ?: results.get<EnrichmentData.Artwork>(EnrichmentType.ARTIST_PHOTO)?.url
            val backgroundUrl = results.get<EnrichmentData.Artwork>(EnrichmentType.ARTIST_BACKGROUND)?.url
            val logoUrl = results.get<EnrichmentData.Artwork>(EnrichmentType.ARTIST_LOGO)?.url
            val bannerUrl = results.get<EnrichmentData.Artwork>(EnrichmentType.ARTIST_BANNER)?.url
            val bioData = results.biography()
            val biography = bioData?.text
            val biographySource = bioData?.source
            val genres = results.genres()
            val similarArtists = results.similarArtists()?.artists?.map { it.toString() }.orEmpty()
            val topTracks = results.topTracks()?.tracks?.map { it.toString() }.orEmpty()

            val metadata = ArtistMetadata(
                name = cleanArtist,
                photoUrl = photoUrl,
                backgroundUrl = backgroundUrl,
                logoUrl = logoUrl,
                bannerUrl = bannerUrl,
                biography = biography,
                biographySource = biographySource,
                genres = genres,
                similarArtists = similarArtists,
                topTracks = topTracks
            )

            artistMetadataCache.put(cacheKey, metadata)
            if (!photoUrl.isNullOrBlank()) {
                artistPhotoUrlCache.put(cacheKey, photoUrl)
            }
            return@withContext metadata
        } catch (e: Exception) {
            Timber.w(e, "Failed to retrieve artist metadata for: $cleanArtist")
        }
        null
    }

    override suspend fun getAlbumCoverArtUrl(albumTitle: String, artistName: String): String? = withContext(ioDispatcher) {
        val cleanAlbum = albumTitle.trim()
        val cleanArtist = artistName.trim()
        if (cleanAlbum.isBlank()) return@withContext null
        val cacheKey = "album_${cleanArtist.lowercase()}_${cleanAlbum.lowercase()}"
        albumArtUrlCache.get(cacheKey)?.let { return@withContext it }
        albumMetadataCache.get(cacheKey)?.coverArtUrl?.let {
            albumArtUrlCache.put(cacheKey, it)
            return@withContext it
        }

        try {
            val engine = getEngine()
            val request = EnrichmentRequest.ForAlbum(
                identifiers = EnrichmentIdentifiers(),
                title = cleanAlbum,
                artist = cleanArtist
            )
            val results = engine.enrich(
                request = request,
                types = setOf(EnrichmentType.ALBUM_ART),
                forceRefresh = false
            )
            val coverUrl = results.albumArt()?.url ?: results.get<EnrichmentData.Artwork>(EnrichmentType.ALBUM_ART)?.url
            if (!coverUrl.isNullOrBlank()) {
                albumArtUrlCache.put(cacheKey, coverUrl)
                return@withContext coverUrl
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get album cover art for: $cleanAlbum by $cleanArtist")
        }
        null
    }

    override suspend fun getArtistPhotoUrl(artistName: String): String? = withContext(ioDispatcher) {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank()) return@withContext null
        val cacheKey = "artist_${cleanArtist.lowercase()}"
        artistPhotoUrlCache.get(cacheKey)?.let { return@withContext it }
        artistMetadataCache.get(cacheKey)?.photoUrl?.let {
            artistPhotoUrlCache.put(cacheKey, it)
            return@withContext it
        }

        try {
            val engine = getEngine()
            val request = EnrichmentRequest.ForArtist(
                identifiers = EnrichmentIdentifiers(),
                name = cleanArtist
            )
            val results = engine.enrich(
                request = request,
                types = setOf(EnrichmentType.ARTIST_PHOTO),
                forceRefresh = false
            )
            val photoUrl = results.artistPhoto()?.url ?: results.get<EnrichmentData.Artwork>(EnrichmentType.ARTIST_PHOTO)?.url
            if (!photoUrl.isNullOrBlank()) {
                artistPhotoUrlCache.put(cacheKey, photoUrl)
                return@withContext photoUrl
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get artist photo for: $cleanArtist")
        }
        null
    }

    override suspend fun getTrackArtworkUrl(track: Track): String? = withContext(ioDispatcher) {
        if (track.isOnline && track.contentUri != null) {
            return@withContext track.contentUri.toString()
        }
        val cacheKey = "track_art_${track.artist.lowercase().trim()}_${track.album.lowercase().trim()}"
        trackArtworkUrlCache.get(cacheKey)?.let { return@withContext it }

        if (track.album.isNotBlank()) {
            val albumArt = getAlbumCoverArtUrl(track.album, track.artist)
            if (!albumArt.isNullOrBlank()) {
                trackArtworkUrlCache.put(cacheKey, albumArt)
                return@withContext albumArt
            }
        }

        if (track.artist.isNotBlank()) {
            val artistPhoto = getArtistPhotoUrl(track.artist)
            if (!artistPhoto.isNullOrBlank()) {
                trackArtworkUrlCache.put(cacheKey, artistPhoto)
                return@withContext artistPhoto
            }
        }
        null
    }

    override suspend fun getTrackPreviewUrl(track: Track): String? = withContext(ioDispatcher) {
        getTrackMetadata(track)?.previewUrl
    }

    companion object {
        fun parseLrc(lrcContent: String?): List<LyricLine> {
            if (lrcContent.isNullOrBlank()) return emptyList()
            val lines = mutableListOf<LyricLine>()
            val lrcRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")

            for (rawLine in lrcContent.lines()) {
                val trimmed = rawLine.trim()
                val match = lrcRegex.matchEntire(trimmed)
                if (match != null) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3]
                    val ms = when (fractionStr.length) {
                        1 -> fractionStr.toLong() * 100
                        2 -> fractionStr.toLong() * 10
                        3 -> fractionStr.toLong()
                        else -> 0L
                    }
                    val timestampMs = (min * 60 + sec) * 1000 + ms
                    val text = match.groupValues[4].trim()
                    lines.add(LyricLine(timestampMs, text))
                }
            }
            return lines.sortedBy { it.timestampMs }
        }
    }
}

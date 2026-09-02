package com.kaon.music.core.data.repository

import com.kaon.music.core.data.model.AlbumMetadata
import com.kaon.music.core.data.model.ArtistMetadata
import com.kaon.music.core.data.model.LyricsResult
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackMetadata

interface MetadataRepository {
    suspend fun getLyrics(track: Track): LyricsResult?
    suspend fun getTrackMetadata(track: Track): TrackMetadata?
    suspend fun getAlbumMetadata(albumTitle: String, artistName: String): AlbumMetadata?
    suspend fun getArtistMetadata(artistName: String): ArtistMetadata?
    suspend fun getAlbumCoverArtUrl(albumTitle: String, artistName: String): String?
    suspend fun getArtistPhotoUrl(artistName: String): String?
    suspend fun getTrackArtworkUrl(track: Track): String?
    suspend fun getTrackPreviewUrl(track: Track): String?
}

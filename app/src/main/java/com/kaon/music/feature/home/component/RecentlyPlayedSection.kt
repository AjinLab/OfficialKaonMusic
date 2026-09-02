package com.kaon.music.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.component.EmptyStateView
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary

@Composable
fun RecentlyPlayedSection(
    recentTracks: List<Track>,
    recentAlbums: List<Album>,
    recentArtists: List<Artist>,
    onTrackClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    onExploreMusicClick: () -> Unit = onSeeAllClick
) {
    if (recentTracks.isEmpty() && recentAlbums.isEmpty() && recentArtists.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.MusicNote,
            title = "Start Your Journey",
            message = "Play songs from your library or search YouTube Music to populate your personal feed.",
            actionLabel = "Explore Tracks",
            onActionClick = onExploreMusicClick,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Played",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = KaonTextPrimary
            )

            TextButton(onClick = onSeeAllClick) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = KaonTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Display distinct recent albums
            items(recentAlbums.take(6), key = { "album_${it.albumId}" }) { album ->
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAlbumClick(album) }
                ) {
                    ArtworkImage(
                        albumId = album.albumId,
                        album = album.title,
                        artist = album.artist,
                        sizeBucket = SizeBucket.FULL,
                        modifier = Modifier.size(130.dp),
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = album.displayTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = KaonTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Display top artists with photo
            items(recentArtists.take(4), key = { "artist_${it.artistId}_${it.name}" }) { artist ->
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onArtistClick(artist) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ArtworkImage(
                        artistName = artist.name,
                        isArtist = true,
                        cornerRadius = 65.dp,
                        modifier = Modifier.size(130.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = KaonTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonTextSecondary
                    )
                }
            }

            // Display recent individual tracks
            items(recentTracks.take(6), key = { "track_${it.id}" }) { track ->
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTrackClick(track) }
                ) {
                    ArtworkImage(
                        track = track,
                        sizeBucket = SizeBucket.FULL,
                        modifier = Modifier.size(130.dp),
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = track.displayTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = KaonTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

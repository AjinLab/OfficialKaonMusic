package com.kaon.music.plugins.defaultui.screens.album

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.plugins.defaultui.components.AsyncImage
import com.kaon.music.plugins.defaultui.components.TrackListItem
import com.kaon.music.plugins.defaultui.theme.KaonColors
import com.kaon.music.plugins.defaultui.viewmodels.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onNavigateUp: () -> Unit
) {
    val albumDetail by viewModel.albumDetail.collectAsState(initial = null)

    if (albumDetail == null) return
    val album = albumDetail!!.album
    val songs = albumDetail!!.songs

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ALBUM",
                            style = MaterialTheme.typography.labelSmall,
                            color = KaonColors.TextSecondary
                        )
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Album header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                AsyncImage(
                    path = songs.firstOrNull()?.artworkPath,
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = 4.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val yearStr = album.year?.let { "$it • " } ?: ""
                    Text(
                        text = "$yearStr${album.songCount} Songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonColors.TextSecondary
                    )
                }
            }

            HorizontalDivider(color = KaonColors.Divider)

            // Track list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id }
                ) { index, song ->
                    TrackListItem(
                        trackNumber = "%02d".format(song.track.takeIf { it > 0 } ?: (index + 1)),
                        title = song.title,
                        artworkPath = song.artworkPath,
                        onPlay = { viewModel.playSongs(songs, index) },
                        onFavorite = { /* TODO */ },
                        onMore = { /* TODO */ },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

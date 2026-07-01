package com.kaon.music.plugins.defaultui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaon.music.plugins.defaultui.theme.KaonColors
import com.kaon.music.plugins.defaultui.viewmodels.LibraryViewModel

@Composable
fun ArtistsTab(viewModel: LibraryViewModel, onArtistClick: (Long) -> Unit) {
    val artists by viewModel.artists.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = artists,
            key = { it.id }
        ) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .animateItem(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${artist.albumCount} Albums • ${artist.songCount} Songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonColors.TextSecondary
                    )
                }
            }
        }
    }
}

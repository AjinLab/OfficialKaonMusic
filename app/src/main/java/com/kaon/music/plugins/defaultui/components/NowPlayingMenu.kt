package com.kaon.music.plugins.defaultui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaon.music.core.playback.PlayerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingMenu(
    playerController: PlayerController,
    onDismiss: () -> Unit,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit
) {
    val currentSong by playerController.currentSong.collectAsState()
    val song = currentSong ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            ListItem(
                headlineContent = { Text(song.title) },
                supportingContent = { Text("${song.artist} • ${song.album}") },
                leadingContent = {
                    ArtworkImage(
                        artwork = song.artwork,
                        modifier = Modifier.size(40.dp)
                    )
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ListItem(
                headlineContent = { Text("Go to Album") },
                leadingContent = { Icon(Icons.Rounded.Album, contentDescription = null) },
                modifier = Modifier.clickable {
                    onGoToAlbum(song.albumId)
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text("Go to Artist") },
                leadingContent = { Icon(Icons.Rounded.Person, contentDescription = null) },
                modifier = Modifier.clickable {
                    onGoToArtist(song.artistId)
                    onDismiss()
                }
            )
        }
    }
}

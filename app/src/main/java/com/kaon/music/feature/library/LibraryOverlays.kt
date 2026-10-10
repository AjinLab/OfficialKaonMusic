package com.kaon.music.feature.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.data.model.Track
import com.kaon.music.feature.library.playlist.AddToPlaylistBottomSheet

/**
 * State for the add-to-playlist sheet and the new-playlist dialog, owned by the root scaffold so a
 * single instance serves every destination (Phase 6A §4: one mechanism, not one per screen).
 */
class AddToPlaylistUiState {
    var trackForAddToPlaylist by mutableStateOf<Track?>(null)
    var showCreatePlaylistDialog by mutableStateOf(false)
    var trackToAppendAfterCreate by mutableStateOf<Track?>(null)
}

/**
 * The add-to-playlist sheet and new-playlist dialog, rendered once at the root.
 *
 * These are genuine modal overlays: they exist above any destination and the sheet consumes its own
 * dismissal, so they deliberately stay OUT of the navigation stack.
 */
@Composable
fun AddToPlaylistDialogs(
    viewModel: LibraryViewModel,
    state: AddToPlaylistUiState,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.trackForAddToPlaylist != null) {
        AddToPlaylistBottomSheet(
            track = state.trackForAddToPlaylist!!,
            playlists = playlists.playlists,
            onDismiss = { state.trackForAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                val track = state.trackForAddToPlaylist!!
                viewModel.addTrackToPlaylist(playlist.id, track, track.displayTitle, playlist.name)
                state.trackForAddToPlaylist = null
            },
            onCreateNewPlaylist = {
                state.trackToAppendAfterCreate = state.trackForAddToPlaylist
                state.trackForAddToPlaylist = null
                state.showCreatePlaylistDialog = true
            }
        )
    }

    if (state.showCreatePlaylistDialog) {
        var playlistNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { state.showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { if (it.length <= 50) playlistNameInput = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("My Awesome Playlist") },
                    singleLine = true,
                    modifier = Modifier
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = playlistNameInput.trim()
                        if (name.isNotBlank()) {
                            viewModel.createPlaylist(name, state.trackToAppendAfterCreate)
                            state.showCreatePlaylistDialog = false
                            state.trackToAppendAfterCreate = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    state.showCreatePlaylistDialog = false
                    state.trackToAppendAfterCreate = null
                }) {
                    Text("Cancel", color = KaonTextSecondary)
                }
            },
            modifier = modifier
        )
    }
}

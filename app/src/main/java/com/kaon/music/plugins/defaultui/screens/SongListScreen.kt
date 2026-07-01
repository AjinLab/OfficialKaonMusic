package com.kaon.music.plugins.defaultui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaon.music.plugins.defaultui.viewmodels.LibraryViewModel
import androidx.compose.foundation.lazy.items

@Composable
fun SongListScreen(
    viewModel: LibraryViewModel
) {
    val songs by viewModel.songs.collectAsState()

    LazyColumn {
        items(songs) { song ->
            Text(
                text = "${song.title} - ${song.artist}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Log.d("KAON", "TITLE=${song.title}")
                        Log.d("KAON", "URI=${song.uri}")
                        viewModel.playSong(song)
                    }
                    .padding(16.dp)
            )
        }
    }
}

package com.kaon.music

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.kaon.music.core.config.ConfigKeys
import com.kaon.music.core.config.impl.DataStoreConfigStore
import com.kaon.music.core.event.PluginLoadedEvent
import com.kaon.music.core.kernel.impl.KaonKernel
import com.kaon.music.core.plugin.TestPlugin
import com.kaon.music.media.ExoPlaybackEngine
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import com.kaon.music.media.MediaManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import com.kaon.music.ui.theme.KaonMusicTheme
import androidx.compose.material3.Button
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val kernel = KaonKernel()

    private lateinit var mediaManager: MediaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaManager = MediaManager(
            ExoPlaybackEngine(this)
        )

        Log.d("KAON", "onCreate reached")

        val configStore = DataStoreConfigStore(this)

        enableEdgeToEdge()

        setContent {
            KaonMusicTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { padding ->

                    Greeting(
                        mediaManager = mediaManager,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }

        lifecycleScope.launch {

            configStore.putString(
                ConfigKeys.THEME_MODE,
                "dark"
            )

            configStore.getString(
                ConfigKeys.THEME_MODE
            ).collect {
                println("THEME = $it")
            }

            kernel.start()

            kernel.eventBus.subscribe(
                PluginLoadedEvent::class
            ) {
                Log.d(
                    "KAON",
                    "Plugin Loaded: ${it.pluginId}"
                )
            }

            kernel.pluginLoader.register(
                TestPlugin(kernel.eventBus)
            )
        }
    }
}

@Composable
fun Greeting(
    mediaManager: MediaManager,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                mediaManager.loadResource(
                    R.raw.test_song
                )
                mediaManager.play()
            }
        ) {
            Text("Play Test Song")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Text("Preview")
}

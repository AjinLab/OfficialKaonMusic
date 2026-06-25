package com.application.kaonmusic

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.kaon.core.event.PluginLoadedEvent
import com.kaon.core.kernel.impl.KaonKernel
import com.kaon.core.plugin.TestPlugin
import com.kaon.ui.theme.KaonMusicTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val kernel = KaonKernel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("KAON", "onCreate reached")

        enableEdgeToEdge()

        setContent {
            Greeting("Android")
        }

        lifecycleScope.launch {

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
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KaonMusicTheme {
        Greeting("Android")
    }
}

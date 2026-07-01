package com.kaon.music

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.kaon.music.core.kernel.impl.KaonKernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.permission.PermissionManager
import com.kaon.music.ui.KaonApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val kernel = KaonKernel(applicationContext)

        val permissionManager = kernel.get<PermissionManager>()
        if (!permissionManager.hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
            permissionManager.requestPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
        }

        lifecycleScope.launch {
            kernel.start()
        }

        setContent {
            KaonApp(kernel)
        }
    }
}
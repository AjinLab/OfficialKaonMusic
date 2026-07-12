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

        val kernel = (application as KaonApplication).kernel

        val permissionManager = kernel.get<PermissionManager>()
        val permissionsToRequest = mutableListOf<String>()
        if (!permissionManager.hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (!permissionManager.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
        }

        lifecycleScope.launch {
            kernel.start()
        }

        setContent {
            KaonApp(kernel)
        }
    }

    override fun onStart() {
        super.onStart()
        val kernel = (application as KaonApplication).kernel
        if (kernel.contains(com.kaon.music.core.metrics.JankStatsMonitor::class)) {
            kernel.get<com.kaon.music.core.metrics.JankStatsMonitor>().startTracking(this)
        }
    }

    override fun onStop() {
        super.onStop()
        val kernel = (application as KaonApplication).kernel
        if (kernel.contains(com.kaon.music.core.metrics.JankStatsMonitor::class)) {
            kernel.get<com.kaon.music.core.metrics.JankStatsMonitor>().stopTracking()
        }
    }
}
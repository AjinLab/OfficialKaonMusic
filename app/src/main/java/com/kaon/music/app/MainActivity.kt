package com.kaon.music.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kaon.music.core.playback.KaonPlaybackService
import com.kaon.music.ui.plugin.UiControllers

/**
 * Truly minimal activity host delegating UI rendering to the active [com.kaon.music.ui.plugin.KaonUiPlugin].
 *
 * Implements the thin host architecture pattern (ARCHITECTURE.md §3):
 * - Bootstraps and acquires the active UI plugin from the kernel
 * - Completely decoupled from presentation ViewModels and feature code (zero feature.* imports)
 * - Passes capability controllers into the active UI plugin
 * - Routes platform notification intents and runtime permissions directly through capability controllers
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        val container = (application as KaonApplication).container
        container.libraryController.setPermissionState(audioGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as KaonApplication
        val container = app.container
        val activeUiPlugin = container.getActiveUiPlugin()

        setContent {
            activeUiPlugin.Content(
                controllers = UiControllers(
                    playerController = container.playerController,
                    libraryController = container.libraryController,
                    settingsController = container.settingsController,
                    userMessageBus = container.userMessageBus,
                    onRequestPermission = ::requestRequiredPermissions
                )
            )
        }

        handleNotificationIntent(intent)
        requestRequiredPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(KaonPlaybackService.EXTRA_EXPAND_PLAYER, false) == true) {
            val container = (application as KaonApplication).container
            container.playerController.requestExpandPlayer()
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            val container = (application as KaonApplication).container
            container.libraryController.setPermissionState(true)
        }
    }
}

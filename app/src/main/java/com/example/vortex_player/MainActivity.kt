package com.example.vortex_player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import com.example.vortex_player.ui.PlayerActions
import com.example.vortex_player.ui.PlayerRoot

class MainActivity : ComponentActivity() {

    private lateinit var controller: PlayerController
    private val prefs by lazy { getSharedPreferences("vortex", MODE_PRIVATE) }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) controller.onFolderChosen(uri)
        }

    private val pickCoverFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) controller.onCoverFolderChosen(uri)
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) controller.updateNotification()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Sem a faixa escura que o Android põe atrás dos botões de navegação.
        window.isNavigationBarContrastEnforced = false
        requestHighestRefreshRate()
        volumeControlStream = AudioManager.STREAM_MUSIC

        controller = PlayerController.get(this)
        controller.onSongStarted = ::askNotificationPermissionOnce
        val actions = PlayerActions(
            pickFolder = { pickFolder.launch(null) },
            pickCoverFolder = { pickCoverFolder.launch(null) },
        )
        setContent { PlayerRoot(controller, actions) }
    }

    override fun onResume() {
        super.onResume()
        controller.refreshVolume()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            window.decorView.post { controller.refreshVolume() }
        }
        return handled
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.onSongStarted = null
        // Leaving the app (back/close) stops the music, as before; a screen rotation does not
        if (isFinishing) controller.release()
    }

    /** Android 13+ hides notifications until allowed; ask once, on the first song played. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)) return
        prefs.edit { putBoolean(KEY_NOTIFICATION_ASKED, true) }
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Pede a maior taxa de atualização da tela (ex.: 90 Hz ou 120 Hz) na resolução atual.
     * Sem isso, alguns aparelhos deixam o app a 60 Hz.
     */
    private fun requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    private companion object {
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
    }
}

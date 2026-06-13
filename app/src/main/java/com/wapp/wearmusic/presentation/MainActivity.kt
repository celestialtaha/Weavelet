package com.wapp.wearmusic.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material3.AppScaffold
import com.wapp.wearmusic.R
import com.wapp.wearmusic.complication.MusicComplicationProvider
import com.wapp.wearmusic.presentation.screens.MusicPlayerApp
import com.wapp.wearmusic.presentation.theme.WearMusicTheme
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_DESTINATION = "com.wapp.wearmusic.extra.OPEN_DESTINATION"
        const val EXTRA_TILE_ACTION = "com.wapp.wearmusic.extra.TILE_ACTION"
        const val DESTINATION_PLAYER = "player"
        const val DESTINATION_LIBRARY = "library"
        const val TILE_ACTION_TOGGLE_PLAYBACK = "toggle_playback"
    }

    private val viewModel: MusicPlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private var contentInitialized = false
    private var openPlayerRequestCount by mutableIntStateOf(0)
    private var openLibraryRequestCount by mutableIntStateOf(0)
    private var togglePlaybackRequestCount by mutableIntStateOf(0)

    /** Single-permission launcher keeps the callback minimal. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permission granted - proceed with app initialization
            initializeAppIfNeeded()
        } else {
            // Do not auto-loop permission prompts; keep app usable and inform user.
            toast(R.string.need_storage_permission)
            initializeAppIfNeeded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        initialiseComplicationSwitch()
        handleLaunchIntent(intent)
        checkOrRequestPermission()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (hasMediaReadPermission()) {
            viewModel.onAppForeground()
        }
    }

    /** Reads the saved flag once and pushes it to the complication provider. */
    private fun initialiseComplicationSwitch() {
        val prefs = getSharedPreferences("music_player_settings", MODE_PRIVATE)
        MusicComplicationProvider.enabled.value =
            prefs.getBoolean("enable_complication", /* default = */ true)
    }

    /** Handles both ≥34 and <34 storage-read permissions. */
    private fun checkOrRequestPermission() {
        when {
            hasMediaReadPermission() -> initializeAppIfNeeded()
            else -> permissionLauncher.launch(getMediaReadPermission())
        }
    }

    private fun initializeAppIfNeeded() {
        if (contentInitialized) return
        contentInitialized = true
        setContent {
            WearMusicApp(
                openPlayerRequestCount = openPlayerRequestCount,
                openLibraryRequestCount = openLibraryRequestCount,
                togglePlaybackRequestCount = togglePlaybackRequestCount
            )
        }
    }

    private fun handleLaunchIntent(intent: android.content.Intent?) {
        when (intent?.getStringExtra(EXTRA_OPEN_DESTINATION)) {
            DESTINATION_PLAYER -> openPlayerRequestCount += 1
            DESTINATION_LIBRARY -> openLibraryRequestCount += 1
        }
        if (intent?.getStringExtra(EXTRA_TILE_ACTION) == TILE_ACTION_TOGGLE_PLAYBACK) {
            togglePlaybackRequestCount += 1
        }
    }

    private fun getMediaReadPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasMediaReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, getMediaReadPermission()) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Tiny util to avoid Toast boilerplate. */
    private fun Context.toast(messageRes: Int) =
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}

@Composable
fun WearMusicApp(
    openPlayerRequestCount: Int = 0,
    openLibraryRequestCount: Int = 0,
    togglePlaybackRequestCount: Int = 0
) {
    WearMusicTheme {
        AppScaffold {
            MusicPlayerApp(
                openPlayerRequestCount = openPlayerRequestCount,
                openLibraryRequestCount = openLibraryRequestCount,
                togglePlaybackRequestCount = togglePlaybackRequestCount
            )
        }
    }
}

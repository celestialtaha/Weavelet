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
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material3.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.wapp.wearmusic.R
import com.wapp.wearmusic.complication.MusicComplicationProvider
import com.wapp.wearmusic.presentation.screens.MusicPlayerApp
import com.wapp.wearmusic.presentation.theme.WearMusicTheme
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MusicPlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    /** Single-permission launcher keeps the callback minimal. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.loadTracks()
        } else {
            showPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        initialiseComplicationSwitch()
        checkOrRequestPermission()

        setContent {
            WearMusicApp(viewModel, settingsViewModel)
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
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED          -> viewModel.loadTracks()

            shouldShowRequestPermissionRationale(permission)  -> showPermissionRationale()

            else                                             -> permissionLauncher.launch(permission)
        }
    }

    private fun showPermissionRationale() {
        toast(R.string.need_storage_permission)
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        permissionLauncher.launch(permission)
    }

    /** Tiny util to avoid Toast boilerplate. */
    private fun Context.toast(messageRes: Int) =
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}

@Composable
fun WearMusicApp(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    WearMusicTheme {
        TimeText()
        MusicPlayerApp(viewModel, settingsViewModel)
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearMusicTheme { TimeText() }
}

package com.wapp.wearmusic.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

/**
 * Home screen that serves as the entry point to the app
 * Shows buttons for Music Library, Settings, and About
 */
@Composable
fun HomeScreen(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App Title
            item {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.title1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                )
            }
            
            // Music Library Button
            item {
                Button(
                    onClick = onLibraryClick,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                ) {
                    Text(stringResource(R.string.music_library))
                }
            }
            
            // Settings Button
            item {
                Button(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primaryVariant
                    )
                ) {
                    Text(stringResource(R.string.settings))
                }
            }
            
            // About Button
            item {
                Button(
                    onClick = onAboutClick,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.secondary
                    )
                ) {
                    Text(stringResource(R.string.about))
                }
            }
        }
    }
}

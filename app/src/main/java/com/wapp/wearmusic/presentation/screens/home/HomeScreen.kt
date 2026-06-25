package com.wapp.wearmusic.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wapp.wearmusic.R

/**
 * Home screen that serves as the entry point to the app
 * Shows buttons for Music Library, Settings, and About
 */
@Composable
fun HomeScreen(
    showNowPlaying: Boolean,
    onNowPlayingClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollState)
    val configuration = LocalConfiguration.current
    val minScreenDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val compact = minScreenDp <= 220
    val horizontalPadding = if (compact) 8.dp else 12.dp
    val actionWidth = if (compact) 0.9f else 0.84f

    ScreenScaffold(
        scrollState = scrollState
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .requestFocusOnHierarchyActive()
                .rotaryScrollable(
                    behavior = rotaryBehavior,
                    focusRequester = focusRequester
                )
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            ListHeader(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }

            if (showNowPlaying) {
                Button(
                    onClick = onNowPlayingClick,
                    modifier = Modifier.fillMaxWidth(actionWidth),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(stringResource(R.string.now_playing))
                }
            }

            HomeActionButton(
                label = stringResource(R.string.music_library),
                onClick = onLibraryClick,
                widthFraction = actionWidth,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            HomeActionButton(
                label = stringResource(R.string.settings),
                onClick = onSettingsClick,
                widthFraction = actionWidth,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            HomeActionButton(
                label = stringResource(R.string.about),
                onClick = onAboutClick,
                widthFraction = actionWidth
            )
        }
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    onClick: () -> Unit,
    widthFraction: Float,
    colors: androidx.wear.compose.material3.ButtonColors = ButtonDefaults.buttonColors()
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .padding(vertical = 1.dp),
        colors = colors
    ) {
        Text(label)
    }
}

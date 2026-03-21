package com.wapp.wearmusic.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import com.wapp.wearmusic.presentation.screens.library.LibraryScreen
import com.wapp.wearmusic.presentation.screens.player.PlayerScreen
import com.wapp.wearmusic.presentation.screens.home.HomeScreen
import com.wapp.wearmusic.presentation.screens.home.AboutScreen
import com.wapp.wearmusic.presentation.screens.settings.SettingsScreen
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerUiState
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

/**
 * Root composable that hosts Library ⇄ Player ⇄ Settings
 */
// Define sealed class OUTSIDE the composable function at the top level
sealed class Screen {
    object Home : Screen()
    object Library : Screen()
    object Player : Screen()
    object Settings : Screen()
    object About : Screen()
}

@Composable
fun MusicPlayerApp() {
    val viewModel: MusicPlayerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    // Single source of truth for navigation
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val swipeState = rememberSwipeToDismissBoxState()

    // Trigger initial track load once.
    LaunchedEffect(Unit) {
        viewModel.loadTracks()
    }

    // Handle swipe dismissals
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
            swipeState.snapTo(SwipeToDismissValue.Default)
            currentScreen = when (currentScreen) {
                Screen.Settings -> Screen.Home
                Screen.About -> Screen.Home
                Screen.Player -> Screen.Library
                Screen.Library -> Screen.Home
                else -> Screen.Home
            }
        }
    }

    BasicSwipeToDismissBox(
        state = swipeState,
        userSwipeEnabled = currentScreen != Screen.Home,
        backgroundKey = "Background",
        contentKey = "Foreground"
    ) { isBackground ->
        if (isBackground) {
            // Background shows previous screen
            when (currentScreen) {
                Screen.Library -> HomeScreen(
                    onLibraryClick = { /* Handled in foreground */ },
                    onSettingsClick = { /* Handled in foreground */ },
                    onAboutClick = { /* Handled in foreground */ }
                )
                Screen.Player -> LibraryContent(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel
                ) { index ->
                    viewModel.playTracks(index)
                    currentScreen = Screen.Player
                }
                Screen.Settings, Screen.About -> HomeScreen(
                    onLibraryClick = { /* Handled in foreground */ },
                    onSettingsClick = { /* Handled in foreground */ },
                    onAboutClick = { /* Handled in foreground */ }
                )
                else -> Box(Modifier.fillMaxSize()) // Empty background for home
            }
        } else {
            // Foreground shows current screen
            when (currentScreen) {
                Screen.Home -> HomeScreen(
                    onLibraryClick = { currentScreen = Screen.Library },
                    onSettingsClick = { currentScreen = Screen.Settings },
                    onAboutClick = { currentScreen = Screen.About }
                )
                Screen.Library -> LibraryContent(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel
                ) { index ->
                    viewModel.playTracks(index)
                    currentScreen = Screen.Player
                }
                Screen.Player -> PlayerScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel
                )
                Screen.Settings -> SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onBackClick = { currentScreen = Screen.Home }
                )
                Screen.About -> AboutScreen(
                    onBackClick = { currentScreen = Screen.Home }
                )
            }
        }
    }
}

@Composable
private fun LibraryContent(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onTrackClick: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {
        MusicPlayerUiState.Loading -> LoadingScreen()
        MusicPlayerUiState.Empty -> EmptyLibraryScreen()
        is MusicPlayerUiState.Error -> ErrorScreen((uiState as MusicPlayerUiState.Error).message)
        MusicPlayerUiState.Success -> LibraryScreen(
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            onTrackClick = onTrackClick
        )
    }
}

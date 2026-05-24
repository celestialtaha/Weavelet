package com.wapp.wearmusic.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import com.wapp.wearmusic.core.data.model.Track
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
fun MusicPlayerApp(openPlayerRequestCount: Int = 0) {
    val viewModel: MusicPlayerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    // Single source of truth for navigation
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var playerBackTarget by remember { mutableStateOf<Screen>(Screen.Home) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val swipeState = rememberSwipeToDismissBoxState()
    val libraryArtistDetailActive by viewModel.libraryArtistDetailActive.collectAsStateWithLifecycle()
    val hasNowPlaying = playbackState.currentTrack != null

    // Trigger initial track load once.
    LaunchedEffect(Unit) {
        viewModel.loadTracks()
    }

    LaunchedEffect(openPlayerRequestCount) {
        if (openPlayerRequestCount > 0) {
            playerBackTarget = Screen.Home
            currentScreen = Screen.Player
        }
    }

    // Handle swipe dismissals
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
            swipeState.snapTo(SwipeToDismissValue.Default)
            currentScreen = when (currentScreen) {
                Screen.Settings -> Screen.Home
                Screen.About -> Screen.Home
                Screen.Player -> playerBackTarget
                Screen.Library -> {
                    if (libraryArtistDetailActive) {
                        viewModel.requestLibraryInternalBack()
                        Screen.Library
                    } else {
                        Screen.Home
                    }
                }
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
                    showNowPlaying = hasNowPlaying,
                    onNowPlayingClick = { /* Handled in foreground */ },
                    onLibraryClick = { /* Handled in foreground */ },
                    onSettingsClick = { /* Handled in foreground */ },
                    onAboutClick = { /* Handled in foreground */ }
                )
                Screen.Player -> {
                    if (playerBackTarget == Screen.Home) {
                        HomeScreen(
                            showNowPlaying = hasNowPlaying,
                            onNowPlayingClick = { /* Handled in foreground */ },
                            onLibraryClick = { /* Handled in foreground */ },
                            onSettingsClick = { /* Handled in foreground */ },
                            onAboutClick = { /* Handled in foreground */ }
                        )
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
                Screen.Settings, Screen.About -> HomeScreen(
                    showNowPlaying = hasNowPlaying,
                    onNowPlayingClick = { /* Handled in foreground */ },
                    onLibraryClick = { /* Handled in foreground */ },
                    onSettingsClick = { /* Handled in foreground */ },
                    onAboutClick = { /* Handled in foreground */ }
                )
                else -> Box(Modifier.fillMaxSize()) // Empty background for home
            }
        } else {
            saveableStateHolder.SaveableStateProvider(screenKey(currentScreen)) {
                // Foreground shows current screen
                when (currentScreen) {
                    Screen.Home -> HomeScreen(
                        showNowPlaying = hasNowPlaying,
                        onNowPlayingClick = {
                            playerBackTarget = Screen.Home
                            currentScreen = Screen.Player
                        },
                        onLibraryClick = { currentScreen = Screen.Library },
                        onSettingsClick = { currentScreen = Screen.Settings },
                        onAboutClick = { currentScreen = Screen.About }
                    )
                    Screen.Library -> LibraryContent(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        onTrackClick = { index ->
                            viewModel.playTracks(index)
                            playerBackTarget = Screen.Library
                            currentScreen = Screen.Player
                        },
                        onPlayArtistTrack = { tracks, index ->
                            viewModel.playTrackList(tracks, index)
                            playerBackTarget = Screen.Library
                            currentScreen = Screen.Player
                        },
                        onBackClick = { currentScreen = Screen.Home }
                    )
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
}

private fun screenKey(screen: Screen): String {
    return when (screen) {
        Screen.Home -> "home"
        Screen.Library -> "library"
        Screen.Player -> "player"
        Screen.Settings -> "settings"
        Screen.About -> "about"
    }
}

@Composable
private fun LibraryContent(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onTrackClick: (Int) -> Unit,
    onPlayArtistTrack: (List<Track>, Int) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryRefreshing by viewModel.libraryRefreshing.collectAsStateWithLifecycle()

    when (uiState) {
        MusicPlayerUiState.Loading -> LoadingScreen()
        MusicPlayerUiState.Empty -> EmptyLibraryScreen(
            onBackClick = onBackClick,
            onRefreshClick = { viewModel.refreshLibrary() },
            isRefreshing = libraryRefreshing
        )
        is MusicPlayerUiState.Error -> ErrorScreen(
            message = (uiState as MusicPlayerUiState.Error).message,
            onBackClick = onBackClick,
            onRetryClick = { viewModel.refreshLibrary() },
            isRetrying = libraryRefreshing
        )
        MusicPlayerUiState.Success -> LibraryScreen(
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            onTrackClick = onTrackClick,
            onPlayArtistTrack = onPlayArtistTrack,
            onBackClick = onBackClick
        )
    }
}

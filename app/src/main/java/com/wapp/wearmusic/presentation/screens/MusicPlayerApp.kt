package com.wapp.wearmusic.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
enum class Screen {
    HOME,
    LIBRARY,
    PLAYER,
    SETTINGS,
    ABOUT
}

@Composable
fun MusicPlayerApp(
    openPlayerRequestCount: Int = 0,
    openLibraryRequestCount: Int = 0,
    togglePlaybackRequestCount: Int = 0
) {
    val viewModel: MusicPlayerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    // Single source of truth for navigation
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var playerBackTarget by rememberSaveable { mutableStateOf(Screen.HOME) }
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
            playerBackTarget = Screen.HOME
            currentScreen = Screen.PLAYER
        }
    }

    LaunchedEffect(openLibraryRequestCount) {
        if (openLibraryRequestCount > 0) {
            currentScreen = Screen.LIBRARY
        }
    }

    LaunchedEffect(togglePlaybackRequestCount) {
        if (togglePlaybackRequestCount > 0) {
            playerBackTarget = Screen.HOME
            currentScreen = Screen.PLAYER
            viewModel.togglePlayPause()
        }
    }

    fun navigateBack() {
        currentScreen = when (currentScreen) {
            Screen.SETTINGS -> Screen.HOME
            Screen.ABOUT -> Screen.HOME
            Screen.PLAYER -> playerBackTarget
            Screen.LIBRARY -> {
                if (libraryArtistDetailActive) {
                    viewModel.requestLibraryInternalBack()
                    Screen.LIBRARY
                } else {
                    Screen.HOME
                }
            }
            Screen.HOME -> Screen.HOME
        }
    }

    BackHandler(enabled = currentScreen != Screen.HOME) {
        navigateBack()
    }

    // Handle swipe dismissals
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
            swipeState.snapTo(SwipeToDismissValue.Default)
            navigateBack()
        }
    }

    BasicSwipeToDismissBox(
        state = swipeState,
        userSwipeEnabled = currentScreen != Screen.HOME,
        backgroundKey = "Background",
        contentKey = "Foreground"
    ) { isBackground ->
        if (isBackground) {
            // Background shows previous screen
            when (currentScreen) {
                Screen.LIBRARY -> HomeScreen(
                    showNowPlaying = hasNowPlaying,
                    onNowPlayingClick = { /* Handled in foreground */ },
                    onLibraryClick = { /* Handled in foreground */ },
                    onSettingsClick = { /* Handled in foreground */ },
                    onAboutClick = { /* Handled in foreground */ }
                )
                Screen.PLAYER -> {
                    if (playerBackTarget == Screen.HOME) {
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
                Screen.SETTINGS, Screen.ABOUT -> HomeScreen(
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
                    Screen.HOME -> HomeScreen(
                        showNowPlaying = hasNowPlaying,
                        onNowPlayingClick = {
                            playerBackTarget = Screen.HOME
                            currentScreen = Screen.PLAYER
                        },
                        onLibraryClick = { currentScreen = Screen.LIBRARY },
                        onSettingsClick = { currentScreen = Screen.SETTINGS },
                        onAboutClick = { currentScreen = Screen.ABOUT }
                    )
                    Screen.LIBRARY -> LibraryContent(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        onTrackClick = { index ->
                            viewModel.playTracks(index)
                            playerBackTarget = Screen.LIBRARY
                            currentScreen = Screen.PLAYER
                        },
                        onPlayArtistTrack = { tracks, index ->
                            viewModel.playTrackList(tracks, index)
                            playerBackTarget = Screen.LIBRARY
                            currentScreen = Screen.PLAYER
                        },
                        onBackClick = { currentScreen = Screen.HOME }
                    )
                    Screen.PLAYER -> PlayerScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onBackClick = { currentScreen = Screen.HOME }
                    )
                    Screen.ABOUT -> AboutScreen(
                        onBackClick = { currentScreen = Screen.HOME }
                    )
                }
            }
        }
    }
}

private fun screenKey(screen: Screen): String {
    return when (screen) {
        Screen.HOME -> "home"
        Screen.LIBRARY -> "library"
        Screen.PLAYER -> "player"
        Screen.SETTINGS -> "settings"
        Screen.ABOUT -> "about"
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

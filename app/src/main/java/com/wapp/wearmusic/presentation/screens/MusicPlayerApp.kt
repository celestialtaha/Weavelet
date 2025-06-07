package com.wapp.wearmusic.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
@Composable
fun MusicPlayerApp() {
    val viewModel: MusicPlayerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    // Collect UI state safely
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val currentTrack = playbackState.currentTrack

    var showHome by remember { mutableStateOf(true) }
    var showLibrary by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }

    // Track if we've triggered initial load
    var initialLoadTriggered by remember { mutableStateOf(false) }

    // Trigger initial track load
    LaunchedEffect(Unit) {
        if (!initialLoadTriggered) {
            viewModel.loadTracks()
            initialLoadTriggered = true
        }
    }

    val swipeState = rememberSwipeToDismissBoxState()

    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
            swipeState.snapTo(SwipeToDismissValue.Default)
            when {
                showSettings -> showSettings = false
                showAbout -> {
                    showAbout = false
                    showHome = true
                }
                showPlayer -> {
                    showPlayer = false
                    showLibrary = true
                }
                showLibrary -> {
                    showLibrary = false
                    showHome = true
                }
                else -> showHome = true
            }
        }
    }

    BasicSwipeToDismissBox(
        state = swipeState,
        userSwipeEnabled = showSettings || showAbout || showLibrary || showPlayer,
        backgroundKey = "Background",
        contentKey = "Foreground"
    ) { isBackground ->
        if (isBackground) {
            Box(Modifier.fillMaxSize()) {
                when {
                    showSettings && showLibrary -> {
                        LibraryScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            onTrackClick = { idx ->
                                viewModel.playTracks(idx)
                                showLibrary = false
                                showPlayer = true
                            }
                        )
                    }
                    showSettings && showPlayer -> {
                        PlayerScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                    showAbout -> {
                        HomeScreen(
                            onLibraryClick = { /* no-op */ },
                            onSettingsClick = { /* no-op */ },
                            onAboutClick = { /* no-op */ }
                        )
                    }
                    showLibrary -> {
                        HomeScreen(
                            onLibraryClick = { /* no-op */ },
                            onSettingsClick = { /* no-op */ },
                            onAboutClick = { /* no-op */ }
                        )
                    }
                    showPlayer -> {
                        LibraryScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            onTrackClick = { /* no-op */ }
                        )
                    }
                }
            }
        } else {
            when {
                showSettings -> {
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onBackClick = { showSettings = false }
                    )
                }
                showAbout -> {
                    AboutScreen(
                        onBackClick = {
                            showAbout = false
                            showHome = true
                        }
                    )
                }
                showPlayer -> {
                    PlayerScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
                showHome -> {
                    HomeScreen(
                        onLibraryClick = {
                            showHome = false
                            showLibrary = true
                        },
                        onSettingsClick = { showSettings = true },
                        onAboutClick = {
                            showHome = false
                            showAbout = true
                        }
                    )
                }
                showLibrary || (uiState == MusicPlayerUiState.Success && currentTrack == null) -> {
                    when (uiState) {
                        MusicPlayerUiState.Loading -> LoadingScreen()
                        MusicPlayerUiState.Empty -> EmptyLibraryScreen()
                        is MusicPlayerUiState.Error -> ErrorScreen((uiState as MusicPlayerUiState.Error).message)
                        MusicPlayerUiState.Success -> {
                            LibraryScreen(
                                viewModel = viewModel,
                                settingsViewModel = settingsViewModel,
                                onTrackClick = { idx ->
                                    viewModel.playTracks(idx)
                                    showLibrary = false
                                    showPlayer = true
                                }
                            )
                        }
                    }
                }
                else -> showHome = true
            }
        }
    }
}
package com.wapp.wearmusic.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.*

import com.wapp.wearmusic.presentation.screens.library.LibraryScreen
import com.wapp.wearmusic.presentation.screens.player.PlayerScreen
import com.wapp.wearmusic.presentation.screens.home.HomeScreen
import com.wapp.wearmusic.presentation.screens.home.AboutScreen
import com.wapp.wearmusic.presentation.screens.settings.SettingsScreen
import com.wapp.wearmusic.presentation.viewmodel.*

/**
 * Root composable that hosts Library ⇄ Player ⇄ Settings
 * using the *new* BasicSwipeToDismissBox API (Wear Compose ≥ 1.5.0-beta03).
 */
@Composable
fun MusicPlayerApp(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    val uiState      by viewModel.uiState.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    var showHome     by remember { mutableStateOf(true) }
    var showLibrary  by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout    by remember { mutableStateOf(false) }
    var showPlayer   by remember { mutableStateOf(false) }

    val swipeState = rememberSwipeToDismissBoxState()

    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
            swipeState.snapTo(SwipeToDismissValue.Default)
            when {
                showSettings -> showSettings = false
                showAbout    -> {
                    showAbout = false
                    showHome  = true
                }
                showPlayer   -> {
                    // IMPORTANT: do NOT clearCurrentTrack() here
                    showPlayer  = false
                    showLibrary = true
                }
                showLibrary  -> {
                    showLibrary = false
                    showHome    = true
                }
                else -> showHome = true
            }
        }
    }

    BasicSwipeToDismissBox(
        state            = swipeState,
        userSwipeEnabled = showSettings || showAbout || showLibrary || showPlayer,
        backgroundKey    = "Background",
        contentKey       = "Foreground"
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
                                showPlayer  = true
                            },
                            //onSettingsClick = { /* no-op in BG */ }
                        )
                    }
                    showSettings && showPlayer -> {
                        PlayerScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
//                            onBackClick = { /* no-op in BG */ },
//                            onSettingsClick = { /* no-op in BG */ }
                        )
                    }
                    showAbout -> {
                        HomeScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            onLibraryClick  = { /* no-op in BG */ },
                            onSettingsClick = { /* no-op in BG */ },
                            onAboutClick    = { /* no-op in BG */ }
                        )
                    }
                    showLibrary -> {
                        HomeScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            onLibraryClick  = { /* no-op in BG */ },
                            onSettingsClick = { /* no-op in BG */ },
                            onAboutClick    = { /* no-op in BG */ }
                        )
                    }
                    showPlayer -> {
                        LibraryScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            onTrackClick = { /* no-op in BG */ },
                            //onSettingsClick = { /* no-op in BG */ }
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
                            showHome  = true
                        }
                    )
                }
                showPlayer -> {
                    PlayerScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
//                        onBackClick = {
//                            // tapping the top-left arrow expressly stops playback:
//                            //viewModel.clearCurrentTrack()
//                            showPlayer  = false
//                            showLibrary = true
//                        },
//                        onSettingsClick = { showSettings = true }
                    )
                }
                showHome -> {
                    HomeScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        onLibraryClick  = {
                            showHome    = false
                            showLibrary = true
                        },
                        onSettingsClick = { showSettings = true },
                        onAboutClick   = {
                            showHome  = false
                            showAbout = true
                        }
                    )
                }
                showLibrary || (uiState == MusicPlayerUiState.Success && currentTrack == null) -> {
                    when (uiState) {
                        MusicPlayerUiState.Loading -> LoadingScreen()
                        MusicPlayerUiState.Empty   -> EmptyLibraryScreen()
                        is MusicPlayerUiState.Error -> ErrorScreen((uiState as MusicPlayerUiState.Error).message)
                        MusicPlayerUiState.Success -> {
                            LibraryScreen(
                                viewModel = viewModel,
                                settingsViewModel = settingsViewModel,
                                onTrackClick = { idx ->
                                    viewModel.playTracks(idx)
                                    showLibrary = false
                                    showPlayer  = true
                                },
                                //onSettingsClick = { showSettings = true }
                            )
                        }
                    }
                }
                else -> showHome = true
            }
        }
    }
}

package com.wapp.wearmusic.presentation.screens.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wapp.wearmusic.R
import com.wapp.wearmusic.data.model.RepeatMode
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    // 1) State from ViewModel
    val track by viewModel.currentTrack.collectAsState()
    val playing by viewModel.isPlaying.collectAsState()
    val posMs by viewModel.currentPosition.collectAsState()
    val durMs by viewModel.duration.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    // 2) Preference: showAlbumArt
    val settings by settingsViewModel.settings.collectAsState()
    val showArt = settings.showAlbumArt

    // 3) Compute progress fraction
    val progress by remember(posMs, durMs) {
        mutableFloatStateOf(if (durMs > 0) posMs / durMs.toFloat() else 0f)
    }

    // 4) AudioManager for volume control
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // 5) Create and remember a FocusRequester
    val focusRequester = remember { FocusRequester() }

    // 6) When this screen appears, request focus so rotary events will be received
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 7) Make this Box focusable and request focus via focusRequester
            .focusRequester(focusRequester)
            .focusable()
            // 8) Now attach the rotary handler for volume control with haptic feedback
            .onRotaryScrollEvent { event ->
                if (event.verticalScrollPixels > 0f) {
                    viewModel.adjustVolume(true)
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                } else {
                    viewModel.adjustVolume(false)
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                true
            }
            .safeDrawingPadding()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (track == null) {
            Text(
                text = stringResource(R.string.no_track_selected),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
        } else {
            // Create a local copy that can be smart cast
            val currentTrack = track
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Album Art (if enabled)
                if (showArt) {
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentTrack?.albumArtUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentTrack.albumArtUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "No Album Art",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        
                        // Progress indicator overlay
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 3.dp,
                            indicatorColor = MaterialTheme.colors.primary
                        )
                    }
                }

                // Track Info
                Text(
                    text = currentTrack?.title ?: "",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )

                // Mode indicators row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle button
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { viewModel.toggleShuffle() },
                        tint = if (shuffleMode) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )

                    // Repeat button
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.OFF -> Icons.Default.Repeat
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                        },
                        contentDescription = "Repeat: ${repeatMode.name}",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { viewModel.toggleRepeatMode() },
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Main Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { viewModel.skipPrevious() }
                            .background(
                                MaterialTheme.colors.surface.copy(alpha = 0.3f),
                                CircleShape
                            )
                            .padding(6.dp)
                    )

                    // Play/Pause
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { viewModel.togglePlayPause() }
                            .background(
                                MaterialTheme.colors.primary,
                                CircleShape
                            )
                            .padding(8.dp),
                        tint = MaterialTheme.colors.onPrimary
                    )

                    // Next
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { viewModel.skipNext() }
                            .background(
                                MaterialTheme.colors.surface.copy(alpha = 0.3f),
                                CircleShape
                            )
                            .padding(6.dp)
                    )
                }

                // Time info
                if (durMs > 0) {
                    Text(
                        text = "${formatTime(posMs)} / ${formatTime(durMs)}",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Format milliseconds to MM:SS format
 */
private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}
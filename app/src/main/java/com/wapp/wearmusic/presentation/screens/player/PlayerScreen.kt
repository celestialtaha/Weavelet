package com.wapp.wearmusic.presentation.screens.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
            // 8) Now attach the rotary handler
            .onRotaryScrollEvent { event ->
                if (event.verticalScrollPixels > 0f) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                } else {
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
            // Placeholder if no track
            Text(
                stringResource(R.string.nothing_playing),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            return@Box
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Row with Previous, AlbumArt+PlayPause+Progress, Next
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Previous button (36dp)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.surface)
                        .clickable { viewModel.skipPrevious() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colors.onSurface
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Album art with progress ring and play/pause (container 108dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(108.dp)
                ) {
                    // Circular progress around
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                        indicatorColor = MaterialTheme.colors.primary
                    )

                    // Album art or placeholder (88dp)
                    if (showArt) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(track!!.albumArtUri)
                                .crossfade(true)
                                .placeholder(R.drawable.ic_music_note)
                                .error(R.drawable.ic_music_note)
                                .build(),
                            contentDescription = stringResource(R.string.album_art_for, track!!.title),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colors.surface)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Play/Pause button centered (44dp) with slight transparency
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.4f))
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playing)
                                androidx.compose.material.icons.Icons.Default.Pause
                            else
                                androidx.compose.material.icons.Icons.Default.PlayArrow,
                            contentDescription = if (playing)
                                stringResource(R.string.pause)
                            else
                                stringResource(R.string.play),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colors.onPrimary
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Next button (36dp)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.surface)
                        .clickable { viewModel.skipNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colors.onSurface
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Track title and artist at bottom
            Text(
                text = track!!.title,
                style = MaterialTheme.typography.title3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track!!.artist,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
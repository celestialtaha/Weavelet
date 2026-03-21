/*
 * PlayerScreen.kt  –  Wear OS Music Player
 * Tested with:
 *   androidx.wear.compose:compose-material:1.5.0-beta03
 *   androidx.compose.ui:ui:1.8.0
 *   androidx.lifecycle:lifecycle-runtime-compose:2.8.0
 */
package com.wapp.wearmusic.presentation.screens.player

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wapp.wearmusic.R
import com.wapp.wearmusic.core.data.model.RepeatMode
import com.wapp.wearmusic.core.data.model.Track
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel
import kotlin.math.min

/* -------------------------------------------------------------------------- */
/*                                SCREEN BODY                                 */
/* -------------------------------------------------------------------------- */

private enum class TimeMode { ELAPSED, REMAINING }

@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    /* 1 ─── Collect state (lifecycle-aware) */
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val track = playbackState.currentTrack
    val playing = playbackState.isPlaying
    val posMs = playbackState.position
    val durMs = playbackState.duration
    val shuffleOn  by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        viewModel.setProgressTrackingEnabled(true)
        onDispose {
            viewModel.setProgressTrackingEnabled(false)
        }
    }


    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val showArt = settings.showAlbumArt
    val haptic = LocalHapticFeedback.current

    val configuration = LocalConfiguration.current
    val minScreenDp = min(configuration.screenWidthDp, configuration.screenHeightDp)
    val isCompact = minScreenDp <= 240
    val screenPadding = if (isCompact) 1.dp else 5.dp
    val sectionSpacing = if (isCompact) 2.dp else 5.dp
    val transportSpacing = if (isCompact) 2.dp else 4.dp
    val modeButtonSize = if (isCompact) 36.dp else 42.dp
    val modeIconSize = if (isCompact) 15.dp else 17.dp

    var timeMode by rememberSaveable { mutableStateOf(TimeMode.ELAPSED) }
    val remainingMs = (durMs - posMs).coerceAtLeast(0L)
    val centerTimeText = when {
        durMs <= 0L -> null
        timeMode == TimeMode.ELAPSED -> formatTime(posMs)
        else -> "-${formatTime(remainingMs)}"
    }

    val progress by remember(posMs, durMs) {
        derivedStateOf {
            if (durMs > 0) posMs / durMs.toFloat() else 0f
        }
    }

    /* 3 ─── Rotary plumbing */
    val ctx       = LocalContext.current
    val audio     = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val focusReq  = remember { FocusRequester() }

    /* Accumulator for smoother, one-step-per-detent behaviour */
    var accumPx by remember { mutableFloatStateOf(0f) }
    val PIXELS_PER_STEP = 48f         // tweak if your hardware feels different

    val screenScrollState = rememberScrollState()
    ScreenScaffold(
        scrollState = screenScrollState,
        scrollIndicator = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusReq)
                .onPreRotaryScrollEvent { ev ->
                    accumPx += ev.verticalScrollPixels     // +ve = clockwise on most watches
                    var handled = false
                    while (accumPx >= PIXELS_PER_STEP) {
                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            /* no FLAG_SHOW_UI – avoids overlay stealing focus */ 0
                        )
                        accumPx -= PIXELS_PER_STEP
                        handled = true
                    }
                    while (accumPx <= -PIXELS_PER_STEP) {
                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            0
                        )
                        accumPx += PIXELS_PER_STEP
                        handled = true
                    }
                    if (handled) Log.d("PlayerScreen", "Volume step, accum=$accumPx")
                    handled        // consume only if we actually changed volume
                }
                .focusTarget()                     // .focusable() if <1.8
                .padding(screenPadding),
            contentAlignment = Alignment.Center
        ) {
            LaunchedEffect(Unit) { focusReq.requestFocus() }

            /* ----- Empty state ----- */
            if (track == null) {
                Text(
                    text      = stringResource(R.string.no_track_selected),
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                return@Box
            }

            PlayerBackdrop(
                track = track!!,
                progress = progress,
                showAlbumArt = showArt,
                compact = isCompact
            )

            /* ----- Main column ----- */
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(horizontal = if (isCompact) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TrackTimeChip(
                    value = centerTimeText,
                    onClick = if (durMs > 0) {
                        {
                            timeMode = if (timeMode == TimeMode.ELAPSED) {
                                TimeMode.REMAINING
                            } else {
                                TimeMode.ELAPSED
                            }
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                    } else {
                        null
                    },
                    compact = isCompact
                )
                Spacer(Modifier.height(sectionSpacing))
                TrackInfo(track = track!!, compact = isCompact)
                Spacer(Modifier.height(sectionSpacing))
                TransportControls(
                    playing  = playing,
                    onPrev   = viewModel::skipPrevious,
                    onToggle = viewModel::togglePlayPause,
                    onNext   = viewModel::skipNext,
                    hapticEnabled = hapticEnabled,
                    spacing = transportSpacing
                )
                Spacer(Modifier.height(sectionSpacing))
                ModeButtons(
                    shuffleOn   = shuffleOn,
                    repeatMode  = repeatMode,
                    toggleShuffle   = viewModel::toggleShuffle,
                    toggleRepeatMode = viewModel::toggleRepeatMode,
                    hapticEnabled = hapticEnabled,
                    buttonSize = modeButtonSize,
                    iconSize = modeIconSize
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                           SUB-COMPOSABLES (unchanged)                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun PlayerBackdrop(
    track: Track,
    progress: Float,
    showAlbumArt: Boolean,
    compact: Boolean
) {
    val ctx = LocalContext.current
    val edgePadding = if (compact) 2.dp else 4.dp
    val strokeWidth = if (compact) 4.dp else 5.dp
    val hasArt = showAlbumArt && track.albumArtUri != null

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (hasArt) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(track.albumArtUri).crossfade(true).build(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (hasArt) 0.50f else 0.30f),
                            Color.Black.copy(alpha = if (hasArt) 0.72f else 0.56f)
                        )
                    )
                )
        )

        CircularProgressIndicator(
            progress = {progress},
            strokeWidth = strokeWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(edgePadding),
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun TrackTimeChip(
    value: String?,
    onClick: (() -> Unit)?,
    compact: Boolean
) {
    if (value == null) return

    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
                shape = CircleShape
            )
            .clip(CircleShape)
            .semantics { role = Role.Button }
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun TrackInfo(track: Track, compact: Boolean) {
    Text(
        text       = track.title,
        style      = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
        textAlign  = TextAlign.Center,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis
    )
    if (!compact) {
        Text(
            text       = track.artist,
            style      = MaterialTheme.typography.bodySmall,
            textAlign  = TextAlign.Center,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeButtons(
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    toggleShuffle: () -> Unit,
    toggleRepeatMode: () -> Unit,
    hapticEnabled: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp
) {
    val haptic = LocalHapticFeedback.current

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconToggleButton(
            checked = shuffleOn,
            onCheckedChange = { checked ->
                toggleShuffle()
                if (hapticEnabled){
                    haptic.performHapticFeedback(
                        if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                    )
                }
            },
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape),        // keep it circular
        ) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier.size(iconSize)
            )
        }

        IconToggleButton(
            checked = repeatMode != RepeatMode.OFF,
            onCheckedChange = {
                val nextMode = when (repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
                toggleRepeatMode()
                if (hapticEnabled){
                    haptic.performHapticFeedback(
                        if (nextMode == RepeatMode.OFF) {
                            HapticFeedbackType.ToggleOff
                        } else {
                            HapticFeedbackType.ToggleOn
                        }
                    )
                }
            },
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape),
        ) {
            Icon(
                if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun TransportControls(
    playing: Boolean,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    hapticEnabled: Boolean,
    spacing: androidx.compose.ui.unit.Dp
) {
    val haptic = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        IconButton(onClick = {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
            }
            onPrev()
        }) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev")
        }
        FilledTonalIconButton(onClick = {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            onToggle()
        }) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play"
            )
        }
        IconButton(onClick = {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
            }
            onNext()
        }) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next")
        }
    }
}

//@Composable
//private fun ControlIcon(
//    vector: ImageVector,
//    desc: String,
//    onClick: () -> Unit
//) {
//    Icon(
//        imageVector = vector,
//        contentDescription = desc,
//        modifier = Modifier
//            .size(32.dp)
//            .clickable(onClick = onClick)
//            .background(
//                MaterialTheme.colors.surface.copy(alpha = 0.3f),
//                CircleShape
//            )
//            .padding(6.dp)
//    )
//}

/* -------------------------------------------------------------------------- */
/*                                  UTILS                                     */
/* -------------------------------------------------------------------------- */

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}

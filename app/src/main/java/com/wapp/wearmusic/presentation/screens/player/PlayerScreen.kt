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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
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
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wapp.wearmusic.R
import com.wapp.wearmusic.data.model.RepeatMode
import com.wapp.wearmusic.data.model.Track
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.map

/* -------------------------------------------------------------------------- */
/*                                SCREEN BODY                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    /* 1 ─── Collect state (lifecycle-aware) */
    val track      by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playing    by viewModel.isPlaying.collectAsStateWithLifecycle()
    val posMs      by viewModel.currentPosition.collectAsStateWithLifecycle()
    val durMs      by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleOn  by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()


    val showArt by settingsViewModel.settings
        .map { it.showAlbumArt }                    // take only the Boolean field
        .collectAsStateWithLifecycle(initialValue = true)

    /* 2 ─── Derived progress */
//    val progress by remember {
//        derivedStateOf { if (durMs > 0) posMs / durMs.toFloat() else 0f }
//    }
    val progress by remember(posMs, durMs) {
        mutableFloatStateOf( if (durMs > 0) posMs / durMs.toFloat() else 0f )
    }

    /* 3 ─── Rotary plumbing */
    val ctx       = LocalContext.current
    val audio     = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val focusReq  = remember { FocusRequester() }

    /* Accumulator for smoother, one-step-per-detent behaviour */
    var accumPx by remember { mutableStateOf(0f) }
    val PIXELS_PER_STEP = 48f         // tweak if your hardware feels different

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
            .safeDrawingPadding()
            .padding(8.dp),
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

        /* ----- Main column ----- */
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showArt) AlbumArtWithProgress(track!!, progress)
            TrackInfo(track!!)
            TransportControls(
                playing  = playing,
                onPrev   = viewModel::skipPrevious,
                onToggle = viewModel::togglePlayPause,
                onNext   = viewModel::skipNext,
                hapticEnabled = hapticEnabled
            )
            ModeButtons(
                shuffleOn   = shuffleOn,
                repeatMode  = repeatMode,
                toggleShuffle   = viewModel::toggleShuffle,
                toggleRepeatMode = viewModel::toggleRepeatMode,
                hapticEnabled = hapticEnabled
            )
            if (durMs > 0) TimeLabel(posMs, durMs)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                           SUB-COMPOSABLES (unchanged)                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun AlbumArtWithProgress(track: Track, progress: Float) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        if (track.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(track.albumArtUri).crossfade(true).build(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "No art",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
        }

        CircularProgressIndicator(
            progress = {progress},
            strokeWidth = 3.dp,
            modifier = Modifier.fillMaxSize(),
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun TrackInfo(track: Track) {
    Text(
        text       = track.title,
        style      = MaterialTheme.typography.titleMedium,
        textAlign  = TextAlign.Center,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis
    )
    Text(
        text       = track.artist,
        style      = MaterialTheme.typography.bodySmall,
        textAlign  = TextAlign.Center,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeButtons(
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    toggleShuffle: () -> Unit,
    toggleRepeatMode: () -> Unit,
    hapticEnabled: Boolean
) {
    val ButtonSize = 24.dp      // outer touch target (≥24 dp is still accessible)
    val IconSize   = 16.dp      // glyph itself
    val haptic = LocalHapticFeedback.current

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconToggleButton(
            checked = shuffleOn,
            onCheckedChange = {
                toggleShuffle()
                if (hapticEnabled){
                    haptic.performHapticFeedback(if (shuffleOn) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                }
            },
            modifier = Modifier
                .size(ButtonSize)          // shrink the overall button
                .clip(CircleShape),        // keep it circular
        ) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier.size(IconSize)   // shrink the icon glyph
            )
        }

        IconToggleButton(
            checked = repeatMode != RepeatMode.OFF,
            onCheckedChange = { toggleRepeatMode()
                if (hapticEnabled){
                    haptic.performHapticFeedback(if (repeatMode==RepeatMode.ONE||repeatMode==RepeatMode.ALL) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                }
            },
            modifier = Modifier
                .size(ButtonSize)
                .clip(CircleShape),
        ) {
            Icon(
                if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                modifier = Modifier.size(IconSize)
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
    hapticEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

@Composable
private fun TimeLabel(posMs: Long, durMs: Long) {
    Text(
        text  = "${formatTime(posMs)} / ${formatTime(durMs)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/* -------------------------------------------------------------------------- */
/*                                  UTILS                                     */
/* -------------------------------------------------------------------------- */

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}

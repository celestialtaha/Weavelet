package com.wapp.wearmusic.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wapp.wearmusic.data.model.Track
import com.wapp.wearmusic.data.repository.MusicRepository
import com.wapp.wearmusic.service.MusicPlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await

/**
 * ViewModel that controls playback through Media3 [MediaController].
 */

class MusicPlayerViewModel(app: Application) : AndroidViewModel(app) {

    /* ------------------------------------------------------------------ */
    /* Dependencies                                                        */
    /* ------------------------------------------------------------------ */

    private val repo  = MusicRepository(app)
    private val prefs = app.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)

    /* ------------------------------------------------------------------ */
    /* MediaController                                                     */
    /* ------------------------------------------------------------------ */

    private val sessionToken = SessionToken(
        app, ComponentName(app, MusicPlaybackService::class.java)
    )
    private var controller: MediaController? = null

    /* ------------------------------------------------------------------ */
    /* UI-state flows                                                      */
    /* ------------------------------------------------------------------ */

    private val _uiState         = MutableStateFlow<MusicPlayerUiState>(MusicPlayerUiState.Loading)
    private val _tracks          = MutableStateFlow<List<Track>>(emptyList())
    private val _currentTrack    = MutableStateFlow<Track?>(null)
    private val _isPlaying       = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)
    private val _duration        = MutableStateFlow(0L)

    val uiState:        StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()
    val tracks:         StateFlow<List<Track>>        = _tracks.asStateFlow()
    val currentTrack:   StateFlow<Track?>             = _currentTrack.asStateFlow()
    val isPlaying:      StateFlow<Boolean>            = _isPlaying.asStateFlow()
    val currentPosition:StateFlow<Long>               = _currentPosition.asStateFlow()
    val duration:       StateFlow<Long>               = _duration.asStateFlow()

    /* ------------------------------------------------------------------ */
    /* Init                                                                */
    /* ------------------------------------------------------------------ */

    private var posTicker: Job? = null

    init {
        viewModelScope.launch {
            controller = MediaController.Builder(getApplication(), sessionToken)
                .buildAsync()
                .await()                            // ✅ ListenableFuture.await()

            controller?.addListener(playerListener) // ✅ correct listener type
            startPositionTicker()
        }
        loadTracks()              // initial library scan
    }

    override fun onCleared() {
        controller?.release()
        posTicker?.cancel()
        super.onCleared()
    }

    /* ------------------------------------------------------------------ */
    /* Public commands (UI)                                                */
    /* ------------------------------------------------------------------ */

    fun loadTracks(): Job = viewModelScope.launch {
        _uiState.value = MusicPlayerUiState.Loading
        try {
            repo.getAllTracks().collectLatest { list ->
                _tracks.value  = list
                _uiState.value =
                    if (list.isEmpty()) MusicPlayerUiState.Empty
                    else                MusicPlayerUiState.Success
            }
        } catch (e: Exception) {
            _uiState.value = MusicPlayerUiState.Error(e.message ?: "Unknown error")
        }
    }

    fun playTracks(startIndex: Int = 0) {
        val list = _tracks.value
        if (list.isEmpty()) return

        // ensure service is running (Android O+)
        val ctx = getApplication<Application>()
        //ctx.startForegroundService(Intent(ctx, MusicPlaybackService::class.java)) !! keep this commented
        ctx.startService(Intent(ctx, MusicPlaybackService::class.java))

        controller?.apply {
            setMediaItems(list.map { it.toMediaItem() }, startIndex, /*pos*/0)
            prepare()
            if (prefs.getBoolean("auto_play_on_start", false)) play()
        }
        _currentTrack.value = list.getOrNull(startIndex)
    }

    fun togglePlayPause() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun skipNext()        = controller?.seekToNextMediaItem()
    fun skipPrevious()    = controller?.seekToPreviousMediaItem()
    fun seekTo(pos: Long) = controller?.seekTo(pos)

    /** Return from Player → Library */
    fun clearCurrentTrack() {
        _currentTrack.value = null
        _isPlaying.value    = false
        controller?.pause()
    }

    /* ------------------------------------------------------------------ */
    /* Internal helpers                                                    */
    /* ------------------------------------------------------------------ */

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri)
                    .build()
            )
            .build()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED))
                _isPlaying.value = player.isPlaying

            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {

                _currentPosition.value = player.currentPosition
                _duration.value        = player.duration.takeIf { it > 0 } ?: 0

                player.currentMediaItem?.mediaId?.let { id ->
                    viewModelScope.launch {
                        repo.getTrackById(id).collectLatest { _currentTrack.value = it }
                    }
                }
            }
        }
    }

    private fun startPositionTicker() {
        posTicker?.cancel()
        posTicker = viewModelScope.launch {
            // keep a local copy so we emit only when value really changes
            var lastPos = -1L
            var lastDur = -1L

            while (isActive) {
                controller?.let { ctrl ->
                    val pos = ctrl.currentPosition
                    val dur = ctrl.duration

                    if (pos != lastPos)  _currentPosition.value = pos
                    if (dur > 0 && dur != lastDur) _duration.value = dur

                    lastPos = pos
                    lastDur = dur
                }

                /* Dynamic delay: 250 ms while playing, 2 s while paused */
                delay(if (controller?.isPlaying == true) 250 else 2_000)
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Sealed UI-state class                                                     */
/* ------------------------------------------------------------------------- */
sealed class MusicPlayerUiState {
    object Loading : MusicPlayerUiState()
    object Success : MusicPlayerUiState()
    object Empty   : MusicPlayerUiState()
    data class Error(val message: String) : MusicPlayerUiState()
}

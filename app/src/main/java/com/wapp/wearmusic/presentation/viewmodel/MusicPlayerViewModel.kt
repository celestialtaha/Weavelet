package com.wapp.wearmusic.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wapp.wearmusic.data.model.Track
import com.wapp.wearmusic.data.model.RepeatMode
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
    private val _shuffleMode     = MutableStateFlow(false)
    private val _repeatMode      = MutableStateFlow(RepeatMode.OFF)
    private val _currentPage = MutableStateFlow(0)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMoreTracks = MutableStateFlow(true)
    private var currentTrackJob: Job? = null

    val uiState:        StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()
    val tracks:         StateFlow<List<Track>>        = _tracks.asStateFlow()
    val currentTrack:   StateFlow<Track?>             = _currentTrack.asStateFlow()
    val isPlaying:      StateFlow<Boolean>            = _isPlaying.asStateFlow()
    val currentPosition:StateFlow<Long>               = _currentPosition.asStateFlow()
    val duration:       StateFlow<Long>               = _duration.asStateFlow()
    val shuffleMode:    StateFlow<Boolean>            = _shuffleMode.asStateFlow()
    val repeatMode:     StateFlow<RepeatMode>         = _repeatMode.asStateFlow()
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    val hasMoreTracks: StateFlow<Boolean> = _hasMoreTracks.asStateFlow()
    val hapticEnabled = MutableStateFlow(true) // default true

    /* ------------------------------------------------------------------ */
    /* Init                                                                */
    /* ------------------------------------------------------------------ */

    private var posTicker: Job? = null
    private var originalTrackList: List<Track> = emptyList()

    init {
        hapticEnabled.value = prefs.getBoolean("haptic_feedback", true)
        viewModelScope.launch {
            controller = MediaController.Builder(getApplication(), sessionToken)
                .buildAsync()
                .await()                            // ✅ ListenableFuture.await()

            controller?.addListener(playerListener) // ✅ correct listener type
            startPositionTicker()
            
            // Load saved settings
            _shuffleMode.value = prefs.getBoolean("shuffle_mode", false)
            _repeatMode.value = RepeatMode.valueOf(
                prefs.getString("repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name
            )
            
            // Apply repeat mode to controller
            updateRepeatMode(_repeatMode.value)
        }
        loadTracks()              // initial library scan
    }

    override fun onCleared() {
        currentTrackJob?.cancel()
        controller?.removeListener(playerListener) // <-- Add this line
        controller?.release()
        posTicker?.cancel()
        super.onCleared()
    }

    /* ------------------------------------------------------------------ */
    /* Public commands (UI)                                                */
    /* ------------------------------------------------------------------ */

   /**
     * Load tracks with pagination
     */
    fun loadTracks() {
        viewModelScope.launch {
            _uiState.value = MusicPlayerUiState.Loading
            
            try {
                repo.getTracksPage(0, 50).collect { page ->
                    _tracks.value = page.tracks
                    _currentPage.value = 0
                    _hasMoreTracks.value = page.hasMore
                    _uiState.value = if (page.tracks.isEmpty()) {
                        MusicPlayerUiState.Empty
                    } else {
                        MusicPlayerUiState.Success
                    }
                }
            } catch (e: Exception) {
                _uiState.value = MusicPlayerUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Load more tracks (pagination)
     */
    fun loadMoreTracks() {
        if (_isLoadingMore.value || !_hasMoreTracks.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            
            try {
                val nextPage = _currentPage.value + 1
                repo.getTracksPage(nextPage * 50, 50).collect { page ->
                    val currentTracks = _tracks.value.toMutableList()
                    currentTracks.addAll(page.tracks)
                    _tracks.value = currentTracks
                    _currentPage.value = nextPage
                    _hasMoreTracks.value = page.hasMore
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Load album art for a specific track
     */
    fun loadAlbumArt(track: Track, onLoaded: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            val bitmap = repo.loadAlbumArt(track)
            onLoaded(bitmap)
        }
    }

    /**
     * Refresh library in background
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            repo.refreshLibraryInBackground()
            loadTracks() // Reload first page
        }
    }

    fun playTracks(startIndex: Int = 0) {
        val list = _tracks.value
        if (list.isEmpty()) return

        // ensure service is running (Android O+)
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, MusicPlaybackService::class.java))

        controller?.apply {
            setMediaItems(list.map { it.toMediaItem() }, startIndex, /*pos*/0)
            prepare()
            if (prefs.getBoolean("auto_play_on_start", false)) play()
        }
        _currentTrack.value = list.getOrNull(startIndex)
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    
    fun skipNext() {
        controller?.seekToNextMediaItem()
    }
    
    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }
    
    fun seekTo(pos: Long) = controller?.seekTo(pos)

    /** Toggle shuffle mode */
    fun toggleShuffle() {

        val newShuffleMode = !_shuffleMode.value
        _shuffleMode.value = newShuffleMode
        
        // Save to preferences
        prefs.edit().putBoolean("shuffle_mode", newShuffleMode).apply()
        
        // Update track list
        _tracks.value = if (newShuffleMode) {
            originalTrackList.shuffled()
        } else {
            originalTrackList
        }
        
        // If currently playing, update the controller's playlist
        if (_currentTrack.value != null) {
            val currentTrack = _currentTrack.value
            val newIndex = _tracks.value.indexOfFirst { it.id == currentTrack?.id }
            if (newIndex >= 0) {
                controller?.setMediaItems(
                    _tracks.value.map { it.toMediaItem() },
                    newIndex,
                    controller?.currentPosition ?: 0
                )
            }
        }
    }

    /** Cycle through repeat modes: OFF -> ALL -> ONE -> OFF */
    fun toggleRepeatMode() {
        val newRepeatMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = newRepeatMode
        
        // Save to preferences
        prefs.edit().putString("repeat_mode", newRepeatMode.name).apply()
        
        // Apply to controller
        updateRepeatMode(newRepeatMode)
    }

    /** Return from Player → Library */
    fun clearCurrentTrack() {
        _currentTrack.value = null
        _isPlaying.value    = false
        controller?.pause()
    }

    /* ------------------------------------------------------------------ */
    /* Internal helpers                                                    */
    /* ------------------------------------------------------------------ */

    private fun updateRepeatMode(mode: RepeatMode) {
        controller?.let { ctrl ->
            ctrl.repeatMode = when (mode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
        }
    }

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

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            updateCurrentTrack()
            _duration.value = sanitizeDuration(controller?.duration)
        }

        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
            updateCurrentTrack()
            val metaDur = sanitizeDuration(metadata.durationMs)
            if (metaDur > 0 && metaDur != _duration.value) _duration.value = metaDur
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                _currentPosition.value = newPosition.positionMs
            }
        }
    }
    private fun startPositionTicker() {
        posTicker?.cancel()
        posTicker = viewModelScope.launch {
            var lastPos = -1L
            while (isActive) {
                controller?.currentPosition?.let { pos ->
                    if (pos != lastPos) _currentPosition.value = pos
                    lastPos = pos
                }
                delay(if (controller?.isPlaying == true) 250 else 2_000)
            }
        }
    }
    private fun sanitizeDuration(dur: Long?): Long =
        dur?.takeIf { it > 0 } ?: 0L


    private fun updateCurrentTrack() {
        controller?.currentMediaItem?.mediaId?.let { id ->
            // Cancel previous request if any
            currentTrackJob?.cancel()
            currentTrackJob = viewModelScope.launch {
                repo.getTrackById(id).collectLatest {
                    _currentTrack.value = it
                }
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
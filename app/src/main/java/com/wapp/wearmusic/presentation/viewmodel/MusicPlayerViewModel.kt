package com.wapp.wearmusic.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wapp.wearmusic.data.model.RepeatMode
import com.wapp.wearmusic.data.model.Track
import com.wapp.wearmusic.data.repository.MusicRepository
import com.wapp.wearmusic.service.MusicPlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await

class MusicPlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)
    private val prefs = app.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow<MusicPlayerUiState>(MusicPlayerUiState.Loading)
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _playbackState = MutableStateFlow(PlaybackState())
    private val _shuffleMode = MutableStateFlow(false)
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    private val _paginationState = MutableStateFlow<PaginationState>(PaginationState.Idle)
    private val _currentPage = MutableStateFlow(0)
    private val _hasMoreTracks = MutableStateFlow(true)

    private val sessionToken = SessionToken(
        app, ComponentName(app, MusicPlaybackService::class.java)
    )
    private val controllerDeferred = viewModelScope.async {
        MediaController.Builder(app, sessionToken)
            .buildAsync()
            .await()
            .apply {
                addListener(playerListener)
                shuffleModeEnabled = prefs.getBoolean("shuffle_mode", false)
                repeatMode = when (RepeatMode.valueOf(
                    prefs.getString("repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name
                )) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
            }
    }
    private var controller: MediaController? = null

    init {
        viewModelScope.launch {
            controller = controllerDeferred.await()
            _shuffleMode.value = controller?.shuffleModeEnabled ?: false
            _repeatMode.value = when (controller?.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        }
    }

    data class PlaybackState(
        val currentTrack: Track? = null,
        val isPlaying: Boolean = false,
        val position: Long = 0L,
        val duration: Long = 0L
    )



    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    val paginationState: StateFlow<PaginationState> = _paginationState.asStateFlow()
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    val hasMoreTracks: StateFlow<Boolean> = _hasMoreTracks.asStateFlow()
    val hapticEnabled = MutableStateFlow(prefs.getBoolean("haptic_feedback", true))

    private val mediaItemCache = mutableMapOf<String, MediaItem>()

    override fun onCleared() {
        progressUpdateJob?.cancel()
        viewModelScope.launch {
            controllerDeferred.await().removeListener(playerListener)
            controllerDeferred.await().release()
        }
        super.onCleared()
    }

    fun loadTracks() {
        // Only load if we're not already loading and haven't loaded anything
        if (_paginationState.value == PaginationState.Idle && _tracks.value.isEmpty()) {
            viewModelScope.launch {
                try {
                    _paginationState.value = PaginationState.LoadingFirst
                    repo.getTracksPage(0, 50).collect { page ->
                        _tracks.value = page.tracks
                        _currentPage.value = 0
                        _hasMoreTracks.value = page.hasMore

                        // Update UI state based on results
                        _uiState.value = when {
                            page.tracks.isNotEmpty() -> MusicPlayerUiState.Success
                            else -> MusicPlayerUiState.Empty
                        }

                        _paginationState.value = PaginationState.Idle
                    }
                } catch (e: Exception) {
                    _uiState.value = MusicPlayerUiState.Error(e.message ?: "Load failed")
                    _paginationState.value = PaginationState.Error(e.message ?: "Load failed")
                }
            }
        }
    }

    fun loadMoreTracks() {
        if (_paginationState.value is PaginationState.LoadingMore || !_hasMoreTracks.value) return

        _paginationState.value = PaginationState.LoadingMore
        viewModelScope.launch {
            delay(300)
            try {
                val nextPage = _currentPage.value + 1
                repo.getTracksPage(nextPage * 50, 50).collect { page ->
                    _tracks.value = _tracks.value + page.tracks
                    _currentPage.value = nextPage
                    _hasMoreTracks.value = page.hasMore
                    _paginationState.value = PaginationState.Idle
                }
            } catch (e: Exception) {
                _paginationState.value = PaginationState.Error(e.message ?: "Load failed")
            }
        }
    }

    fun loadAlbumArt(track: Track, onLoaded: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            onLoaded(repo.loadAlbumArt(track))
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            repo.refreshLibraryInBackground()
            loadTracks()
        }
    }

    fun playTracks(startIndex: Int = 0) {
        if (_tracks.value.isEmpty()) return

        viewModelScope.launch {
            val controller = controllerDeferred.await()
            repo.startPlaybackService()

            controller.setMediaItems(
                _tracks.value.map { it.toCachedMediaItem() },
                startIndex,
                0
            )
            controller.prepare()

            if (prefs.getBoolean("auto_play_on_start", false)) {
                controller.play()
            }

            _playbackState.value = _playbackState.value.copy(
                currentTrack = _tracks.value.getOrNull(startIndex)
            )
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            controllerDeferred.await().let { controller ->
                if (controller.isPlaying) controller.pause() else controller.play()
            }
        }
    }

    fun skipNext() {
        viewModelScope.launch {
            controllerDeferred.await().seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            controllerDeferred.await().seekToPreviousMediaItem()
        }
    }

    fun seekTo(pos: Long) {
        viewModelScope.launch {
            controllerDeferred.await().seekTo(pos)
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            val controller = controllerDeferred.await()
            val newMode = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = newMode
            _shuffleMode.value = newMode
            prefs.edit().putBoolean("shuffle_mode", newMode).apply()
        }
    }

    fun toggleRepeatMode() {
        viewModelScope.launch {
            val controller = controllerDeferred.await()
            val newMode = when (_repeatMode.value) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }

            controller.repeatMode = when (newMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }

            _repeatMode.value = newMode
            prefs.edit().putString("repeat_mode", newMode.name).apply()
        }
    }

    fun clearCurrentTrack() {
        _playbackState.value = _playbackState.value.copy(
            currentTrack = null,
            isPlaying = false
        )
        viewModelScope.launch {
            controllerDeferred.await().pause()
        }
    }


    private fun Track.toCachedMediaItem(): MediaItem {
        return mediaItemCache.getOrPut(id) {
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setArtworkUri(albumArtUri)
                        .setDurationMs(duration)
                        .build()
                )
                .build()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            updateCurrentTrack()
            _playbackState.value = _playbackState.value.copy(
                duration = sanitizeDuration(controller?.duration),
                position = 0L
            )
        }

        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
            updateCurrentTrack()
            val metaDur = sanitizeDuration(metadata.durationMs)
            if (metaDur > 0) {
                _playbackState.value = _playbackState.value.copy(
                    duration = metaDur,
                    position = 0L
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startProgressUpdates()
            else progressUpdateJob?.cancel()
            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playbackState.update { it.copy(position = controller?.currentPosition ?: 0L) }
        }

//        override fun onEvents(player: Player, events: Player.Events) {
//            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
//                _playbackState.update { it.copy(position = player.currentPosition) }
//            }
//        }
    }

    private var progressUpdateJob: Job? = null

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.let {
                    _playbackState.update { state ->
                        state.copy(position = it.currentPosition)
                    }
                }
            }
        }
    }

    private fun sanitizeDuration(dur: Long?): Long {
        return dur?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    }

    private fun updateCurrentTrack() {
        val mediaId = controller?.currentMediaItem?.mediaId ?: return
        val cachedTrack = _tracks.value.find { it.id == mediaId }

        if (cachedTrack != null) {
            _playbackState.update { it.copy(currentTrack = cachedTrack) }
        } else {
            viewModelScope.launch {
                repo.getTrackById(mediaId).collectLatest { track ->
                    if (track != null) {
                        _playbackState.update { it.copy(currentTrack = track) }
                    }
                }
            }
        }
    }
}

sealed class MusicPlayerUiState {
    object Loading : MusicPlayerUiState()
    object Success : MusicPlayerUiState()
    object Empty : MusicPlayerUiState()
    data class Error(val message: String) : MusicPlayerUiState()
}

sealed class PaginationState {
    object Idle : PaginationState()
    object LoadingFirst : PaginationState()
    object LoadingMore : PaginationState()
    data class Error(val message: String) : PaginationState()
}
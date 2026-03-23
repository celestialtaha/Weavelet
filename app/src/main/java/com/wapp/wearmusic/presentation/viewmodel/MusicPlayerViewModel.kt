package com.wapp.wearmusic.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.wapp.wearmusic.complication.MusicComplicationProvider
import com.wapp.wearmusic.core.data.model.ArtistSummary
import com.wapp.wearmusic.core.data.model.RepeatMode
import com.wapp.wearmusic.core.data.model.Track
import com.wapp.wearmusic.core.data.repository.MusicRepository
import com.wapp.wearmusic.core.player.MusicPlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await

class MusicPlayerViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val PAGE_SIZE = 50
    }

    private val repo = MusicRepository(app)
    private val sharedPreferences = app.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow<MusicPlayerUiState>(MusicPlayerUiState.Loading)
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _playbackState = MutableStateFlow(PlaybackState())
    private val _shuffleMode = MutableStateFlow(false)
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    private val _paginationState = MutableStateFlow<PaginationState>(PaginationState.Idle)
    private val _currentPage = MutableStateFlow(0)
    private val _hasMoreTracks = MutableStateFlow(true)
    private val _artists = MutableStateFlow<List<ArtistSummary>>(emptyList())
    private val _artistsLoading = MutableStateFlow(false)
    private val _selectedArtistTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _selectedArtistTracksLoading = MutableStateFlow(false)
    private var loadedSortBy: String? = null
    private var loadedArtists = false
    private var loadedArtistId: Long? = null

    private val sessionToken = SessionToken(
        app, ComponentName(app, MusicPlaybackService::class.java)
    )
    private val complicationUpdateRequester = ComplicationDataSourceUpdateRequester.create(
        app, ComponentName(app, MusicComplicationProvider::class.java)
    )
    private val controllerDeferred = viewModelScope.async {
        MediaController.Builder(app, sessionToken)
            .buildAsync()
            .await()
            .apply {
                addListener(playerListener)
                shuffleModeEnabled = sharedPreferences.getBoolean("shuffle_mode", false)
                repeatMode = when (RepeatMode.valueOf(
                    sharedPreferences.getString("repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name
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
    val artists: StateFlow<List<ArtistSummary>> = _artists.asStateFlow()
    val artistsLoading: StateFlow<Boolean> = _artistsLoading.asStateFlow()
    val selectedArtistTracks: StateFlow<List<Track>> = _selectedArtistTracks.asStateFlow()
    val selectedArtistTracksLoading: StateFlow<Boolean> = _selectedArtistTracksLoading.asStateFlow()
    val currentTrackId: StateFlow<String?> = _playbackState
        .map { it.currentTrack?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hapticEnabled = MutableStateFlow(sharedPreferences.getBoolean("haptic_feedback", true))

    private val mediaItemCache = mutableMapOf<String, MediaItem>()
    private var progressTrackingEnabled = false
    private var lastLibraryFingerprint: MusicRepository.LibraryFingerprint? = null

    override fun onCleared() {
        progressUpdateJob?.cancel()
        controller?.let {
            it.removeListener(playerListener)
            it.release()
        }
        controller = null
        controllerDeferred.cancel()
        super.onCleared()
    }

    fun loadTracks(forceRefresh: Boolean = false) {
        val sortBy = sharedPreferences.getString("sort_by", "title") ?: "title"
        if (_paginationState.value != PaginationState.Idle) {
            return
        }
        if (!forceRefresh && _tracks.value.isNotEmpty() && loadedSortBy == sortBy) {
            return
        }
        viewModelScope.launch {
            if (forceRefresh) {
                repo.clearCache()
                loadedArtists = false
                loadedArtistId = null
                _artists.value = emptyList()
                _selectedArtistTracks.value = emptyList()
            }
            loadFirstPage(sortBy)
        }
    }

    fun loadMoreTracks() {
        if (_paginationState.value is PaginationState.LoadingMore || !_hasMoreTracks.value) return
        _paginationState.value = PaginationState.LoadingMore
        viewModelScope.launch {
            delay(300)
            try {
                val nextPage = _currentPage.value + 1
                val sortBy = sharedPreferences.getString("sort_by", "title") ?: "title"
                val page = repo.getTracksPage(nextPage * PAGE_SIZE, PAGE_SIZE, sortBy).first()
                _tracks.value = _tracks.value + page.tracks
                _currentPage.value = nextPage
                _hasMoreTracks.value = page.hasMore
                _paginationState.value = PaginationState.Idle
            } catch (e: Exception) {
                // Keep paging retryable after transient query failures.
                _paginationState.value = PaginationState.Idle
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
            loadedArtists = false
            loadedArtistId = null
            _artists.value = emptyList()
            _selectedArtistTracks.value = emptyList()
            val sortBy = sharedPreferences.getString("sort_by", "title") ?: "title"
            loadFirstPage(sortBy)
        }
    }

    fun onAppForeground() {
        if (_paginationState.value is PaginationState.LoadingFirst ||
            _paginationState.value is PaginationState.LoadingMore
        ) return

        viewModelScope.launch {
            val currentFingerprint = repo.getLibraryFingerprint(forceFresh = true)
            val knownFingerprint = lastLibraryFingerprint

            if (knownFingerprint != null && knownFingerprint != currentFingerprint) {
                repo.clearCache()
                loadedArtists = false
                loadedArtistId = null
                _artists.value = emptyList()
                _selectedArtistTracks.value = emptyList()
                val sortBy = sharedPreferences.getString("sort_by", "title") ?: "title"
                loadFirstPage(sortBy)
            } else if (knownFingerprint == null) {
                lastLibraryFingerprint = currentFingerprint
            }
        }
    }

    fun loadArtists(forceRefresh: Boolean = false) {
        if (_artistsLoading.value) return
        if (!forceRefresh && loadedArtists && _artists.value.isNotEmpty()) return

        viewModelScope.launch {
            _artistsLoading.value = true
            try {
                _artists.value = repo.getArtists(forceFresh = forceRefresh).first()
                loadedArtists = true
            } finally {
                _artistsLoading.value = false
            }
        }
    }

    fun loadTracksForArtist(artistId: Long, forceRefresh: Boolean = false) {
        if (_selectedArtistTracksLoading.value) return
        if (!forceRefresh && loadedArtistId == artistId && _selectedArtistTracks.value.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _selectedArtistTracksLoading.value = true
            try {
                _selectedArtistTracks.value = repo
                    .getTracksByArtist(artistId = artistId, sortBy = "title", forceFresh = forceRefresh)
                    .first()
                loadedArtistId = artistId
            } finally {
                _selectedArtistTracksLoading.value = false
            }
        }
    }

    fun clearSelectedArtistTracks() {
        loadedArtistId = null
        _selectedArtistTracks.value = emptyList()
    }

    fun playTracks(startIndex: Int = 0) {
        if (_tracks.value.isEmpty()) return
        playTrackList(_tracks.value, startIndex)
    }

    fun playTrackList(trackList: List<Track>, startIndex: Int = 0) {
        if (trackList.isEmpty()) return
        viewModelScope.launch {
            val controller = controllerDeferred.await()
            controller.setMediaItems(trackList.map { it.toCachedMediaItem() }, startIndex, 0)
            controller.prepare()
            if (sharedPreferences.getBoolean("auto_play_on_start", false)) {
                controller.play()
            }
            _playbackState.value = _playbackState.value.copy(currentTrack = trackList.getOrNull(startIndex))
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            controllerDeferred.await().let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    fun skipNext() = viewModelScope.launch { controllerDeferred.await().seekToNextMediaItem() }
    fun skipPrevious() = viewModelScope.launch { controllerDeferred.await().seekToPreviousMediaItem() }
    fun seekTo(pos: Long) = viewModelScope.launch { controllerDeferred.await().seekTo(pos) }

    fun toggleShuffle() {
        viewModelScope.launch {
            val controller = controllerDeferred.await()
            val newMode = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = newMode
            _shuffleMode.value = newMode
            sharedPreferences.edit { putBoolean("shuffle_mode", newMode) }
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
            sharedPreferences.edit { putString("repeat_mode", newMode.name) }
        }
    }

    fun clearCurrentTrack() {
        _playbackState.value = _playbackState.value.copy(
            currentTrack = null,
            isPlaying = false
        )
        MusicComplicationProvider.currentTrackTitle.value = null
        MusicComplicationProvider.isPlaying.value = false
        requestComplicationUpdate()
        viewModelScope.launch {
            controllerDeferred.await().pause()
        }
    }

    fun setProgressTrackingEnabled(enabled: Boolean) {
        progressTrackingEnabled = enabled
        if (enabled && _playbackState.value.isPlaying) {
            startProgressUpdates()
        } else if (!enabled) {
            progressUpdateJob?.cancel()
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
            _playbackState.value = _playbackState.value.copy(duration = sanitizeDuration(controller?.duration), position = 0L)
        }
        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
            updateCurrentTrack()
            val metaDur = sanitizeDuration(metadata.durationMs)
            if (metaDur > 0) _playbackState.value = _playbackState.value.copy(duration = metaDur, position = 0L)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && progressTrackingEnabled) {
                startProgressUpdates()
            } else {
                progressUpdateJob?.cancel()
            }
            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            MusicComplicationProvider.isPlaying.value = isPlaying
            requestComplicationUpdate()
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            _playbackState.update { it.copy(position = controller?.currentPosition ?: 0L) }
        }
    }

    private var progressUpdateJob: Job? = null
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                controller?.let { c -> _playbackState.update { it.copy(position = c.currentPosition) } }
            }
        }
    }

    private fun sanitizeDuration(dur: Long?): Long = dur?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    private suspend fun loadFirstPage(sortBy: String) {
        try {
            _paginationState.value = PaginationState.LoadingFirst
            val page = repo.getTracksPage(0, PAGE_SIZE, sortBy).first()
            // Establish a non-stale baseline used by onAppForeground change detection.
            lastLibraryFingerprint = repo.getLibraryFingerprint(forceFresh = true)
            _tracks.value = page.tracks
            _currentPage.value = 0
            _hasMoreTracks.value = page.hasMore
            loadedSortBy = sortBy
            _uiState.value = if (page.tracks.isNotEmpty()) MusicPlayerUiState.Success else MusicPlayerUiState.Empty
            _paginationState.value = PaginationState.Idle
        } catch (e: Exception) {
            _uiState.value = MusicPlayerUiState.Error(e.message ?: "Load failed")
            _paginationState.value = PaginationState.Error(e.message ?: "Load failed")
        }
    }

    private fun requestComplicationUpdate() {
        runCatching { complicationUpdateRequester.requestUpdateAll() }
    }

    private fun updateCurrentTrack() {
        val mediaId = controller?.currentMediaItem?.mediaId ?: return
        val cachedTrack = _tracks.value.find { it.id == mediaId }
        if (cachedTrack != null) {
            _playbackState.update { it.copy(currentTrack = cachedTrack) }
            MusicComplicationProvider.currentTrackTitle.value = cachedTrack.title
            requestComplicationUpdate()
        } else {
            viewModelScope.launch {
                repo.getTrackById(mediaId).collectLatest { track ->
                    if (track != null) {
                        _playbackState.update { it.copy(currentTrack = track) }
                        MusicComplicationProvider.currentTrackTitle.value = track.title
                        requestComplicationUpdate()
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

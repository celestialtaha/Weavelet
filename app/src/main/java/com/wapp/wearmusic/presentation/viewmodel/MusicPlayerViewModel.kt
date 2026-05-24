package com.wapp.wearmusic.presentation.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.wapp.wearmusic.complication.MusicComplicationProvider
import com.wapp.wearmusic.core.data.model.ArtistSummary
import com.wapp.wearmusic.core.data.model.RepeatMode
import com.wapp.wearmusic.core.data.model.Track
import com.wapp.wearmusic.core.data.repository.MusicRepository
import com.wapp.wearmusic.core.player.MusicPlaybackService
import com.wapp.wearmusic.service.LibraryScanService
import com.wapp.wearmusic.tile.MainTileService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await

class MusicPlayerViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val PAGE_SIZE = 50
        private const val LIBRARY_SCAN_COOLDOWN_MS = 2 * 60 * 1000L
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_TILE_TRACK_TITLE = "tile_track_title"
        private const val KEY_TILE_TRACK_ARTIST = "tile_track_artist"
        private const val KEY_TILE_IS_PLAYING = "tile_is_playing"
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
    private val _trackSearchResults = MutableStateFlow<List<Track>>(emptyList())
    private val _trackSearchLoading = MutableStateFlow(false)
    private val _libraryArtistDetailActive = MutableStateFlow(false)
    private val _libraryInternalBackRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _libraryRefreshing = MutableStateFlow(false)
    private val _hapticEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_HAPTIC_FEEDBACK, true))
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
                repeatMode = when (loadSavedRepeatMode()) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
            }
    }
    private var controller: MediaController? = null
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == KEY_HAPTIC_FEEDBACK) {
                _hapticEnabled.value = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
            }
        }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        registerLibraryScanReceiver()
        viewModelScope.launch {
            controller = controllerDeferred.await()
            val activeController = controller ?: return@launch
            _shuffleMode.value = activeController.shuffleModeEnabled
            _repeatMode.value = when (activeController.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
            syncPlaybackStateFromController(activeController)
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
    val trackSearchResults: StateFlow<List<Track>> = _trackSearchResults.asStateFlow()
    val trackSearchLoading: StateFlow<Boolean> = _trackSearchLoading.asStateFlow()
    val libraryArtistDetailActive: StateFlow<Boolean> = _libraryArtistDetailActive.asStateFlow()
    val libraryInternalBackRequests: SharedFlow<Unit> = _libraryInternalBackRequests.asSharedFlow()
    val libraryRefreshing: StateFlow<Boolean> = _libraryRefreshing.asStateFlow()
    val currentTrackId: StateFlow<String?> = _playbackState
        .map { it.currentTrack?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val mediaItemCache = mutableMapOf<String, MediaItem>()
    private var progressTrackingEnabled = false
    private var trackSearchJob: Job? = null
    private var currentTrackLookupJob: Job? = null
    private var lastLibraryFingerprint: MusicRepository.LibraryFingerprint? = null
    private var lastLibraryScanRequestMs = 0L
    private var libraryScanReceiverRegistered = false
    private val libraryScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LibraryScanService.ACTION_SCAN_COMPLETED) {
                onLibraryScanCompleted()
            }
        }
    }

    override fun onCleared() {
        progressUpdateJob?.cancel()
        trackSearchJob?.cancel()
        currentTrackLookupJob?.cancel()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        unregisterLibraryScanReceiver()
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
                clearTrackSearch()
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
            _libraryRefreshing.value = true
            repo.refreshLibraryInBackground()
            loadedArtists = false
            loadedArtistId = null
            _artists.value = emptyList()
            _selectedArtistTracks.value = emptyList()
            clearTrackSearch()
            val sortBy = sharedPreferences.getString("sort_by", "title") ?: "title"
            try {
                loadFirstPage(sortBy)
            } finally {
                _libraryRefreshing.value = false
            }
        }
    }

    fun searchTracks(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            clearTrackSearch()
            return
        }
        trackSearchJob?.cancel()
        trackSearchJob = viewModelScope.launch {
            _trackSearchLoading.value = true
            try {
                val sortBy = sharedPreferences.getString("sort_by", "title") ?: "title"
                _trackSearchResults.value = repo.searchTracks(trimmed, sortBy).first()
            } catch (_: Exception) {
                _trackSearchResults.value = emptyList()
            } finally {
                _trackSearchLoading.value = false
            }
        }
    }

    fun clearTrackSearch() {
        trackSearchJob?.cancel()
        _trackSearchResults.value = emptyList()
        _trackSearchLoading.value = false
    }

    fun setLibraryArtistDetailActive(active: Boolean) {
        _libraryArtistDetailActive.value = active
    }

    fun requestLibraryInternalBack() {
        _libraryInternalBackRequests.tryEmit(Unit)
    }

    fun onAppForeground() {
        if (_paginationState.value is PaginationState.LoadingFirst ||
            _paginationState.value is PaginationState.LoadingMore
        ) return

        viewModelScope.launch {
            maybeRequestLibraryScan()
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

    private fun registerLibraryScanReceiver() {
        if (libraryScanReceiverRegistered) return
        val app = getApplication<Application>()
        val filter = IntentFilter(LibraryScanService.ACTION_SCAN_COMPLETED)
        ContextCompat.registerReceiver(
            app,
            libraryScanReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        libraryScanReceiverRegistered = true
    }

    private fun unregisterLibraryScanReceiver() {
        if (!libraryScanReceiverRegistered) return
        val app = getApplication<Application>()
        runCatching { app.unregisterReceiver(libraryScanReceiver) }
        libraryScanReceiverRegistered = false
    }

    private fun maybeRequestLibraryScan() {
        val now = System.currentTimeMillis()
        if (now - lastLibraryScanRequestMs < LIBRARY_SCAN_COOLDOWN_MS) return
        lastLibraryScanRequestMs = now
        val app = getApplication<Application>()
        val scanIntent = Intent(app, LibraryScanService::class.java).apply {
            action = LibraryScanService.ACTION_SCAN_LIBRARY
        }
        runCatching { app.startService(scanIntent) }
    }

    private fun onLibraryScanCompleted() {
        if (_paginationState.value is PaginationState.LoadingFirst ||
            _paginationState.value is PaginationState.LoadingMore
        ) return

        viewModelScope.launch {
            val currentFingerprint = repo.getLibraryFingerprint(forceFresh = true)
            val knownFingerprint = lastLibraryFingerprint
            if (knownFingerprint == null || knownFingerprint != currentFingerprint) {
                repo.clearCache()
                loadedArtists = false
                loadedArtistId = null
                _artists.value = emptyList()
                _selectedArtistTracks.value = emptyList()
                val sortBy = sharedPreferences.getString("sort_by", "title") ?: "title"
                loadFirstPage(sortBy)
            } else {
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
            controller.play()
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
            sharedPreferences.edit { putBoolean(KEY_TILE_IS_PLAYING, isPlaying) }
            requestTileUpdate()
            requestComplicationUpdate()
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            _playbackState.update { it.copy(position = controller?.currentPosition ?: 0L) }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let { syncPlaybackStateFromController(it) }
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

    private fun loadSavedRepeatMode(): RepeatMode {
        val saved = sharedPreferences.getString("repeat_mode", RepeatMode.OFF.name)
        return runCatching {
            RepeatMode.valueOf(saved ?: RepeatMode.OFF.name)
        }.getOrDefault(RepeatMode.OFF)
    }

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

    private fun syncPlaybackStateFromController(player: Player) {
        _playbackState.update {
            it.copy(
                isPlaying = player.isPlaying,
                position = player.currentPosition.coerceAtLeast(0L),
                duration = sanitizeDuration(player.duration)
            )
        }
        MusicComplicationProvider.isPlaying.value = player.isPlaying
        if (player.currentMediaItem != null) {
            updateCurrentTrack()
        } else {
            _playbackState.update { it.copy(currentTrack = null) }
            MusicComplicationProvider.currentTrackTitle.value = null
            clearTileNowPlaying()
        }
        requestComplicationUpdate()
    }

    private fun updateCurrentTrack() {
        val mediaId = controller?.currentMediaItem?.mediaId ?: return
        val cachedTrack = _tracks.value.find { it.id == mediaId }
        if (cachedTrack != null) {
            currentTrackLookupJob?.cancel()
            _playbackState.update { it.copy(currentTrack = cachedTrack) }
            MusicComplicationProvider.currentTrackTitle.value = cachedTrack.title
            updateTileNowPlaying(cachedTrack)
            requestComplicationUpdate()
        } else {
            currentTrackLookupJob?.cancel()
            currentTrackLookupJob = viewModelScope.launch {
                val track = repo.getTrackById(mediaId).first()
                if (track != null && controller?.currentMediaItem?.mediaId == mediaId) {
                    _playbackState.update { it.copy(currentTrack = track) }
                    MusicComplicationProvider.currentTrackTitle.value = track.title
                    updateTileNowPlaying(track)
                    requestComplicationUpdate()
                }
            }
        }
    }

    private fun updateTileNowPlaying(track: Track) {
        sharedPreferences.edit {
            putString(KEY_TILE_TRACK_TITLE, track.title)
            putString(KEY_TILE_TRACK_ARTIST, track.artist)
            putBoolean(KEY_TILE_IS_PLAYING, controller?.isPlaying == true)
        }
        requestTileUpdate()
    }

    private fun clearTileNowPlaying() {
        sharedPreferences.edit {
            remove(KEY_TILE_TRACK_TITLE)
            remove(KEY_TILE_TRACK_ARTIST)
            putBoolean(KEY_TILE_IS_PLAYING, false)
        }
        requestTileUpdate()
    }

    private fun requestTileUpdate() {
        val app = getApplication<Application>()
        runCatching {
            TileService.getUpdater(app).requestUpdate(MainTileService::class.java)
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

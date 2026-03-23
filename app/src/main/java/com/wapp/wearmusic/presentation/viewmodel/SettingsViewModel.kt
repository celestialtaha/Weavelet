package com.wapp.wearmusic.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.wapp.wearmusic.complication.MusicComplicationProvider
import com.wapp.wearmusic.core.data.model.Settings
import com.wapp.wearmusic.core.data.model.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for handling user settings
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val sharedPreferences = application.getSharedPreferences(
        "music_player_settings",
        Context.MODE_PRIVATE
    )
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings(): Settings {
        return Settings(
            autoPlayOnStart = sharedPreferences.getBoolean(KEY_AUTO_PLAY, false),
            showAlbumArt = sharedPreferences.getBoolean(KEY_SHOW_ALBUM_ART, true),
            enableComplication = sharedPreferences.getBoolean(KEY_ENABLE_COMPLICATION, true),
            sortBy = sharedPreferences.getString(KEY_SORT_BY, "title") ?: "title",
            shuffleMode = sharedPreferences.getBoolean(KEY_SHUFFLE_MODE, false),
            repeatMode = RepeatMode.valueOf(
                sharedPreferences.getString(KEY_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name
            ),
            hapticFeedback = sharedPreferences.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        )
    }
    
    /**
     * Save settings to SharedPreferences
     */
    private fun saveSettings(settings: Settings) {
        viewModelScope.launch {
            sharedPreferences.edit {
                putBoolean(KEY_AUTO_PLAY, settings.autoPlayOnStart)
                putBoolean(KEY_SHOW_ALBUM_ART, settings.showAlbumArt)
                putBoolean(KEY_ENABLE_COMPLICATION, settings.enableComplication)
                putString(KEY_SORT_BY, settings.sortBy)
                putBoolean(KEY_SHUFFLE_MODE, settings.shuffleMode)
                putString(KEY_REPEAT_MODE, settings.repeatMode.name)
                putBoolean(KEY_HAPTIC_FEEDBACK, settings.hapticFeedback)
            }
        }
    }
    
    /**
     * Update auto play setting
     */
    fun setAutoPlayOnStart(enabled: Boolean) {
        val newSettings = _settings.value.copy(autoPlayOnStart = enabled)
        _settings.value = newSettings
        saveSettings(newSettings)
    }
    
    /**
     * Update show album art setting
     */
    fun setShowAlbumArt(enabled: Boolean) {
        val newSettings = _settings.value.copy(showAlbumArt = enabled)
        _settings.value = newSettings
        saveSettings(newSettings)
    }
    
    /**
     * Update enable complication setting
     */
    fun setEnableComplication(enabled: Boolean) {
        val newSettings = _settings.value.copy(enableComplication = enabled)
        _settings.value = newSettings
        saveSettings(newSettings)
        
        // Update complication state
        updateComplicationState(enabled)
    }
    
    /**
     * Update sort by setting
     */
    fun setSortBy(sortBy: String) {
        val newSettings = _settings.value.copy(sortBy = sortBy)
        _settings.value = newSettings
        saveSettings(newSettings)
    }

    /**
     * Update shuffle mode setting
     */
    fun setShuffleMode(enabled: Boolean) {
        val newSettings = _settings.value.copy(shuffleMode = enabled)
        _settings.value = newSettings
        saveSettings(newSettings)
    }

    /**
     * Update repeat mode setting
     */
    fun setRepeatMode(mode: RepeatMode) {
        val newSettings = _settings.value.copy(repeatMode = mode)
        _settings.value = newSettings
        saveSettings(newSettings)
    }

    /**
     * Update haptic feedback setting
     */
    fun setHapticFeedback(enabled: Boolean) {
        val newSettings = _settings.value.copy(hapticFeedback = enabled)
        _settings.value = newSettings
        saveSettings(newSettings)
    }
    
    /**
     * Enable or disable complication updates
     */
    private fun updateComplicationState(enabled: Boolean) {
        try {
            MusicComplicationProvider.enabled.value = enabled
            ComplicationDataSourceUpdateRequester
                .create(
                    getApplication(),
                    ComponentName(getApplication(), MusicComplicationProvider::class.java)
                )
                .requestUpdateAll()

            Log.d("SettingsViewModel", "Complication state updated: $enabled")
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to update complication state", e)
        }
    }
    
    companion object {
        private const val KEY_AUTO_PLAY = "auto_play_on_start"
        private const val KEY_SHOW_ALBUM_ART = "show_album_art"
        private const val KEY_ENABLE_COMPLICATION = "enable_complication"
        private const val KEY_SORT_BY = "sort_by"
        private const val KEY_SHUFFLE_MODE = "shuffle_mode"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
    }
}

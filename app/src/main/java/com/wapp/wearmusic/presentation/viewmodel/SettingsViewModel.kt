package com.wapp.wearmusic.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wapp.wearmusic.data.model.Settings
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
            sortBy = sharedPreferences.getString(KEY_SORT_BY, "title") ?: "title"
        )
    }
    
    /**
     * Save settings to SharedPreferences
     */
    private fun saveSettings(settings: Settings) {
        viewModelScope.launch {
            sharedPreferences.edit()
                .putBoolean(KEY_AUTO_PLAY, settings.autoPlayOnStart)
                .putBoolean(KEY_SHOW_ALBUM_ART, settings.showAlbumArt)
                .putBoolean(KEY_ENABLE_COMPLICATION, settings.enableComplication)
                .putString(KEY_SORT_BY, settings.sortBy)
                .apply()
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
     * Enable or disable complication updates
     */
    private fun updateComplicationState(enabled: Boolean) {
        // Update the complication provider's enabled state
        try {
            // Update the static enabled state in the complication provider
            com.wapp.wearmusic.complication.MusicComplicationProvider.enabled.value = enabled
            
            // Request a complication update
            val complicationManager = androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
                .create(getApplication(), android.content.ComponentName(getApplication(), 
                    com.wapp.wearmusic.complication.MusicComplicationProvider::class.java))
            complicationManager.requestUpdateAll()
        } catch (e: Exception) {
            // Handle exception if complication provider is not found
            Log.e("SettingsViewModel", "Error updating complication state: ${e.message}")
        }
    }
    
    companion object {
        private const val KEY_AUTO_PLAY = "auto_play_on_start"
        private const val KEY_SHOW_ALBUM_ART = "show_album_art"
        private const val KEY_ENABLE_COMPLICATION = "enable_complication"
        private const val KEY_SORT_BY = "sort_by"
    }
}

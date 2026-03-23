package com.wapp.wearmusic.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Complication provider for the music player
 * Shows the current playback status on the watch face
 */
class MusicComplicationProvider : SuspendingComplicationDataSourceService() {

    companion object {
        // Shared state for the complication
        val currentTrackTitle = MutableStateFlow<String?>(null)
        val isPlaying = MutableStateFlow(false)
        val enabled = MutableStateFlow(true) // Default to enabled

        private const val PREFS_NAME = "music_player_settings"
        private const val KEY_ENABLE_COMPLICATION = "enable_complication"
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // Check if complication is enabled by user settings
        if (!isComplicationEnabled()) {
            // Return default/empty complication data when disabled
            return when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> createDefaultComplicationData()
                else -> null
            }
        }
        
        // Return normal complication data when enabled
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> createShortTextComplicationData(request)
            else -> null
        }
    }
    
    private fun createDefaultComplicationData(): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(getString(R.string.app_name)).build(),
            contentDescription = PlainComplicationText.Builder(getString(R.string.complication_label)).build()
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    Icon.createWithResource(this, R.drawable.ic_music_note)
                ).build()
            )
            .build()
    }
    
    private suspend fun createShortTextComplicationData(request: ComplicationRequest): ComplicationData {
        val title = currentTrackTitle.first() ?: getString(R.string.app_name)
        val icon = R.drawable.ic_music_note

        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(title).build(),
            contentDescription = PlainComplicationText.Builder(getString(R.string.complication_label)).build()
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    Icon.createWithResource(this, icon)
                ).build()
            )
            .setTapAction(pendingIntent)
            .build()
    }

    private fun isComplicationEnabled(): Boolean {
        val stored = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLE_COMPLICATION, true)
        enabled.value = stored
        return stored
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(getString(R.string.app_name)).build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_label)).build()
                )
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_music_note)
                        ).build()
                    )
                    .build()
            }
            else -> null
        }
    }
}

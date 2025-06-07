package com.wapp.wearmusic.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // Check if complication is enabled by user settings
        if (!enabled.first()) {
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
        val title = currentTrackTitle.map { it ?: getString(R.string.app_name) }.first()
        val icon = if (isPlaying.first()) {
            R.drawable.ic_notification // Use the music note icon
        } else {
            R.drawable.ic_music_note
        }

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

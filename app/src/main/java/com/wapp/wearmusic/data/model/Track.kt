package com.wapp.wearmusic.data.model

import android.net.Uri

/**
 * Data class representing a music track
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri? = null
)

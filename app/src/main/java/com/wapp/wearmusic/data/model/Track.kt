package com.wapp.wearmusic.data.model

import android.net.Uri

/**
 * Data class representing a music track with lazy loading support
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long,
    val albumArtUri: Uri? = null,
    val isAlbumArtLoaded: Boolean = false
)

/**
 * Pagination data class for efficient loading
 */
data class TrackPage(
    val tracks: List<Track>,
    val offset: Int,
    val hasMore: Boolean,
    val totalCount: Int
)

/**
 * Cache entry for metadata
 */
data class CachedTrackMetadata(
    val track: Track,
    val timestamp: Long,
    val albumArtBitmap: android.graphics.Bitmap? = null
)
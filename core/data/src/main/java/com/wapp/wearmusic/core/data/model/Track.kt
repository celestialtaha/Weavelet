package com.wapp.wearmusic.core.data.model

import android.net.Uri

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

data class TrackPage(
    val tracks: List<Track>,
    val offset: Int,
    val hasMore: Boolean,
    val totalCount: Int
)

data class ArtistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int
)

data class CachedTrackMetadata(
    val track: Track,
    val timestamp: Long,
    val albumArtBitmap: android.graphics.Bitmap? = null
)

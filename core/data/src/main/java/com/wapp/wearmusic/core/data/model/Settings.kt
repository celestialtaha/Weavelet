package com.wapp.wearmusic.core.data.model

data class Settings(
    val autoPlayOnStart: Boolean = false,
    val showAlbumArt: Boolean = true,
    val enableComplication: Boolean = true,
    val sortBy: String = "title", // Options: title, artist, album, dateAdded
    val shuffleMode: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val hapticFeedback: Boolean = true
)

enum class RepeatMode {
    OFF,    // No repeat
    ONE,    // Repeat current track
    ALL     // Repeat all tracks
}

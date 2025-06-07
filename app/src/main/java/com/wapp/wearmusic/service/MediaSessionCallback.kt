package com.wapp.wearmusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wapp.wearmusic.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Callback for handling media session commands
 */
class MediaSessionCallback(
    private val player: Player,
    private val serviceScope: CoroutineScope
) : MediaSession.Callback {

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        // This is called when the player needs to load media items
        // We need to convert the MediaItems with just a mediaId to fully prepared MediaItems
        val resultItems = mediaItems.map { mediaItem ->
            // The mediaId is the track ID from our database
            val trackId = mediaItem.mediaId.toLongOrNull()
            if (trackId != null) {
                // We already have the MediaItem with metadata in the player
                // Just return it as is
                mediaItem
            } else {
                // If we can't parse the ID, just return the original item
                mediaItem
            }
        }
        
        // Return the list wrapped in a ListenableFuture
        return Futures.immediateFuture(resultItems)
    }

    /**
     * Convert a Track to a MediaItem
     */
    fun trackToMediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.albumArtUri)
                    .build()
            )
            .build()
    }
}

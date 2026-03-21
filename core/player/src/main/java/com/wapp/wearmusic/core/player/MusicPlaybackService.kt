package com.wapp.wearmusic.core.player

import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wapp.wearmusic.core.data.model.Track

class MusicPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val currentIndex = player.currentMediaItemIndex
            if (player.mediaItemCount > currentIndex + 1) {
                player.seekToNextMediaItem()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                setHandleAudioBecomingNoisy(true)
                addListener(playerListener)
            }

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        player.removeListener(playerListener)
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri)
                    .build()
            )
            .build()
}

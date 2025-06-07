package com.wapp.wearmusic.service

import android.content.Intent
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wapp.wearmusic.data.model.Track

/**
 * Playback service based on Media3 1.7.1.
 * - Builds [ExoPlayer] + [MediaSession].
 * - Media3 publishes and updates the media-style notification automatically.
 * - No NotificationCompat, no compat-token juggling, no explicit
 *   startForeground(): the library does it when playback starts.
 *
 * Manifest entry:
 * <service
 *     android:name=".service.MusicPlaybackService"
 *     android:exported="false"
 *     android:foregroundServiceType="mediaPlayback">
 *     <intent-filter>
 *         <action android:name="androidx.media3.session.MediaSessionService"/>
 *     </intent-filter>
 * </service>
 */
class MusicPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

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
            .build().apply { repeatMode = Player.REPEAT_MODE_ALL }

        // No setForegroundServiceBehavior() – removed from public API
        session = MediaSession.Builder(this, player).build()
    }

    /** Expose our MediaSession to controllers. */
    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        session.release()
        player.release()
        super.onDestroy()
    }

    /* ------------------------------------------------------------------ */
    /* Public helper used by the ViewModel                                */
    /* ------------------------------------------------------------------ */

    fun loadAndPlayTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        // For Android O+: startForegroundService *before* playback if you
        // launch the service yourself. (The controller path works without it.)
        startForegroundService(Intent(this, javaClass))

        player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0)
        player.prepare()
        player.play()   // triggers automatic foreground promotion + notification
    }

    /* Track → MediaItem mapper ----------------------------------------- */
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
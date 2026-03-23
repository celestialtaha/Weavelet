package com.wapp.wearmusic.service

import android.app.Service
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.IBinder
import com.wapp.wearmusic.core.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Background service for optimized library scanning
 */
class LibraryScanService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var musicRepository: MusicRepository
    
    override fun onCreate() {
        super.onCreate()
        musicRepository = MusicRepository(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCAN_LIBRARY -> {
                serviceScope.launch {
                    try {
                        triggerMediaStoreScan()
                        musicRepository.refreshLibraryInBackground()
                    } finally {
                        sendBroadcast(
                            Intent(ACTION_SCAN_COMPLETED).setPackage(packageName)
                        )
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun triggerMediaStoreScan() = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val files = musicDir
            ?.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.filter { isLikelyAudioFile(it.name) }
            ?.map(File::getAbsolutePath)
            ?.toList()
            .orEmpty()

        if (files.isEmpty()) return@withContext

        suspendCancellableCoroutine<Unit> { cont ->
            var remaining = files.size
            MediaScannerConnection.scanFile(
                applicationContext,
                files.toTypedArray(),
                null
            ) { _, _ ->
                remaining -= 1
                if (remaining <= 0 && cont.isActive) {
                    cont.resume(Unit)
                }
            }
        }
    }

    private fun isLikelyAudioFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mp3") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".flac") ||
            lower.endsWith(".ogg")
    }
    
    companion object {
        const val ACTION_SCAN_LIBRARY = "com.wapp.wearmusic.SCAN_LIBRARY"
        const val ACTION_SCAN_COMPLETED = "com.wapp.wearmusic.SCAN_COMPLETED"
    }
}

package com.wapp.wearmusic.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.lifecycleScope
import com.wapp.wearmusic.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
                    musicRepository.refreshLibraryInBackground()
                    stopSelf()
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
    
    companion object {
        const val ACTION_SCAN_LIBRARY = "com.wapp.wearmusic.SCAN_LIBRARY"
    }
}
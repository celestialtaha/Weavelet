package com.wapp.wearmusic.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import com.wapp.wearmusic.data.model.Track
import com.wapp.wearmusic.data.model.TrackPage
import com.wapp.wearmusic.data.model.CachedTrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Repository for accessing music files on the device with efficient data loading
 * Uses MediaStore to efficiently query audio files with pagination and caching
 */
class MusicRepository(private val context: Context) {

    companion object {
        private const val PAGE_SIZE = 50
        private const val CACHE_SIZE = 100 // Number of cached items
        private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes
        private const val ALBUM_ART_CACHE_SIZE = 20 * 1024 * 1024 // 20MB for album art
    }

    // Metadata cache
    private val metadataCache = LruCache<String, CachedTrackMetadata>(CACHE_SIZE)
    
    // Album art cache
    private val albumArtCache = object : LruCache<Long, Bitmap>(ALBUM_ART_CACHE_SIZE) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    // Track count cache
    private var cachedTrackCount: Int? = null
    private var trackCountCacheTime: Long = 0

    /**
     * Get all music tracks from the device with pagination
     */
    fun getTracksPage(offset: Int = 0, limit: Int = PAGE_SIZE): Flow<TrackPage> = flow {
        val tracks = mutableListOf<Track>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = android.os.Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, null)
                putString(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.TITLE)
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
            }

            context.contentResolver.query(
                collection,
                projection,
                queryArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val trackId = id.toString()

                    val cached = getCachedTrack(trackId)
                    if (cached != null && !isCacheExpired(cached.timestamp)) {
                        tracks.add(cached.track)
                        continue
                    }

                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    val track = Track(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri,
                        albumId = albumId,
                        albumArtUri = albumArtUri,
                        isAlbumArtLoaded = false
                    )
                    cacheTrack(track)
                    tracks.add(track)
                }
            }
        } else {
            // Manual pagination for pre-R (including Q for simplicity here)
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder // Simple sort, no limit/offset in query for these versions
            )?.use { cursor ->
                if (cursor.moveToPosition(offset)) { // Try to move to the offset
                    var tracksAddedCount = 0
                    // Loop while there are tracks and we haven't added enough for the current page
                    do {
                        if (tracksAddedCount >= limit) break // Reached page limit

                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                        val id = cursor.getLong(idColumn)
                        val trackId = id.toString()

                        val cached = getCachedTrack(trackId)
                        if (cached != null && !isCacheExpired(cached.timestamp)) {
                            tracks.add(cached.track)
                            tracksAddedCount++
                            if (!cursor.moveToNext()) break // Break if no more tracks after adding cached one
                            continue // Continue to next item in cursor
                        }

                        val title = cursor.getString(titleColumn) ?: "Unknown Title"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album"
                        val albumId = cursor.getLong(albumIdColumn)
                        val duration = cursor.getLong(durationColumn)

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        val albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        )

                        val track = Track(
                            id = trackId,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uri = contentUri,
                            albumId = albumId,
                            albumArtUri = albumArtUri,
                            isAlbumArtLoaded = false
                        )
                        cacheTrack(track)
                        tracks.add(track)
                        tracksAddedCount++
                    } while (cursor.moveToNext()) // Move to next, loop continues if successful
                }
            }
        }

        val totalCount = getTotalTrackCount()
        val hasMore = offset + tracks.size < totalCount

        emit(TrackPage(
            tracks = tracks,
            offset = offset,
            hasMore = hasMore,
            totalCount = totalCount
        ))
    }.flowOn(Dispatchers.IO)

    /**
     * Get all tracks (for backward compatibility)
     */
    fun getAllTracks(): Flow<List<Track>> = flow {
        val allTracks = mutableListOf<Track>()
        var offset = 0
        var hasMore = true
        
        while (hasMore) {
            getTracksPage(offset, PAGE_SIZE).collect { page ->
                allTracks.addAll(page.tracks)
                hasMore = page.hasMore
                offset += PAGE_SIZE
            }
        }
        
        emit(allTracks)
    }.flowOn(Dispatchers.IO)

    /**
     * Lazy load album artwork for a track
     */
    suspend fun loadAlbumArt(track: Track): Bitmap? = withContext(Dispatchers.IO) {
        // Check cache first
        albumArtCache.get(track.albumId)?.let { return@withContext it }
        
        try {
            track.albumArtUri?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    bitmap?.let {
                        // Scale down for watch display and memory efficiency
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            it, 128, 128, true
                        )
                        albumArtCache.put(track.albumId, scaledBitmap)
                        return@withContext scaledBitmap
                    }
                }
            }
        } catch (e: IOException) {
            // Album art not available
        }
        null
    }

    /**
     * Get total track count with caching
     */
    private fun getTotalTrackCount(): Int {
        val currentTime = System.currentTimeMillis()
        
        // Return cached count if still valid
        if (cachedTrackCount != null && 
            currentTime - trackCountCacheTime < CACHE_EXPIRY_MS) {
            return cachedTrackCount!!
        }
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            null,
            null
        )?.use { cursor ->
            cachedTrackCount = cursor.count
            trackCountCacheTime = currentTime
            return cursor.count
        }
        
        return 0
    }

    /**
     * Cache management methods
     */
    private fun getCachedTrack(trackId: String): CachedTrackMetadata? {
        return metadataCache.get(trackId)
    }
    
    private fun cacheTrack(track: Track) {
        val cached = CachedTrackMetadata(
            track = track,
            timestamp = System.currentTimeMillis()
        )
        metadataCache.put(track.id, cached)
    }
    
    private fun isCacheExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
    }

    /**
     * Clear caches
     */
    fun clearCache() {
        metadataCache.evictAll()
        albumArtCache.evictAll()
        cachedTrackCount = null
    }

    /**
     * Background library scanning optimization
     */
    suspend fun refreshLibraryInBackground() = withContext(Dispatchers.IO) {
        // Clear caches to force refresh
        clearCache()
        
        // Pre-load first page for quick access
        getTracksPage(0, PAGE_SIZE).collect { page ->
            // Pre-cache album art for first few tracks
            page.tracks.take(10).forEach { track ->
                loadAlbumArt(track)
            }
        }
    }

    /**
     * Get a specific track by ID (existing method)
     */
    fun getTrackById(trackId: String): Flow<Track?> = flow {
        // Check cache first
        val cached = getCachedTrack(trackId)
        if (cached != null && !isCacheExpired(cached.timestamp)) {
            emit(cached.track)
            return@flow
        }
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(trackId)
        
        var track: Track? = null
        
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                
                track = Track(
                    id = trackId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    uri = contentUri,
                    albumId = albumId,
                    albumArtUri = albumArtUri,
                    isAlbumArtLoaded = false
                )
                
                track?.let { cacheTrack(it) }
            }
        }
        
        emit(track)
    }.flowOn(Dispatchers.IO)
}
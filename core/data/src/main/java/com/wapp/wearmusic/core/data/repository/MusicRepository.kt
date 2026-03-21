package com.wapp.wearmusic.core.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.wapp.wearmusic.core.data.model.Track
import com.wapp.wearmusic.core.data.model.TrackPage
import com.wapp.wearmusic.core.data.model.CachedTrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val LOAD_TIMEOUT = 10000L

    /**
     * Get all music tracks from the device with pagination
     */
    fun getTracksPageTimeoutWrapper(
        offset: Int = 0,
        limit: Int = PAGE_SIZE
    ): Flow<TrackPage> = flow {
        try {
            withTimeout(LOAD_TIMEOUT) {
                emitAll(getTracksPage(offset, limit))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MusicRepository", "Track loading timed out", e)
            throw IOException("Track loading timed out", e)
        }
    }

    fun getTracksPage(offset: Int = 0, limit: Int = PAGE_SIZE): Flow<TrackPage> = flow {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

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

            context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
                processCursor(cursor, tracks)
            }
        } else {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                if (cursor.moveToPosition(offset)) {
                    var tracksAddedCount = 0
                    do {
                        if (tracksAddedCount >= limit) break
                        if (processCursorRow(cursor, tracks)) {
                            tracksAddedCount++
                        }
                    } while (cursor.moveToNext())
                }
            }
        }

        val totalCount = getTotalTrackCount()
        val hasMore = offset + tracks.size < totalCount

        emit(TrackPage(tracks, offset, hasMore, totalCount))
    }.flowOn(Dispatchers.IO)

    private fun processCursor(cursor: android.database.Cursor, tracks: MutableList<Track>) {
        while (cursor.moveToNext()) {
            processCursorRow(cursor, tracks)
        }
    }

    private fun processCursorRow(cursor: android.database.Cursor, tracks: MutableList<Track>): Boolean {
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
            return true
        }

        val title = cursor.getString(titleColumn) ?: "Unknown Title"
        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
        val album = cursor.getString(albumColumn) ?: "Unknown Album"
        val albumId = cursor.getLong(albumIdColumn)
        val duration = cursor.getLong(durationColumn)

        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

        val track = Track(
            id = trackId, title = title, artist = artist, album = album,
            duration = duration, uri = contentUri, albumId = albumId,
            albumArtUri = albumArtUri, isAlbumArtLoaded = false
        )
        cacheTrack(track)
        tracks.add(track)
        return true
    }

    suspend fun loadAlbumArt(track: Track): Bitmap? = withContext(Dispatchers.IO) {
        albumArtCache.get(track.albumId)?.let { return@withContext it }
        try {
            track.albumArtUri?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    bitmap?.let {
                        val scaledBitmap = Bitmap.createScaledBitmap(it, 128, 128, true)
                        albumArtCache.put(track.albumId, scaledBitmap)
                        return@withContext scaledBitmap
                    }
                }
            }
        } catch (e: IOException) {}
        null
    }

    private fun getTotalTrackCount(): Int {
        val currentTime = System.currentTimeMillis()
        if (cachedTrackCount != null && currentTime - trackCountCacheTime < CACHE_EXPIRY_MS) {
            return cachedTrackCount!!
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), selection, null, null)?.use { cursor ->
            cachedTrackCount = cursor.count
            trackCountCacheTime = currentTime
            return cursor.count
        }
        return 0
    }

    private fun getCachedTrack(trackId: String): CachedTrackMetadata? = metadataCache.get(trackId)
    private fun cacheTrack(track: Track) {
        metadataCache.put(track.id, CachedTrackMetadata(track, System.currentTimeMillis()))
    }
    private fun isCacheExpired(timestamp: Long): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
    fun clearCache() {
        metadataCache.evictAll()
        albumArtCache.evictAll()
        cachedTrackCount = null
    }

    fun getTrackById(trackId: String): Flow<Track?> = flow {
        val cached = getCachedTrack(trackId)
        if (cached != null && !isCacheExpired(cached.timestamp)) {
            emit(cached.track)
            return@flow
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        context.contentResolver.query(collection, projection, selection, arrayOf(trackId), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val tracks = mutableListOf<Track>()
                processCursorRow(cursor, tracks)
                if (tracks.isNotEmpty()) {
                    emit(tracks[0])
                    return@flow
                }
            }
        }
        emit(null)
    }.flowOn(Dispatchers.IO)
}

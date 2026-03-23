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
import com.wapp.wearmusic.core.data.model.ArtistSummary
import com.wapp.wearmusic.core.data.model.Track
import com.wapp.wearmusic.core.data.model.TrackPage
import com.wapp.wearmusic.core.data.model.CachedTrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
        private const val ARTIST_TRACK_CACHE_SIZE = 40
        private const val SORT_TITLE = "title"
        private const val SORT_ARTIST = "artist"
        private const val SORT_ALBUM = "album"
        private const val SORT_DATE_ADDED = "dateAdded"
        private const val UNKNOWN_ARTIST = "Unknown Artist"
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
    private var cachedLatestDateAddedSec: Long? = null
    private var latestDateAddedCacheTime: Long = 0
    private var cachedArtists: List<ArtistSummary>? = null
    private var artistsCacheTime: Long = 0
    private val LOAD_TIMEOUT = 10000L
    private val artistTracksCache =
        object : LruCache<Long, CachedArtistTracks>(ARTIST_TRACK_CACHE_SIZE) {}

    private data class CachedArtistTracks(
        val tracks: List<Track>,
        val timestamp: Long
    )

    data class LibraryFingerprint(
        val totalTracks: Int,
        val latestDateAddedSec: Long
    )

    /**
     * Get all music tracks from the device with pagination
     */
    fun getTracksPageTimeoutWrapper(
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
        sortBy: String = SORT_TITLE
    ): Flow<TrackPage> = flow {
        try {
            withTimeout(LOAD_TIMEOUT) {
                emitAll(getTracksPage(offset, limit, sortBy))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MusicRepository", "Track loading timed out", e)
            throw IOException("Track loading timed out", e)
        }
    }

    fun getTracksPage(
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
        sortBy: String = SORT_TITLE
    ): Flow<TrackPage> = flow {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val sortConfig = getSortConfig(sortBy)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = sortConfig.legacySortOrder

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = android.os.Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, null)
                putString(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, sortConfig.sortColumn)
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, sortConfig.sortDirection)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
            }

            context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
                processCursor(cursor, tracks)
            }
        } else {
            val pagedSortOrder = "$sortOrder LIMIT $limit OFFSET $offset"
            context.contentResolver.query(collection, projection, selection, null, pagedSortOrder)?.use { cursor ->
                processCursor(cursor, tracks)
            }
        }

        val totalCount = getTotalTrackCount()
        val hasMore = offset + tracks.size < totalCount

        emit(TrackPage(tracks, offset, hasMore, totalCount))
    }.flowOn(Dispatchers.IO)

    private data class SortConfig(
        val sortColumn: String,
        val sortDirection: Int,
        val legacySortOrder: String
    )

    private fun getSortConfig(sortBy: String): SortConfig {
        return when (sortBy) {
            SORT_ARTIST -> SortConfig(
                sortColumn = MediaStore.Audio.Media.ARTIST,
                sortDirection = android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING,
                legacySortOrder = "${MediaStore.Audio.Media.ARTIST} ASC, ${MediaStore.Audio.Media.TITLE} ASC"
            )
            SORT_ALBUM -> SortConfig(
                sortColumn = MediaStore.Audio.Media.ALBUM,
                sortDirection = android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING,
                legacySortOrder = "${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TITLE} ASC"
            )
            SORT_DATE_ADDED -> SortConfig(
                sortColumn = MediaStore.Audio.Media.DATE_ADDED,
                sortDirection = android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
                legacySortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            else -> SortConfig(
                sortColumn = MediaStore.Audio.Media.TITLE,
                sortDirection = android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING,
                legacySortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            )
        }
    }

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
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { boundsStream ->
                    BitmapFactory.decodeStream(boundsStream, null, options)
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(options, 128, 128)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                    bitmap?.let {
                        val scaledBitmap = Bitmap.createScaledBitmap(it, 128, 128, true)
                        albumArtCache.put(track.albumId, scaledBitmap)
                        if (scaledBitmap != it) {
                            it.recycle()
                        }
                        return@withContext scaledBitmap
                    }
                }
            }
        } catch (e: IOException) {}
        null
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun getTotalTrackCount(forceFresh: Boolean = false): Int {
        val currentTime = System.currentTimeMillis()
        if (!forceFresh && cachedTrackCount != null && currentTime - trackCountCacheTime < CACHE_EXPIRY_MS) {
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

    private fun getLatestDateAddedSec(forceFresh: Boolean = false): Long {
        val currentTime = System.currentTimeMillis()
        if (!forceFresh &&
            cachedLatestDateAddedSec != null &&
            currentTime - latestDateAddedCacheTime < CACHE_EXPIRY_MS
        ) {
            return cachedLatestDateAddedSec!!
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Audio.Media.DATE_ADDED)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = android.os.Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putString(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.DATE_ADDED)
                putInt(
                    android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
            }
            context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val latest = cursor.getLong(0)
                    cachedLatestDateAddedSec = latest
                    latestDateAddedCacheTime = currentTime
                    return latest
                }
            }
        } else {
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC LIMIT 1"
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val latest = cursor.getLong(0)
                    cachedLatestDateAddedSec = latest
                    latestDateAddedCacheTime = currentTime
                    return latest
                }
            }
        }

        cachedLatestDateAddedSec = 0L
        latestDateAddedCacheTime = currentTime
        return 0L
    }

    suspend fun getLibraryFingerprint(forceFresh: Boolean = false): LibraryFingerprint =
        withContext(Dispatchers.IO) {
            LibraryFingerprint(
                totalTracks = getTotalTrackCount(forceFresh = forceFresh),
                latestDateAddedSec = getLatestDateAddedSec(forceFresh = forceFresh)
            )
        }

    fun getArtists(forceFresh: Boolean = false): Flow<List<ArtistSummary>> = flow {
        val currentTime = System.currentTimeMillis()
        if (!forceFresh &&
            cachedArtists != null &&
            currentTime - artistsCacheTime < CACHE_EXPIRY_MS
        ) {
            emit(cachedArtists!!)
            return@flow
        }

        val artists = mutableListOf<ArtistSummary>()
        val collection = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )
        val selection = "${MediaStore.Audio.Artists.NUMBER_OF_TRACKS} > 0"
        val sortOrder = "${MediaStore.Audio.Artists.ARTIST} COLLATE NOCASE ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val trackCountColumn = cursor.getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = normalizeArtistName(cursor.getString(artistColumn))
                val trackCount = if (trackCountColumn >= 0) cursor.getInt(trackCountColumn) else 0
                artists.add(
                    ArtistSummary(
                        id = id,
                        name = name,
                        trackCount = trackCount
                    )
                )
            }
        }

        cachedArtists = artists
        artistsCacheTime = currentTime
        emit(artists)
    }.flowOn(Dispatchers.IO)

    fun getTracksByArtist(
        artistId: Long,
        sortBy: String = SORT_TITLE,
        forceFresh: Boolean = false
    ): Flow<List<Track>> = flow {
        val cached = artistTracksCache.get(artistId)
        if (!forceFresh && cached != null && !isCacheExpired(cached.timestamp)) {
            emit(cached.tracks)
            return@flow
        }

        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val sortConfig = getSortConfig(sortBy)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(artistId.toString())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = android.os.Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, sortConfig.sortColumn)
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, sortConfig.sortDirection)
            }
            context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
                processCursor(cursor, tracks)
            }
        } else {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortConfig.legacySortOrder
            )?.use { cursor ->
                processCursor(cursor, tracks)
            }
        }

        artistTracksCache.put(
            artistId,
            CachedArtistTracks(
                tracks = tracks,
                timestamp = System.currentTimeMillis()
            )
        )
        emit(tracks)
    }.flowOn(Dispatchers.IO)

    private fun getCachedTrack(trackId: String): CachedTrackMetadata? = metadataCache.get(trackId)
    private fun cacheTrack(track: Track) {
        metadataCache.put(track.id, CachedTrackMetadata(track, System.currentTimeMillis()))
    }
    private fun isCacheExpired(timestamp: Long): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
    fun clearCache() {
        metadataCache.evictAll()
        albumArtCache.evictAll()
        artistTracksCache.evictAll()
        cachedTrackCount = null
        cachedLatestDateAddedSec = null
        cachedArtists = null
    }

    suspend fun refreshLibraryInBackground() = withContext(Dispatchers.IO) {
        clearCache()
            getTracksPage(0, PAGE_SIZE).collect { page ->
                page.tracks.take(10).forEach { track ->
                    loadAlbumArt(track)
                }
        }
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

    private fun normalizeArtistName(rawName: String?): String {
        val normalized = rawName?.trim().orEmpty()
        if (normalized.isEmpty()) return UNKNOWN_ARTIST
        return if (normalized.equals("<unknown>", ignoreCase = true)) UNKNOWN_ARTIST else normalized
    }
}

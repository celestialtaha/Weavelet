package com.wapp.wearmusic.presentation.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wapp.wearmusic.R
import com.wapp.wearmusic.core.data.model.Track
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerUiState
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.PaginationState
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel
import com.wapp.wearmusic.presentation.screens.ErrorScreen
import com.wapp.wearmusic.presentation.screens.LoadingScreen
import com.wapp.wearmusic.presentation.screens.EmptyLibraryScreen
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onTrackClick: (Int) -> Unit,
) {
    // 1) Observe ViewModel state
    val tracks by viewModel.tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle(initialValue = null)
    // val position = playbackState.position TODO: use this
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val paginationState by viewModel.paginationState.collectAsStateWithLifecycle()
    val hasMoreTracks by viewModel.hasMoreTracks.collectAsStateWithLifecycle()

    val listState = rememberScalingLazyListState()
    val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }

    // 2) Observe settings (for showAlbumArt)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val showArt = settings.showAlbumArt
    val configuration = LocalConfiguration.current
    val minScreenDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val compact = minScreenDp <= 220
    val horizontalPadding = if (compact) 4.dp else 6.dp
    val trackItemWidthFraction = if (compact) 0.98f else 0.96f

    LaunchedEffect(settings.sortBy) {
        viewModel.loadTracks()
    }

    // 3) Search query
    var query by rememberSaveable { mutableStateOf("") }

    // 4) Filtered results
    val results: List<Track> = remember(tracks, query) {
        if (query.isBlank()) {
            tracks
        } else {
            val q = query.trim()
            tracks.filter {
                it.title.contains(q, ignoreCase = true) ||
                        it.artist.contains(q, ignoreCase = true) ||
                        it.album.contains(q, ignoreCase = true)
            }
        }
    }

    // 5) Auto-load more when scrolling near the end
    LaunchedEffect(layoutInfo, hasMoreTracks, paginationState) {
        if (layoutInfo.visibleItemsInfo.isNotEmpty() &&
            paginationState is PaginationState.Idle &&
            hasMoreTracks) {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.last()
                val loadThreshold = 5 // Load more when 5 items from end

                if (lastVisibleItem.index >= tracks.size - loadThreshold) {
                    // Add small delay to prevent multiple rapid calls
                    delay(100)
                    if (paginationState is PaginationState.Idle) {
                        viewModel.loadMoreTracks()
                    }
                }
        }
    }

    ScreenScaffold(
        scrollState = listState
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 6) Show loading/empty/error if needed
            when (uiState) {
                is MusicPlayerUiState.Loading -> LoadingScreen()
                is MusicPlayerUiState.Empty -> EmptyLibraryScreen()
                is MusicPlayerUiState.Error -> ErrorScreen((uiState as MusicPlayerUiState.Error).message)
                MusicPlayerUiState.Success -> {
                    // 7) Main track list
                    ScalingLazyColumn(
                        state = listState,
                        contentPadding = contentPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            ListHeader(modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.music_library))
                            }
                        }

                        // (a) Search box
                        item {
                            SearchBox(
                                query = query,
                                onQueryChange = { query = it },
                                compact = compact,
                                modifier = Modifier.fillMaxWidth(trackItemWidthFraction)
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        // (b) If no results
                        if (results.isEmpty()) {
                            item {
                                Text(
                                    if (query.isNotEmpty()) stringResource(R.string.no_music_found)
                                    else stringResource(R.string.empty_library),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                                    modifier = Modifier
                                        .fillMaxWidth(trackItemWidthFraction)
                                        .padding(top = 16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // (c) Each track row
                            itemsIndexed(
                                items = results,
                                key = { _, track -> track.id }
                            ) { _, track ->
                                TrackItem(
                                    track = track,
                                    showAlbumArt = showArt,
                                    isPlaying = track.id == currentTrackId,
                                    compact = compact,
                                    itemWidthFraction = trackItemWidthFraction,
                                    onClick = {
                                        val index = tracks.indexOfFirst { it.id == track.id }
                                        if (index >= 0) onTrackClick(index)
                                    }
                                )
                            }

                            // (d) Pagination loading indicator
                            if (paginationState is PaginationState.LoadingMore) {
                                item {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    )
                                }
                            }

                            item { Spacer(Modifier.height(32.dp)) }
                        }
                    }
                }
            }

            // 8) Show initial loading indicator during first load
            if (paginationState is PaginationState.LoadingFirst) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val kb = LocalSoftwareKeyboardController.current
    Box(
        modifier
            .background(MaterialTheme.colorScheme.primaryDim, CircleShape)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp)
    ) {
        if (query.isBlank()) {
            Text(
                stringResource(R.string.search_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                modifier = Modifier.padding(start = 24.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle.Default.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                modifier = Modifier.weight(1f)
            )

            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(R.string.clear),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: Track,
    showAlbumArt: Boolean,
    isPlaying: Boolean,
    compact: Boolean,
    itemWidthFraction: Float,
    onClick: () -> Unit
) {
    val artistColor = if (isPlaying) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    val cardColors = if (isPlaying) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            titleColor = MaterialTheme.colorScheme.onPrimaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            subtitleColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f)
        )
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(itemWidthFraction)
            .padding(vertical = 2.dp),
        colors = cardColors
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAlbumArt) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track.albumArtUri)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .build(),
                    contentDescription = stringResource(R.string.album_art_for, track.title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (compact) 34.dp else 38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = artistColor
                )
            }

            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

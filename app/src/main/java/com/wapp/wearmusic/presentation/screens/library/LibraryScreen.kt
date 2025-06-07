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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wapp.wearmusic.R
import com.wapp.wearmusic.data.model.Track
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerUiState
import com.wapp.wearmusic.presentation.viewmodel.MusicPlayerViewModel
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel
import com.wapp.wearmusic.presentation.screens.ErrorScreen
import com.wapp.wearmusic.presentation.screens.LoadingScreen
import com.wapp.wearmusic.presentation.screens.EmptyLibraryScreen

@Composable
fun LibraryScreen(
    viewModel: MusicPlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onTrackClick: (Int) -> Unit,
    //onSettingsClick: () -> Unit  // No longer used, but kept for signature compatibility
) {
    // 1) Observe ViewModel state
    val tracks by viewModel.tracks.collectAsState(initial = emptyList())
    val currentTrack by viewModel.currentTrack.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMoreTracks by viewModel.hasMoreTracks.collectAsState()

    // 2) Observe settings (for showAlbumArt)
    val settings by settingsViewModel.settings.collectAsState()
    val showArt = settings.showAlbumArt

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

    // 5) Prepare list state and auto-scroll to currently playing
    LaunchedEffect(layoutInfo) {
        if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val lastVisibleItem = layoutInfo.visibleItemsInfo.last()
            val loadThreshold = 5 // Load more when 5 items from end

            if (lastVisibleItem.index >= tracks.size - loadThreshold) {
                if (!isLoadingMore && hasMoreTracks) {
                    viewModel.loadMoreTracks()
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        // 6) Show loading/empty/error if needed
        when (uiState) {
            MusicPlayerUiState.Loading -> LoadingScreen()
            MusicPlayerUiState.Empty -> EmptyLibraryScreen()
            is MusicPlayerUiState.Error -> ErrorScreen((uiState as MusicPlayerUiState.Error).message)
            else -> {
                // 7) Main track list
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // (a) Search box
                    item {
                        SearchBox(query = query, onQueryChange = { query = it })
                        Spacer(Modifier.height(8.dp))
                    }

                    // (b) If no results
                    if (results.isEmpty()) {
                        item {
                            Text(
                                if (query.isNotEmpty()) stringResource(R.string.no_music_found)
                                else stringResource(R.string.empty_library),
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = .6f),
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .fillMaxWidth(),
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
                                isPlaying = track.id == currentTrack?.id, // ID comparison
                                onClick = {
                                    val index = tracks.indexOfFirst { it.id == track.id }
                                    if (index >= 0) onTrackClick(index)
                                }
                            )
                        }
                        if (isLoadingMore) {
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
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val kb = LocalSoftwareKeyboardController.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface, CircleShape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (query.isBlank()) {
            Text(
                stringResource(R.string.search_hint),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurface.copy(alpha = .6f),
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
                    color = MaterialTheme.colors.onSurface,
                    fontSize = MaterialTheme.typography.body2.fontSize
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
    onClick: () -> Unit
) {
    // Height: 40.dp for art + 8.dp padding top/bottom = 56.dp total
    val rowHeight = 56.dp
    val bgColor = if (isPlaying) MaterialTheme.colors.primary.copy(alpha = .2f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.surface)
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.title2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onSurface.copy(alpha = .7f)
            )
        }

        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.now_playing),
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

package com.wapp.wearmusic.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Arrangement
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

import com.wapp.wearmusic.R

private data class EmptyLibraryLayoutSpec(
    val contentWidthFraction: Float,
    val refreshWidthFraction: Float,
    val sidePadding: Dp,
    val iconTitleSpacing: Dp,
    val titleBodySpacing: Dp,
    val bodyHintSpacing: Dp,
    val hintActionSpacing: Dp,
    val refreshBackSpacing: Dp,
    val useCompactTitle: Boolean,
    val edgeButtonSize: EdgeButtonSize
)

private fun emptyLibraryLayoutSpec(minScreenDp: Int): EmptyLibraryLayoutSpec {
    return when {
        // Very small round screens
        minScreenDp <= 192 -> EmptyLibraryLayoutSpec(
            contentWidthFraction = 0.94f,
            refreshWidthFraction = 0.66f,
            sidePadding = 8.dp,
            iconTitleSpacing = 2.dp,
            titleBodySpacing = 3.dp,
            bodyHintSpacing = 3.dp,
            hintActionSpacing = 6.dp,
            refreshBackSpacing = 3.dp,
            useCompactTitle = true,
            edgeButtonSize = EdgeButtonSize.ExtraSmall
        )
        // Common Wear sizes below large-screen breakpoint.
        minScreenDp < 225 -> EmptyLibraryLayoutSpec(
            contentWidthFraction = 0.90f,
            refreshWidthFraction = 0.68f,
            sidePadding = 9.dp,
            iconTitleSpacing = 3.dp,
            titleBodySpacing = 4.dp,
            bodyHintSpacing = 4.dp,
            hintActionSpacing = 8.dp,
            refreshBackSpacing = 4.dp,
            useCompactTitle = true,
            edgeButtonSize = EdgeButtonSize.ExtraSmall
        )
        // 225dp+ larger Wear screens.
        else -> EmptyLibraryLayoutSpec(
            contentWidthFraction = 0.86f,
            refreshWidthFraction = 0.72f,
            sidePadding = 10.dp,
            iconTitleSpacing = 4.dp,
            titleBodySpacing = 5.dp,
            bodyHintSpacing = 4.dp,
            hintActionSpacing = 8.dp,
            refreshBackSpacing = 4.dp,
            useCompactTitle = false,
            edgeButtonSize = EdgeButtonSize.Small
        )
    }
}

/**
 * Loading screen shown while tracks are being loaded
 */
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading_music),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty library screen shown when no music files are found
 */
@Composable
fun EmptyLibraryScreen(
    onBackClick: () -> Unit,
    onRefreshClick: (() -> Unit)? = null,
    isRefreshing: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val minScreenDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val layout = remember(minScreenDp) { emptyLibraryLayoutSpec(minScreenDp) }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onBackClick,
                buttonSize = layout.edgeButtonSize
            ) {
                Text(
                    text = stringResource(R.string.back),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = contentPadding,
            autoCentering = null
        ) {
            item {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            item { Spacer(modifier = Modifier.height(layout.iconTitleSpacing)) }
            item {
                Text(
                    text = stringResource(id = R.string.empty_library_title),
                    style = (if (layout.useCompactTitle) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                        .copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(layout.contentWidthFraction)
                        .padding(horizontal = layout.sidePadding)
                )
            }
            item { Spacer(modifier = Modifier.height(layout.titleBodySpacing)) }
            item {
                Text(
                    text = stringResource(id = R.string.empty_library_help),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth(layout.contentWidthFraction)
                        .padding(horizontal = layout.sidePadding)
                )
            }
            item { Spacer(modifier = Modifier.height(layout.bodyHintSpacing)) }
            item {
                Text(
                    text = stringResource(R.string.swipe_back_hint),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(layout.contentWidthFraction)
                )
            }
            item { Spacer(modifier = Modifier.height(layout.hintActionSpacing)) }
            if (onRefreshClick != null) {
                item {
                    Button(
                        onClick = onRefreshClick,
                        modifier = Modifier.fillMaxWidth(layout.refreshWidthFraction),
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRefreshing) {
                                stringResource(R.string.refreshing)
                            } else {
                                stringResource(R.string.refresh_library)
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(layout.refreshBackSpacing)) }
            }
        }
    }
}

/**
 * Error screen shown when there's an error loading music files
 */
@Composable
fun ErrorScreen(
    message: String,
    onBackClick: () -> Unit,
    onRetryClick: (() -> Unit)? = null,
    isRetrying: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val minScreenDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val compact = minScreenDp < 225
    val listState = rememberScalingLazyListState()
    val contentWidth = if (compact) 0.9f else 0.84f

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onBackClick,
                buttonSize = if (compact) EdgeButtonSize.ExtraSmall else EdgeButtonSize.Small
            ) {
                Text(stringResource(R.string.back))
            }
        }
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = contentPadding,
            autoCentering = null
        ) {
            item {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            item { Spacer(modifier = Modifier.height(if (compact) 3.dp else 4.dp)) }
            item {
                Text(
                    text = stringResource(id = R.string.error_loading_music),
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(contentWidth)
                )
            }
            if (message.isNotBlank()) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth(contentWidth)
                            .padding(horizontal = if (compact) 8.dp else 10.dp)
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp)) }
            if (onRetryClick != null) {
                item {
                    Button(
                        onClick = onRetryClick,
                        enabled = !isRetrying,
                        modifier = Modifier.fillMaxWidth(0.68f)
                    ) {
                        if (isRetrying) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRetrying) {
                                stringResource(R.string.refreshing)
                            } else {
                                stringResource(R.string.retry)
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
            item {
                Text(
                    text = stringResource(R.string.swipe_back_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(contentWidth)
                )
            }
        }
    }
}

package com.wapp.wearmusic.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settings = settingsViewModel.settings.collectAsState().value

    ScalingLazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        item {
            SettingActionButton(
                label = stringResource(R.string.auto_play_on_start),
                enabled = settings.autoPlayOnStart,
                onClick = { settingsViewModel.setAutoPlayOnStart(!settings.autoPlayOnStart) }
            )
        }
        item {
            SettingActionButton(
                label = stringResource(R.string.show_album_art),
                enabled = settings.showAlbumArt,
                onClick = { settingsViewModel.setShowAlbumArt(!settings.showAlbumArt) }
            )
        }
        item {
            SettingActionButton(
                label = stringResource(R.string.enable_complication),
                enabled = settings.enableComplication,
                onClick = { settingsViewModel.setEnableComplication(!settings.enableComplication) }
            )
        }

        item {
            Text(
                text = stringResource(R.string.sort_by),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        item {
            SortOptionButton(
                label = stringResource(R.string.sort_by_title),
                selected = settings.sortBy == "title",
                onClick = { settingsViewModel.setSortBy("title") }
            )
        }
        item {
            SortOptionButton(
                label = stringResource(R.string.sort_by_artist),
                selected = settings.sortBy == "artist",
                onClick = { settingsViewModel.setSortBy("artist") }
            )
        }
        item {
            SortOptionButton(
                label = stringResource(R.string.sort_by_album),
                selected = settings.sortBy == "album",
                onClick = { settingsViewModel.setSortBy("album") }
            )
        }
        item {
            SortOptionButton(
                label = stringResource(R.string.sort_by_date_added),
                selected = settings.sortBy == "dateAdded",
                onClick = { settingsViewModel.setSortBy("dateAdded") }
            )
        }

        item {
            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
private fun SettingActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        val stateText = if (enabled) "ON" else "OFF"
        Text("$label: $stateText", textAlign = TextAlign.Center)
    }
}

@Composable
private fun SortOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        val prefix = if (selected) "• " else ""
        Text("$prefix$label", textAlign = TextAlign.Center)
    }
}

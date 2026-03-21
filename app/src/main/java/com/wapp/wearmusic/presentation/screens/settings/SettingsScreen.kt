package com.wapp.wearmusic.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val configuration = LocalConfiguration.current
    val minScreenDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val compact = minScreenDp <= 220
    val horizontalPadding = if (compact) 10.dp else 14.dp

    ScreenScaffold(
        scrollState = listState,
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            contentPadding = it,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.settings))
                }
            }

            item {
                SettingSwitchButton(
                    label = stringResource(R.string.auto_play_on_start),
                    checked = settings.autoPlayOnStart,
                    onCheckedChange = settingsViewModel::setAutoPlayOnStart
                )
            }
            item {
                SettingSwitchButton(
                    label = stringResource(R.string.show_album_art),
                    checked = settings.showAlbumArt,
                    onCheckedChange = settingsViewModel::setShowAlbumArt
                )
            }
            item {
                SettingSwitchButton(
                    label = stringResource(R.string.enable_complication),
                    checked = settings.enableComplication,
                    onCheckedChange = settingsViewModel::setEnableComplication
                )
            }

            item {
                ListSubHeader(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.sort_by)) }
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

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SettingSwitchButton(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, textAlign = TextAlign.Start)
    }
}

@Composable
private fun SortOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    RadioButton(
        selected = selected,
        onSelect = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, textAlign = TextAlign.Start)
    }
}

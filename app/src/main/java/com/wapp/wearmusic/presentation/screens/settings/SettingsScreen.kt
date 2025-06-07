package com.wapp.wearmusic.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.viewmodel.SettingsViewModel

/* --------------------------------------------------------------------- */
/*  Settings screen                                                      */
/* --------------------------------------------------------------------- */

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settings = settingsViewModel.settings.collectAsState().value

    /* constant list – remembered so it isn’t recreated every recomposition */
    val sortOptions = remember {
        listOf(
            R.string.sort_by_title      to "title",
            R.string.sort_by_artist     to "artist",
            R.string.sort_by_album      to "album",
            R.string.sort_by_date_added to "dateAdded"
        )
    }

    ScalingLazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        /* header ------------------------------------------------------ */
        item {
            Text(
                text     = stringResource(R.string.settings),
                style    = MaterialTheme.typography.title1,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        /* toggles ----------------------------------------------------- */
        item {
            SettingToggle(
                checked  = settings.autoPlayOnStart,
                labelRes = R.string.auto_play_on_start,
                onToggle = settingsViewModel::setAutoPlayOnStart
            )
        }
        item {
            SettingToggle(
                checked  = settings.showAlbumArt,
                labelRes = R.string.show_album_art,
                onToggle = settingsViewModel::setShowAlbumArt
            )
        }
        item {
            SettingToggle(
                checked  = settings.enableComplication,
                labelRes = R.string.enable_complication,
                onToggle = settingsViewModel::setEnableComplication
            )
        }

        /* sorting section -------------------------------------------- */
        item {
            Text(
                text     = stringResource(R.string.sort_by),
                style    = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        itemsIndexed(
            sortOptions,
            key = { _, option -> option.second }            // stable key for diffing
        ) { _, (textRes, value) ->
            ToggleChip(
                checked = settings.sortBy == value,
                onCheckedChange = { if (it) settingsViewModel.setSortBy(value) },
                label = { Text(stringResource(textRes)) },
                toggleControl = {
                    Checkbox(checked = settings.sortBy == value, enabled = true)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        /* back button ------------------------------------------------- */
        item {
            Button(
                onClick  = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

/* --------------------------------------------------------------------- */
/*  Re-usable toggle chip                                                */
/* --------------------------------------------------------------------- */
@Composable
private fun SettingToggle(
    checked: Boolean,
    labelRes: Int,
    onToggle: (Boolean) -> Unit
) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onToggle,
        label = { Text(stringResource(labelRes)) },
        toggleControl = {
            Switch(
                checked = checked,
                enabled = true,
                colors  = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colors.primary
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

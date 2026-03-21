package com.wapp.wearmusic.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun WearMusicTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(context) {
        dynamicColorScheme(context)
    } ?: MaterialTheme.colorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

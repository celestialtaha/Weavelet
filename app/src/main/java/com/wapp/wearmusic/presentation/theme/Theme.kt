package com.wapp.wearmusic.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

private val BrandBlue = Color(0xFF89F0FF)
private val BrandBlueDim = Color(0xFF58CFE2)
private val BrandBlueContainer = Color(0xFF17343A)
private val BrandBlueOnContainer = Color(0xFFD8F8FF)

@Composable
fun WearMusicTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseColorScheme = remember(context) {
        dynamicColorScheme(context)
    } ?: MaterialTheme.colorScheme

    val colorScheme = remember(baseColorScheme) {
        baseColorScheme.copy(
            primary = BrandBlue,
            primaryDim = BrandBlueDim,
            primaryContainer = BrandBlueContainer,
            onPrimary = Color.Black,
            onPrimaryContainer = BrandBlueOnContainer,
            secondary = BrandBlue,
            secondaryDim = BrandBlueDim,
            secondaryContainer = BrandBlueContainer,
            onSecondary = Color.Black,
            onSecondaryContainer = BrandBlueOnContainer,
            tertiary = BrandBlueDim,
            tertiaryDim = BrandBlueDim,
            tertiaryContainer = BrandBlueContainer,
            onTertiary = Color.Black,
            onTertiaryContainer = BrandBlueOnContainer
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

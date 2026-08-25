package com.tino.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TinoLightColors = lightColorScheme(
    primary = TinoGreen,
    onPrimary = TinoSurface,
    primaryContainer = TinoGreenLight,
    onPrimaryContainer = TinoGreenDark,
    inversePrimary = TinoGreenBright,
    secondary = TinoOrange,
    onSecondary = TinoInk,
    background = TinoPaper,
    onBackground = TinoInk,
    surface = TinoSurface,
    onSurface = TinoInk,
    surfaceVariant = TinoSurfaceVariant,
    onSurfaceVariant = TinoMuted,
    outline = TinoBorder,
    error = TinoRed,
)

@Composable
fun TinoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TinoLightColors,
        typography = TinoTypography,
        content = content,
    )
}

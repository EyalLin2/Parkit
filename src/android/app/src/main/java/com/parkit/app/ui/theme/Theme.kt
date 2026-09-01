package com.parkit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ParkItColors.AccentLight,
    onPrimary = ParkItColors.SurfaceLight,
    secondaryContainer = ParkItColors.SurfaceTintLight,
    background = ParkItColors.BgLight,
    surface = ParkItColors.SurfaceLight,
    onBackground = ParkItColors.TextLight,
    onSurface = ParkItColors.TextLight,
    onSurfaceVariant = ParkItColors.TextMutedLight,
    outline = ParkItColors.BorderLight,
)

private val DarkColors = darkColorScheme(
    primary = ParkItColors.AccentDark,
    onPrimary = ParkItColors.BgDark,
    secondaryContainer = ParkItColors.SurfaceTintDark,
    background = ParkItColors.BgDark,
    surface = ParkItColors.SurfaceDark,
    onBackground = ParkItColors.TextDark,
    onSurface = ParkItColors.TextDark,
    onSurfaceVariant = ParkItColors.TextMutedDark,
    outline = ParkItColors.BorderDark,
)

@Composable
fun ParkItTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = ParkItTypography, content = content)
}

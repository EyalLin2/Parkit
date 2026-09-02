package com.parkit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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

// A single corner-radius scale so cards/sheets/chips read as one system
// instead of every screen picking its own RoundedCornerShape value.
val ParkItShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ParkItTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = ParkItTypography, shapes = ParkItShapes, content = content)
}

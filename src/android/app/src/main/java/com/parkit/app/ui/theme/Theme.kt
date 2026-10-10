package com.parkit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

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
    val isDark = isDarkThemeActive()
    val colors = if (isDark) DarkColors else LightColors

    // enableEdgeToEdge() already makes the status bar transparent, so our
    // own background shows through it (every screen already pads for it
    // with .statusBarsPadding()) — setting a status bar *color* here would
    // be redundant. What's NOT automatic is icon contrast: it was a static
    // light-icons value in themes.xml, fine while the app only ever
    // followed the system theme, but an explicit in-app override can now
    // diverge from the system, so it has to track isDark here instead.
    val activity = LocalContext.current as? Activity
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(colorScheme = colors, typography = ParkItTypography, shapes = ParkItShapes, content = content)
}

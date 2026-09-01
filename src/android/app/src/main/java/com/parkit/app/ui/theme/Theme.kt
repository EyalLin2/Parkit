package com.parkit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF1B4F91)
private val AccentDark = Color(0xFF6AA3E0)

private val LightColors = lightColorScheme(primary = Accent)
private val DarkColors = darkColorScheme(primary = AccentDark)

@Composable
fun ParkItTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

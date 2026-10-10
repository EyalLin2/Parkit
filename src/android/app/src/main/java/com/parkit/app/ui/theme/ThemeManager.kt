package com.parkit.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

/**
 * Explicit light/dark override, independent of the device's system theme —
 * same reasoning as LocaleManager for language: requested as its own choice,
 * not just "whatever the OS happens to be set to". Unlike language, this
 * doesn't need an Activity recreate to take effect — a simple observable
 * in-memory value (backed by SharedPreferences for cold start) is enough,
 * since Compose just recomposes when it changes.
 */
object ThemeManager {
    private const val PREFS = "parkit_theme_prefs"
    private const val KEY = "mode" // "light" | "dark" | null = follow system

    private val overrideState = mutableStateOf<String?>(null)

    fun init(context: Context) {
        overrideState.value = prefs(context).getString(KEY, null)
    }

    fun getOverride(): State<String?> = overrideState

    fun setOverride(context: Context, mode: String?) {
        prefs(context).edit().putString(KEY, mode).apply()
        overrideState.value = mode
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The single source of truth both ParkItTheme (colors) and the map screen
 * (tile source) read, so an explicit "Dark" choice consistently darkens
 * everything instead of just the chrome around a still-light map. */
@Composable
fun isDarkThemeActive(): Boolean {
    val override by ThemeManager.getOverride()
    return when (override) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
}

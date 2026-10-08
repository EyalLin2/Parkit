package com.parkit.app.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Manual per-app language override.
 *
 * Tried the "correct" modern way first — androidx.appcompat's
 * AppCompatDelegate.setApplicationLocales(), which on API 33+ is supposed
 * to delegate to the platform's own per-app language system. Verified live
 * on the emulator (API 37) that it silently no-ops: the call returns
 * normally, getApplicationLocales() reflects it, but the Activity's actual
 * Configuration never changes and strings stay in the original language.
 * Not worth chasing further on what may be very-new/unreleased platform
 * behavior — this manual Context-wrapping approach is deterministic and
 * debuggable, and is itself proven: wrapping worked (Configuration.locale
 * really did change), but resource *resolution* initially still picked
 * English, because `Locale("he")` is aliased internally by java.util.Locale
 * to the legacy ISO code "iw" (a decades-old Hebrew/Yiddish/Indonesian
 * compatibility quirk: he->iw, yi->ji, id->in) — so AssetManager was
 * matching against "iw", not "he". Fixed by shipping values-iw/ as a
 * duplicate of values-he/ alongside it, rather than fighting the alias.
 */
object LocaleManager {
    private const val PREFS = "parkit_locale_prefs"
    private const val KEY_LANGUAGE = "language"

    fun getLanguage(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)

    fun setLanguage(context: Context, languageTag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, languageTag).apply()
    }

    /** Called from Activity.attachBaseContext so every resource lookup
     * (strings, plurals, RTL layout direction) resolves against the
     * override from the very first frame, not just after a later
     * recomposition. */
    fun wrap(base: Context): Context {
        val lang = getLanguage(base) ?: return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

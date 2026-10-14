package com.parkit.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** The public terms and privacy pages. Google Play requires the privacy
 * policy to be reachable from inside the app, not only from the store page,
 * and users must see the terms (no offensive content) before posting. */
object LegalLinks {
    const val PRIVACY = "https://eyallin2.github.io/Parkit/privacy.html"
    const val TERMS = "https://eyallin2.github.io/Parkit/terms.html"

    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser at all — nothing sensible to do.
        }
    }
}

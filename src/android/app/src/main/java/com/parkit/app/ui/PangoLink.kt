package com.parkit.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hand-off to Pango for paying blue-white street parking. No API or deal
 * involved: we just open their app (or its store page). The package must
 * also be listed under <queries> in the manifest, or Android 11+ hides it. */
object PangoLink {
    private const val PACKAGE = "com.unicell.pangoandroid"

    fun open(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")))
        }
    }
}

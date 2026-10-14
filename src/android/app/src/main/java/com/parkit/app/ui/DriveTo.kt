package com.parkit.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Turn-by-turn driving to a spot or car park: Waze first (what most Israeli
 * drivers use), then Google Maps navigation, then any app that takes a geo:
 * link. A plain geo: link only drops a pin, which isn't "I'm on my way". */
object DriveTo {
    private const val WAZE = "com.waze"
    private const val GOOGLE_MAPS = "com.google.android.apps.maps"

    fun open(context: Context, lat: Double, lng: Double) {
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")).setPackage(WAZE),
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=d")).setPackage(GOOGLE_MAPS),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // try the next app
            }
        }
    }
}

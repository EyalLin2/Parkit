package com.parkit.app.ui

import android.content.Context
import com.parkit.app.R
import com.parkit.app.api.Preferences
import com.parkit.app.api.SpotOut

/** How a spot relates to this driver's car and permits. The map never hides
 * a report because of these — it orders and labels them, so a driver can
 * still judge for themselves (and the rules on the sign always win). */
data class DriverFit(
    /** A disabled bay and the driver declared no disabled permit. */
    val needsDisabledPermit: Boolean,
    /** The reporter said it fits a smaller car than this driver's. */
    val tooSmall: Boolean,
    /** Zone number — only for drivers who hold a resident permit (most
     * drivers in a city aren't its residents; for them zones are noise). */
    val zoneLabel: String?,
    /** ...and it's the driver's own zone. */
    val inMyZone: Boolean,
) {
    /** Sorted after everything usable. */
    val unusable: Boolean get() = needsDisabledPermit || tooSmall
}

private fun sizeRank(size: String?): Int? = when (size) {
    "compact" -> 0
    "regular" -> 1
    "large" -> 2
    else -> null
}

fun fitOf(spot: SpotOut, prefs: Preferences): DriverFit {
    val spotRank = sizeRank(spot.vehicleSize)
    val carRank = sizeRank(prefs.vehicleSize)
    return DriverFit(
        needsDisabledPermit = spot.spotType == "disabled" && !prefs.hasDisabledPermit,
        tooSmall = spotRank != null && carRank != null && spotRank < carRank,
        zoneLabel = spot.permitZone?.takeIf { prefs.residentZone != null }?.let { zoneNumber(it) },
        inMyZone = spot.permitZone != null && spot.permitZone == prefs.residentZone,
    )
}

/** "tel-aviv:9" → "9". Keys are city-slug:zone so they stay stable and
 * language-neutral; the app words them itself. */
fun zoneNumber(key: String): String = key.substringAfter(':')

/** "Tel Aviv-Yafo · Zone 9" in the app's language. */
fun zoneDisplay(context: Context, key: String, serverCity: String? = null): String {
    val city = when (key.substringBefore(':')) {
        "tel-aviv" -> context.getString(R.string.city_tel_aviv)
        else -> serverCity ?: key.substringBefore(':')
    }
    return context.getString(R.string.zone_display, city, zoneNumber(key))
}

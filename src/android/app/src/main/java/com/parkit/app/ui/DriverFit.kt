package com.parkit.app.ui

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
    )
}


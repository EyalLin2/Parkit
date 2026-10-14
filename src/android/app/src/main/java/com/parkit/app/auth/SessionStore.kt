package com.parkit.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.parkit.app.api.Preferences

/** What the map needs from the driver profile, cached on the device so it
 * applies on the very first frame. [homeLat]/[homeLng] are the preferred
 * city's centre — the map opens there when GPS can't place the user. */
data class DriverPrefs(
    val preferences: Preferences = Preferences(),
    val homeLat: Double? = null,
    val homeLng: Double? = null,
)

/**
 * Minimal token/user-id persistence. A demo-scale app doesn't need
 * DataStore/encrypted prefs — this mirrors the backend's own dev-login
 * stand-in, not production auth storage.
 */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("parkit_session", Context.MODE_PRIVATE)

    val token = mutableStateOf(prefs.getString(KEY_TOKEN, null))
    val userId = mutableStateOf(prefs.getString(KEY_USER_ID, null))
    val displayName = mutableStateOf(prefs.getString(KEY_DISPLAY_NAME, null))

    val driver = mutableStateOf(loadDriver())

    /** Whether the post-login "your car" step was already shown (saved or
     * skipped) on this device. */
    var onboardingSeen: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, value).apply()

    /** Signed in but the name/avatar step isn't done yet — so closing the app
     * on that screen brings the driver back to it, not to a nameless map. */
    var needsProfile: Boolean
        get() = prefs.getBoolean(KEY_NEEDS_PROFILE, false)
        set(value) = prefs.edit().putBoolean(KEY_NEEDS_PROFILE, value).apply()

    /** The level the driver last saw, so reaching a new one can be celebrated once. */
    var lastSeenLevel: String?
        get() = prefs.getString(KEY_LAST_LEVEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_LEVEL, value).apply()

    /** Where the driver left the car ("I parked"), so the map can walk them
     * back to it and turn leaving into a one-tap report. Cleared on sign-out. */
    var parkedCar: ParkedCar?
        get() {
            if (!prefs.contains(KEY_CAR_AT)) return null
            return ParkedCar(
                lat = prefs.getString(KEY_CAR_LAT, null)?.toDoubleOrNull() ?: return null,
                lng = prefs.getString(KEY_CAR_LNG, null)?.toDoubleOrNull() ?: return null,
                at = prefs.getLong(KEY_CAR_AT, 0L),
                address = prefs.getString(KEY_CAR_ADDRESS, null),
                spotType = prefs.getString(KEY_CAR_TYPE, null) ?: "street",
            )
        }
        set(value) {
            val e = prefs.edit()
            if (value == null) {
                e.remove(KEY_CAR_LAT).remove(KEY_CAR_LNG).remove(KEY_CAR_AT).remove(KEY_CAR_ADDRESS).remove(KEY_CAR_TYPE)
            } else {
                e.putString(KEY_CAR_LAT, value.lat.toString()).putString(KEY_CAR_LNG, value.lng.toString())
                    .putLong(KEY_CAR_AT, value.at).putString(KEY_CAR_ADDRESS, value.address).putString(KEY_CAR_TYPE, value.spotType)
            }
            e.apply()
        }

    /** When the app last went to the background (0 = never / signed out). */
    var leftAppAt: Long
        get() = prefs.getLong(KEY_LEFT_APP_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LEFT_APP_AT, value).apply()

    /** Device-level flags that survive signing out (unlike the session). */
    private val device: SharedPreferences =
        context.applicationContext.getSharedPreferences("parkit_device", Context.MODE_PRIVATE)

    /** The 3-screen intro was shown on this install. */
    var introSeen: Boolean
        get() = device.getBoolean("intro_seen", false)
        set(value) = device.edit().putBoolean("intro_seen", value).apply()

    /** Where the phone last was (any successful fix), so the next launch opens
     * there instead of on a default spot while a fresh fix is on its way. */
    var lastFix: Pair<Double, Double>?
        get() = if (device.contains("last_lat")) {
            Pair(device.getFloat("last_lat", 0f).toDouble(), device.getFloat("last_lng", 0f).toDouble())
        } else {
            null
        }
        set(value) {
            val e = device.edit()
            if (value == null) e.remove("last_lat").remove("last_lng") else e.putFloat("last_lat", value.first.toFloat()).putFloat("last_lng", value.second.toFloat())
            e.apply()
        }

    /** A finish bought in the rewards shop that the car wears ("paint_gold"…), or null. */
    var carSpecial: String?
        get() = device.getString("car_special", null)
        set(value) = device.edit().apply { if (value == null) remove("car_special") else putString("car_special", value) }.apply()

    /** When the full-screen "on open" ad was last shown (epoch ms). */
    var lastOpenAdAt: Long
        get() = device.getLong("last_open_ad_at", 0L)
        set(value) = device.edit().putLong("last_open_ad_at", value).apply()

    /** "I just parked" was explained once on this install. */
    var carExplained: Boolean
        get() = device.getBoolean("car_explained", false)
        set(value) = device.edit().putBoolean("car_explained", value).apply()

    /** Paint the driver picked by hand for the car illustration (index into
     * CarPaints); -1 = use the colour from the vehicle registry. */
    var carPaint: Int
        get() = device.getInt("car_paint", -1)
        set(value) = device.edit().putInt("car_paint", value).apply()

    /** The post-sign-up "how ParkIt works" tips were shown on this install. */
    var tipsSeen: Boolean
        get() = device.getBoolean("tips_seen", false)
        set(value) = device.edit().putBoolean("tips_seen", value).apply()

    fun saveDriver(d: DriverPrefs) {
        val p = d.preferences
        prefs.edit()
            .putString(KEY_VEHICLE_SIZE, p.vehicleSize)
            .putString(KEY_CAR_LABEL, p.carLabel)
            .putString(KEY_CAR_PAINT, p.carPaint)
            .putBoolean(KEY_DISABLED_PERMIT, p.hasDisabledPermit)
            .putString(KEY_HOME_CITY, p.homeCity)
            .putString(KEY_HOME_LAT, d.homeLat?.toString())
            .putString(KEY_HOME_LNG, d.homeLng?.toString())
            .apply()
        driver.value = d
    }

    /** Server copy wins for the preferences; the city centre is kept only
     * while the city is unchanged (another device may have picked another). */
    fun syncDriver(server: Preferences) {
        val current = driver.value
        val keepCentre = current.preferences.homeCity == server.homeCity
        saveDriver(DriverPrefs(server, current.homeLat.takeIf { keepCentre }, current.homeLng.takeIf { keepCentre }))
    }

    private fun loadDriver() = DriverPrefs(
        Preferences(
            vehicleSize = prefs.getString(KEY_VEHICLE_SIZE, null),
            carLabel = prefs.getString(KEY_CAR_LABEL, null),
            carPaint = prefs.getString(KEY_CAR_PAINT, null),
            hasDisabledPermit = prefs.getBoolean(KEY_DISABLED_PERMIT, false),
            homeCity = prefs.getString(KEY_HOME_CITY, null),
        ),
        prefs.getString(KEY_HOME_LAT, null)?.toDoubleOrNull(),
        prefs.getString(KEY_HOME_LNG, null)?.toDoubleOrNull(),
    )

    fun save(token: String, userId: String, displayName: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
        this.token.value = token
        this.userId.value = userId
        this.displayName.value = displayName
    }

    fun clear() {
        prefs.edit().clear().apply()
        token.value = null
        userId.value = null
        displayName.value = null
        driver.value = DriverPrefs()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_NEEDS_PROFILE = "needs_profile"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_LEFT_APP_AT = "left_app_at"
        private const val KEY_CAR_LAT = "car_lat"
        private const val KEY_CAR_LNG = "car_lng"
        private const val KEY_CAR_AT = "car_at"
        private const val KEY_CAR_ADDRESS = "car_address"
        private const val KEY_CAR_TYPE = "car_spot_type"
        private const val KEY_LAST_LEVEL = "last_seen_level"
        private const val KEY_VEHICLE_SIZE = "vehicle_size"
        private const val KEY_CAR_LABEL = "car_label"
        private const val KEY_CAR_PAINT = "car_paint_hex"
        private const val KEY_DISABLED_PERMIT = "has_disabled_permit"
        private const val KEY_HOME_CITY = "home_city"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LNG = "home_lng"
    }
}

data class ParkedCar(val lat: Double, val lng: Double, val at: Long, val address: String?, val spotType: String = "street")

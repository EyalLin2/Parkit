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

    fun saveDriver(d: DriverPrefs) {
        val p = d.preferences
        prefs.edit()
            .putString(KEY_VEHICLE_SIZE, p.vehicleSize)
            .putString(KEY_CAR_LABEL, p.carLabel)
            .putBoolean(KEY_DISABLED_PERMIT, p.hasDisabledPermit)
            .putString(KEY_HOME_CITY, p.homeCity)
            .putString(KEY_RESIDENT_ZONE, p.residentZone)
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
            hasDisabledPermit = prefs.getBoolean(KEY_DISABLED_PERMIT, false),
            homeCity = prefs.getString(KEY_HOME_CITY, null),
            residentZone = prefs.getString(KEY_RESIDENT_ZONE, null),
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
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_VEHICLE_SIZE = "vehicle_size"
        private const val KEY_CAR_LABEL = "car_label"
        private const val KEY_DISABLED_PERMIT = "has_disabled_permit"
        private const val KEY_HOME_CITY = "home_city"
        private const val KEY_RESIDENT_ZONE = "resident_zone"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LNG = "home_lng"
    }
}

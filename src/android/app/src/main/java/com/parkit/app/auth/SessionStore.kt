package com.parkit.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

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
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}

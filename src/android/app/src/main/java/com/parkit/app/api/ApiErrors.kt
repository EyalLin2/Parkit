package com.parkit.app.api

import android.content.Context
import com.parkit.app.R
import retrofit2.HttpException
import java.io.IOException

/** The backend's JWTs expire after 24h with no refresh mechanism (it's the
 * dev-login stand-in, see routers/auth.py) — any authenticated call can
 * come back 401 once a token goes stale. Callers use this to tell "your
 * session expired" apart from a normal network/server error and route
 * back to login instead of showing a raw "HTTP 401 Unauthorized" string. */
fun Throwable.isUnauthorized(): Boolean = (this as? HttpException)?.code() == 401

/** A caught exception's own .message is Retrofit/OkHttp plumbing ("HTTP 409
 * Response.error()", a raw IOException string) — real, but meaningless to
 * someone looking for a parking spot. Map the handful of cases that can
 * actually happen to plain language instead of surfacing that straight to
 * the UI (this is how a literal "HTTP 409" string ended up on screen). */
fun Throwable.friendlyMessage(context: Context): String {
    val code = (this as? HttpException)?.code()
    return when {
        code == 404 -> context.getString(R.string.error_not_found)
        code == 409 -> context.getString(R.string.error_conflict)
        code != null && code >= 500 -> context.getString(R.string.error_server)
        this is IOException -> context.getString(R.string.error_network)
        else -> context.getString(R.string.error_generic)
    }
}

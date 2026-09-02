package com.parkit.app.api

import retrofit2.HttpException

/** The backend's JWTs expire after 24h with no refresh mechanism (it's the
 * dev-login stand-in, see routers/auth.py) — any authenticated call can
 * come back 401 once a token goes stale. Callers use this to tell "your
 * session expired" apart from a normal network/server error and route
 * back to login instead of showing a raw "HTTP 401 Unauthorized" string. */
fun Throwable.isUnauthorized(): Boolean = (this as? HttpException)?.code() == 401

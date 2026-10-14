package com.parkit.app.api

import android.content.Context
import com.parkit.app.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun Throwable.isUnauthorized(): Boolean = (this as? HttpException)?.code() == 401

/** Every error a user can see has a short code (shown as "PK-500" next to
 * the message) so a tester can report exactly what happened. The full
 * table of codes, causes and fixes is in docs/error-codes.md. */
enum class ParkItError(val code: String, val messageRes: Int) {
    Network("PK-100", R.string.error_network),
    Timeout("PK-101", R.string.error_timeout),
    BadRequest("PK-400", R.string.error_generic),
    SessionExpired("PK-401", R.string.error_session),
    Blocked("PK-403", R.string.error_blocked),
    NotFound("PK-404", R.string.error_not_found),
    Conflict("PK-409", R.string.error_conflict),
    PhotoTooLarge("PK-413", R.string.error_photo_large),
    Invalid("PK-422", R.string.error_invalid),
    TooMany("PK-429", R.string.error_too_many),
    Server("PK-500", R.string.error_server),
    Unavailable("PK-503", R.string.error_unavailable),
    Unexpected("PK-900", R.string.error_generic),
    ;

    companion object {
        fun of(t: Throwable): ParkItError = when (val http = (t as? HttpException)?.code()) {
            null -> when (t) {
                is SocketTimeoutException -> Timeout
                is IOException -> Network
                else -> Unexpected
            }
            400 -> BadRequest
            401 -> SessionExpired
            403 -> Blocked
            404 -> NotFound
            409 -> Conflict
            413 -> PhotoTooLarge
            422 -> Invalid
            429 -> TooMany
            503 -> Unavailable
            else -> if (http >= 500) Server else BadRequest
        }
    }
}

/** The last few errors this session (code + time), attached to feedback so
 * a tester doesn't have to remember what popped up. */
object RecentErrors {
    private const val MAX = 10
    private val items = ArrayDeque<String>()

    @Synchronized
    fun record(error: ParkItError) {
        items.addLast("${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} ${error.code}")
        while (items.size > MAX) items.removeFirst()
    }

    @Synchronized
    fun summary(): String = items.joinToString(", ")
}

/** Plain-language message plus the error code, e.g. "Check your connection
 * and try again. (PK-100)". Never Retrofit/OkHttp internals. */
fun Throwable.friendlyMessage(context: Context): String {
    val error = ParkItError.of(this)
    RecentErrors.record(error)
    return context.getString(R.string.error_with_code, context.getString(error.messageRes), error.code)
}

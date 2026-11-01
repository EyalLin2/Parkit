package com.parkit.app.remind

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parkit.app.MainActivity
import com.parkit.app.R
import com.parkit.app.api.ApiClient
import com.parkit.app.api.SpotCreate
import com.parkit.app.api.isOutsideServiceArea
import com.parkit.app.auth.ParkedCar
import com.parkit.app.auth.SessionStore
import com.parkit.app.ui.ParkingPay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** While the car is parked: a quiet notification, on the lock screen too, whose
 * "I'm leaving" button hands the spot on without opening the app. */
object ParkedNotification {
    private const val CHANNEL = "parked_car"
    private const val ID = 78
    private const val RESULT_TIMEOUT_MS = 60_000L

    fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // canPost checks it
    fun show(context: Context, car: ParkedCar, message: String? = null) {
        if (!canPost(context)) return
        val leave = PendingIntent.getBroadcast(
            context,
            ID,
            Intent(context, LeaveSpotReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.parked_notification_title))
            .setContentText(message ?: car.address)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.dock_leave), leave)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    @SuppressLint("MissingPermission") // canPost checks it
    fun finished(context: Context, message: String) {
        if (!canPost(context)) return
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.parked_notification_thanks))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    private fun builder(context: Context): NotificationCompat.Builder {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.parked_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_pin_center)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }
}

class LeaveSpotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Nothing on screen changes while this runs, so a second tap is likely.
        if (!running.compareAndSet(false, true)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A receiver starts in the phone's language, not the one chosen in the app.
                handOn(com.parkit.app.locale.LocaleManager.wrap(context.applicationContext))
            } finally {
                running.set(false)
                pending.finish()
            }
        }
    }

    private suspend fun handOn(context: Context) {
        val session = SessionStore(context)
        val car = session.parkedCar ?: return ParkedNotification.cancel(context)
        val fix = freshFix(context)
        try {
            ApiClient.create(session).reportSpot(
                SpotCreate(
                    lat = car.lat,
                    lng = car.lng,
                    spotType = car.spotType,
                    payment = if (ParkingPay.sessionApp(context) != null) "paid" else "free", // they were paying, so the next driver pays too
                    vehicleSize = session.driver.value.preferences.vehicleSize,
                    address = car.address,
                    reporterLat = fix?.latitude,
                    reporterLng = fix?.longitude,
                    reporterAccuracyM = fix?.takeIf { it.hasAccuracy() }?.accuracy,
                    handoff = true,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Outside the launch city there's nobody to hand it to: just end the parking.
            if (e.isOutsideServiceArea()) {
                val paying = ParkingPay.sessionApp(context) != null
                session.parkedCar = null
                ParkingReminder.cancel(context)
                ParkingPay.clearSession(context)
                if (paying) {
                    ParkedNotification.finished(context, context.getString(R.string.parked_notification_ended_stop_paying))
                } else {
                    ParkedNotification.cancel(context)
                }
                return
            }
            if (session.parkedCar == null) return // ended in the app meanwhile: nothing to offer again
            val problem = when ((e as? HttpException)?.code()) {
                401 -> R.string.parked_notification_signin
                422 -> R.string.parked_notification_far
                else -> R.string.parked_notification_failed
            }
            return ParkedNotification.show(context, car, context.getString(problem))
        }
        session.parkedCar = null
        ParkingReminder.cancel(context)
        val paying = ParkingPay.sessionApp(context) != null
        ParkingPay.clearSession(context)
        ParkedNotification.finished(
            context,
            context.getString(if (paying) R.string.parked_notification_stop_paying else R.string.parked_notification_done),
        )
    }

    /** Where the phone is now. The app isn't running, so the cached fix may be
     * from somewhere else entirely: too old counts as unknown, and the server
     * doesn't reject a report for an unknown position. */
    private suspend fun freshFix(context: Context): Location? =
        currentFix(context) ?: lastKnownFix(context)?.takeIf { System.currentTimeMillis() - it.time <= MAX_FIX_AGE_MS }

    @SuppressLint("MissingPermission") // checked first
    private suspend fun currentFix(context: Context): Location? {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return null
        return try {
            val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            val cancel = com.google.android.gms.tasks.CancellationTokenSource()
            kotlinx.coroutines.withTimeoutOrNull(FIX_TIMEOUT_MS) {
                kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                    cont.invokeOnCancellation { cancel.cancel() }
                    client.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) { } }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) { } }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null // no Google Play services on this phone
        }
    }

    private fun lastKnownFix(context: Context): Location? {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return null
        val locations = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            locations.getProviders(true).mapNotNull { locations.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 5_000L
        const val MAX_FIX_AGE_MS = 2 * 60_000L
        val running = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}

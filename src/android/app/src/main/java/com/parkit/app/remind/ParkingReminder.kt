package com.parkit.app.remind

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.parkit.app.MainActivity
import com.parkit.app.R
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * "Remind me 10 minutes before" — from what the sign reader understood
 * ("paid from 19:00"). One reminder at a time, kept on the phone only;
 * an inexact alarm is fine (a minute either way doesn't matter here and
 * needs no special permission).
 */
object ParkingReminder {
    private const val CHANNEL = "parking_reminders"
    private const val LEAD_MINUTES = 10L
    private val ISRAEL = ZoneId.of("Asia/Jerusalem")

    /** When today [hhmm] (Israel time) is — null if unreadable or already past. */
    fun changeTime(hhmm: String): ZonedDateTime? = runCatching {
        val at = ZonedDateTime.now(ISRAEL).with(LocalTime.parse(hhmm))
        at.takeIf { it.isAfter(ZonedDateTime.now(ISRAEL)) }
    }.getOrNull()

    /** Sets the reminder; returns when it will ring (epoch ms), or null. */
    fun schedule(context: Context, changeAt: ZonedDateTime, text: String): Long? {
        val ringAt = changeAt.minusMinutes(LEAD_MINUTES).toInstant().toEpochMilli()
            .coerceAtLeast(System.currentTimeMillis() + 30_000)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return null
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ringAt, pending(context, text))
        context.getSharedPreferences("parkit_device", Context.MODE_PRIVATE).edit()
            .putLong("reminder_change_at", changeAt.toInstant().toEpochMilli())
            .putString("reminder_text", text)
            .apply()
        return ringAt
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, ""))
        context.getSharedPreferences("parkit_device", Context.MODE_PRIVATE).edit()
            .remove("reminder_change_at").remove("reminder_text").apply()
    }

    /** The change the active reminder is about (epoch ms), if still ahead. */
    fun activeChangeAt(context: Context): Long? =
        context.getSharedPreferences("parkit_device", Context.MODE_PRIVATE).getLong("reminder_change_at", 0L)
            .takeIf { it > System.currentTimeMillis() }

    private fun pending(context: Context, text: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        42,
        Intent(context, ReminderReceiver::class.java).putExtra("text", text),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_pin_center)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(42, notification) // no permission: nothing to show
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ParkingReminder.notify(context, intent.getStringExtra("text").orEmpty())
    }
}

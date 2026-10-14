package com.parkit.app.remind

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parkit.app.MainActivity
import com.parkit.app.R

/**
 * "Did you just park / are you leaving?" without tracking anyone: the phone
 * connecting to (or leaving) the car's own Bluetooth is the signal. The
 * driver picks their car once; location is read only when they tap the
 * notification, with the app open.
 */
object CarBluetooth {
    private const val PREFS = "parkit_device"
    private const val KEY_ADDRESS = "car_bt_address"
    private const val KEY_NAME = "car_bt_name"
    private const val CHANNEL = "car_moments"
    const val EXTRA_ACTION = "parkit_action" // "park" | "leave"

    /** Set by a notification tap, consumed by the map once it's ready. */
    val pendingAction = mutableStateOf<String?>(null)

    fun canUse(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Devices paired with this phone (name, address) — the car is one of them. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<Pair<String, String>> {
        if (!canUse(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching { adapter.bondedDevices.map { (it.name ?: it.address) to it.address } }.getOrDefault(emptyList())
            .sortedBy { it.first.lowercase() }
    }

    fun chosenName(context: Context): String? = prefs(context).getString(KEY_NAME, null)

    fun choose(context: Context, name: String?, address: String?) {
        prefs(context).edit().apply {
            if (address == null) remove(KEY_ADDRESS).remove(KEY_NAME) else putString(KEY_ADDRESS, address).putString(KEY_NAME, name)
        }.apply()
    }

    internal fun isMyCar(context: Context, address: String?): Boolean =
        address != null && address == prefs(context).getString(KEY_ADDRESS, null)

    internal fun hasParkedCar(context: Context): Boolean =
        context.getSharedPreferences("parkit_session", Context.MODE_PRIVATE).contains("car_at")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun notify(context: Context, action: String, title: Int, text: Int) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.car_bt_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            action.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ACTION, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_pin_center)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60_000L) // stale after a while
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(77, n)
    }
}

/** The car's Bluetooth came or went (exempt from Android's implicit-broadcast limits). */
class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (!CarBluetooth.isMyCar(context, device?.address)) return
        when (intent.action) {
            // Got into the car while it's parked → probably leaving.
            BluetoothDevice.ACTION_ACL_CONNECTED -> if (CarBluetooth.hasParkedCar(context)) {
                CarBluetooth.notify(context, "leave", R.string.car_bt_leave_title, R.string.car_bt_leave_text)
            }
            // Left the car with nothing saved → probably just parked.
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (!CarBluetooth.hasParkedCar(context)) {
                CarBluetooth.notify(context, "park", R.string.car_bt_park_title, R.string.car_bt_park_text)
            }
        }
    }
}

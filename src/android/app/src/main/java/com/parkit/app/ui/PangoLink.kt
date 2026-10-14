package com.parkit.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.parkit.app.R

/** Hand-off to the apps Israelis pay blue-white street parking with. No API
 * or deal involved: we open their app (or its store page). Every package
 * must also be listed under <queries> in the manifest, or Android 11+ hides
 * it and we'd think it isn't installed. */
object ParkingPay {
    enum class App(val packageName: String, val label: Int) {
        Pango("com.unicell.pangoandroid", R.string.pay_pango),
        Cellopark("air.com.cellogroup.Cellopark", R.string.pay_cellopark),
    }

    private fun prefs(context: Context) = context.getSharedPreferences("parkit_device", Context.MODE_PRIVATE)

    /** The driver's default payment app (Settings), or null = ask each time. */
    fun preferred(context: Context): App? =
        prefs(context).getString("pay_app", null)?.let { name -> App.entries.firstOrNull { it.name == name } }

    fun setPreferred(context: Context, app: App?) {
        prefs(context).edit().apply { if (app == null) remove("pay_app") else putString("pay_app", app.name) }.apply()
    }

    /** One tap to pay: the default app, else the only one installed, else
     * [choose] (the app shows a picker). */
    fun pay(context: Context, choose: () -> Unit) {
        val app = preferred(context) ?: installed(context).singleOrNull()
        if (app != null) open(context, app) else choose()
    }

    fun installed(context: Context): List<App> =
        App.entries.filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }

    fun open(context: Context, app: App) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")))
        }
    }
}

/** Big tappable tiles, one per payment app — just the name. */
@androidx.compose.runtime.Composable
fun PayAppTiles(selected: ParkingPay.App?, onPick: (ParkingPay.App) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        ParkingPay.App.entries.forEach { app ->
            val chosen = app == selected
            androidx.compose.material3.Surface(
                onClick = { onPick(app) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = if (chosen) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                border = if (chosen) androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary) else null,
                modifier = androidx.compose.ui.Modifier.weight(1f),
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = androidx.compose.ui.Modifier.padding(vertical = 22.dp, horizontal = 8.dp),
                ) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(app.label),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}


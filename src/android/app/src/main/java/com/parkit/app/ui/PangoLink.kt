package com.parkit.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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

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

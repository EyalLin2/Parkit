package com.parkit.app.api

import android.content.Context
import android.os.Build
import java.io.File

/** Crash reports without a third-party SDK: an uncaught exception is written
 * to a file (the process is dying, there's no time to send it), and the next
 * launch posts it to /feedback as kind "crash" and deletes the file. */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    "Recent errors: ${RecentErrors.summary()}\n" +
                        "Thread: ${thread.name}\n" +
                        error.stackTraceToString().take(7000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    suspend fun sendPending(context: Context, api: ApiService) {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return
        val report = runCatching { file.readText() }.getOrNull().orEmpty()
        val sent = runCatching { api.sendFeedback(FeedbackIn("crash", report.ifBlank { "(empty)" }, appVersion(context), device())) }.isSuccess
        if (sent) file.delete() // otherwise try again next launch
    }

    fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrDefault("unknown")

    fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})".take(120)
}

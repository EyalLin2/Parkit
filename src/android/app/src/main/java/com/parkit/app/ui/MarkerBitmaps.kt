package com.parkit.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.time.Duration
import java.time.Instant
import kotlin.math.min

/** Small colored circular badges drawn at runtime — status color + a short
 * label (relative time for a single spot, a count for a cluster) — instead
 * of a static drawable, since the label content varies per marker. */
object MarkerBitmaps {
    private const val DIAMETER_PX = 84

    fun badge(colorHex: String, label: String): Bitmap {
        val bmp = Bitmap.createBitmap(DIAMETER_PX, DIAMETER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val center = DIAMETER_PX / 2f
        val radius = center - 4f

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawCircle(center, center, radius + 4f, ring)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex); style = Paint.Style.FILL }
        canvas.drawCircle(center, center, radius, fill)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (label.length > 2) 22f else 28f
        }
        val textY = center - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, center, textY, text)
        return bmp
    }

    fun clusterBadge(count: Int): Bitmap = badge("#1B4F91", count.toString())

    /** "2m" / "1h" / "3d" / "now" — compact enough to fit on a small pin. */
    fun relativeTimeShort(iso: String): String = try {
        val minutes = Duration.between(Instant.parse(iso), Instant.now()).toMinutes()
        when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h"
            else -> "${min(minutes / 1440, 99)}d"
        }
    } catch (_: Exception) {
        ""
    }

    /** "2 minutes ago" / "1 hour ago" — for the full callout card. */
    fun relativeTimeLong(iso: String): String = try {
        val minutes = Duration.between(Instant.parse(iso), Instant.now()).toMinutes()
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
            minutes < 1440 -> "${minutes / 60} hour${if (minutes / 60 == 1L) "" else "s"} ago"
            else -> "${minutes / 1440} day${if (minutes / 1440 == 1L) "" else "s"} ago"
        }
    } catch (_: Exception) {
        ""
    }
}

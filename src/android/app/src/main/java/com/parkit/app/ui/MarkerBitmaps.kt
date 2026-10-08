package com.parkit.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.parkit.app.R
import java.time.Duration
import java.time.Instant
import kotlin.math.min

/** Small colored circular badges drawn at runtime — status color + a short
 * label (relative time for a single spot, a count for a cluster) — instead
 * of a static drawable, since the label content varies per marker. */
object MarkerBitmaps {
    private const val DIAMETER_PX = 84

    // Padding around the circle so a soft drop-shadow has room to bleed into —
    // without it, pins read as flat stickers pasted on the map instead of
    // objects with depth (the "Google Maps pin" look).
    private const val PADDING_PX = 12
    private const val CANVAS_PX = DIAMETER_PX + PADDING_PX * 2

    fun badge(colorHex: String, label: String): Bitmap {
        val bmp = Bitmap.createBitmap(CANVAS_PX, CANVAS_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val center = CANVAS_PX / 2f
        val radius = DIAMETER_PX / 2f - 4f

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 3f, Color.argb(110, 0, 0, 0))
        }
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
    fun relativeTimeShort(context: Context, iso: String): String = try {
        val minutes = Duration.between(Instant.parse(iso), Instant.now()).toMinutes()
        when {
            minutes < 1 -> context.getString(R.string.time_now_short)
            minutes < 60 -> context.getString(R.string.time_minutes_short, minutes)
            minutes < 1440 -> context.getString(R.string.time_hours_short, minutes / 60)
            else -> context.getString(R.string.time_days_short, min(minutes / 1440, 99))
        }
    } catch (_: Exception) {
        ""
    }

    /** "2 minutes ago" / "1 hour ago" — for the full callout card. */
    fun relativeTimeLong(context: Context, iso: String): String = try {
        val minutes = Duration.between(Instant.parse(iso), Instant.now()).toMinutes()
        val res = context.resources
        when {
            minutes < 1 -> context.getString(R.string.time_now_long)
            minutes < 60 -> res.getQuantityString(R.plurals.time_minutes_long, minutes.toInt(), minutes.toInt())
            minutes < 1440 -> res.getQuantityString(R.plurals.time_hours_long, (minutes / 60).toInt(), minutes / 60)
            else -> res.getQuantityString(R.plurals.time_days_long, (minutes / 1440).toInt(), minutes / 1440)
        }
    } catch (_: Exception) {
        ""
    }
}

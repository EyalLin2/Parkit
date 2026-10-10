package com.parkit.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.parkit.app.R
import java.time.Duration
import java.time.Instant
import kotlin.math.min

/** Real teardrop map pins — a circular "head" (status color + a short
 * label) with a tapered tail pointing at the exact coordinate, drawn at
 * runtime so the label content can vary per marker. Flat colored circles
 * read as "generic app markers"; this is the one shape everyone already
 * reads as "a map pin" from Google Maps/Waze. */
object MarkerBitmaps {
    private const val HEAD_DIAMETER = 64f
    private const val HEAD_RADIUS = HEAD_DIAMETER / 2f
    private const val TAIL_LENGTH = 26f
    private const val SIDE_PADDING = 8f
    private const val BOTTOM_SHADOW_PADDING = 6f

    private const val CANVAS_WIDTH = HEAD_DIAMETER + SIDE_PADDING * 2
    private const val HEAD_CENTER_Y = SIDE_PADDING + HEAD_RADIUS
    private const val TIP_Y = HEAD_CENTER_Y + HEAD_RADIUS + TAIL_LENGTH
    private const val CANVAS_HEIGHT = TIP_Y + BOTTOM_SHADOW_PADDING

    /** The pin's tip — not its bounding-box center — is the actual
     * coordinate, so callers must anchor the marker here, not at (0.5, 0.5). */
    const val ANCHOR_X = 0.5f
    val ANCHOR_Y = TIP_Y / CANVAS_HEIGHT

    private fun pinPath(cx: Float): Path {
        val head = Path().apply { addCircle(cx, HEAD_CENTER_Y, HEAD_RADIUS, Path.Direction.CW) }
        val tailHalfWidth = HEAD_RADIUS * 0.52f
        val tailTop = HEAD_CENTER_Y + HEAD_RADIUS * 0.45f
        val tail = Path().apply {
            moveTo(cx - tailHalfWidth, tailTop)
            lineTo(cx, TIP_Y)
            lineTo(cx + tailHalfWidth, tailTop)
            close()
        }
        return Path().apply { op(head, tail, Path.Op.UNION) }
    }

    fun badge(colorHex: String, label: String): Bitmap {
        val w = CANVAS_WIDTH.toInt()
        val h = CANVAS_HEIGHT.toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = CANVAS_WIDTH / 2f
        val pin = pinPath(cx)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(6f, 0f, 3f, Color.argb(110, 0, 0, 0))
        }
        canvas.drawPath(pin, shadowPaint)

        val fillInset = 4f
        val headFill = Path().apply { addCircle(cx, HEAD_CENTER_Y, HEAD_RADIUS - fillInset, Path.Direction.CW) }
        val tailHalfWidth = (HEAD_RADIUS - fillInset) * 0.52f
        val tailTop = HEAD_CENTER_Y + (HEAD_RADIUS - fillInset) * 0.45f
        val tailFill = Path().apply {
            moveTo(cx - tailHalfWidth, tailTop)
            lineTo(cx, TIP_Y - fillInset * 1.4f)
            lineTo(cx + tailHalfWidth, tailTop)
            close()
        }
        val pinFill = Path().apply { op(headFill, tailFill, Path.Op.UNION) }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex); style = Paint.Style.FILL }
        canvas.drawPath(pinFill, fillPaint)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (label.length > 2) 18f else 22f
        }
        val textY = HEAD_CENTER_Y - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, cx, textY, text)
        return bmp
    }

    /** Clusters represent an area/count, not one exact coordinate — a plain
     * circle (center-anchored, not a tip-anchored pin) reads correctly for
     * that, same as how Google Maps/Waze draw cluster badges. */
    fun clusterBadge(count: Int): Bitmap {
        val diameter = 72
        val padding = 10
        val size = diameter + padding * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val center = size / 2f
        val radius = diameter / 2f - 4f

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 3f, Color.argb(110, 0, 0, 0))
        }
        canvas.drawCircle(center, center, radius + 4f, ring)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B4F91"); style = Paint.Style.FILL }
        canvas.drawCircle(center, center, radius, fill)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 24f
        }
        val textY = center - (text.descent() + text.ascent()) / 2f
        canvas.drawText(count.toString(), center, textY, text)
        return bmp
    }

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

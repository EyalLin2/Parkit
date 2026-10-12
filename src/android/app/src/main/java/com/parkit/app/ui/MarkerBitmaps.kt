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

/** Spot pins for the vector map, drawn at runtime so each one can carry
 * its own freshness label. Sized in dp (not raw px) — MapLibre reads the
 * bitmap's density as its pixel ratio, so a px-sized bitmap would render
 * at a different physical size on every screen.
 *
 * Shape follows the Google Maps/Pango parking-pin convention: a colored
 * head with a bold "P" (instantly reads as "parking", unlike a bare time
 * label), a short tail to the exact coordinate, and the age as a small
 * white tag on the head's shoulder — the one fact that decides whether a
 * crowdsourced spot is still worth driving to. */
object MarkerBitmaps {
    private const val HEAD_DP = 34f
    private const val TAIL_DP = 9f
    private const val BORDER_DP = 2.5f
    private const val PAD_DP = 4f
    private const val TAG_H_DP = 16f

    /** Shadow padding under the tip, in dp — callers offset the icon by
     * this so the tip (not the padded bitmap edge) sits on the coordinate. */
    const val TIP_PADDING_DP = PAD_DP

    fun pin(context: Context, colorHex: String, tag: String, disabled: Boolean = false): Bitmap {
        val d = context.resources.displayMetrics.density
        val head = HEAD_DP * d
        val r = head / 2f
        val pad = PAD_DP * d
        val tagH = TAG_H_DP * d

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#202124")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f * d
        }
        val tagW = if (tag.isEmpty()) 0f else tagPaint.measureText(tag) + 10f * d

        // Symmetric width so the head (and its tip) stay on the horizontal
        // center even though the tag hangs off one side.
        val w = (2 * (pad + maxOf(r, r * 0.35f + tagW))).toInt()
        val h = (pad + tagH * 0.35f + head + TAIL_DP * d + pad).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Head centered horizontally so the tip lands on the bitmap's
        // horizontal center (icon-anchor "bottom" relies on that).
        val cx = w / 2f
        val cy = pad + tagH * 0.35f + r
        val tipY = cy + r + TAIL_DP * d

        val outline = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            val tail = Path().apply {
                moveTo(cx - r * 0.45f, cy + r * 0.7f)
                lineTo(cx, tipY)
                lineTo(cx + r * 0.45f, cy + r * 0.7f)
                close()
            }
            op(tail, Path.Op.UNION)
        }
        canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(3f * d, 0f, 1.5f * d, Color.argb(90, 0, 0, 0))
        })

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex) }
        val inner = r - BORDER_DP * d
        canvas.drawCircle(cx, cy, inner, fill)
        canvas.drawPath(Path().apply {
            moveTo(cx - inner * 0.42f, cy + inner * 0.72f)
            lineTo(cx, tipY - BORDER_DP * d * 1.6f)
            lineTo(cx + inner * 0.42f, cy + inner * 0.72f)
            close()
        }, fill)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f * d
        }
        if (disabled) {
            // Disabled-parking spots carry the wheelchair symbol, not "P",
            // so they're recognisable on the map at a glance.
            val icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_pin_accessible)!!
            val half = (20f * d / 2f).toInt()
            icon.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            icon.draw(canvas)
        } else {
            canvas.drawText("P", cx, cy - (p.descent() + p.ascent()) / 2f, p)
        }

        if (tag.isNotEmpty()) {
            val left = cx + r * 0.35f
            val top = pad
            val rect = android.graphics.RectF(left, top, left + tagW, top + tagH)
            canvas.drawRoundRect(rect, tagH / 2f, tagH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(2f * d, 0f, 1f * d, Color.argb(70, 0, 0, 0))
            })
            canvas.drawText(tag, rect.centerX(), rect.centerY() - (tagPaint.descent() + tagPaint.ascent()) / 2f, tagPaint)
        }
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

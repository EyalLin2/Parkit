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
    private const val FRAME_DP = 3.5f // a level frame is a little thicker so its colour reads
    private const val PAD_DP = 4f
    private const val TAG_H_DP = 16f

    /** Shadow padding under the tip, in dp — callers offset the icon by
     * this so the tip (not the padded bitmap edge) sits on the coordinate. */
    const val TIP_PADDING_DP = PAD_DP

    /** [frameHex]: the reporter's level colour (bronze/silver/gold) as the
     * pin's frame instead of white — a recognition perk that leaves the
     * fill, and so the spot's meaning, untouched. */
    fun pin(context: Context, colorHex: String, tag: String, disabled: Boolean = false, hot: Boolean = false, frameHex: String? = null): Bitmap {
        val d = context.resources.displayMetrics.density
        val head = HEAD_DP * d
        val r = head / 2f
        val pad = PAD_DP * d
        val tagH = TAG_H_DP * d
        // Flames rise this far above the head (and lick out to the sides).
        val flame = if (hot) r * 0.9f else 0f
        val above = maxOf(tagH * 0.35f, flame)

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#202124")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f * d
        }
        val tagW = if (tag.isEmpty()) 0f else tagPaint.measureText(tag) + 10f * d

        // Symmetric width so the head (and its tip) stay on the horizontal
        // center even though the tag hangs off one side.
        val halfW = maxOf(if (hot) r * 1.55f else r, r * 0.35f + tagW)
        val w = (2 * (pad + halfW)).toInt()
        val h = (pad + above + head + TAIL_DP * d + pad).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Head centered horizontally so the tip lands on the bitmap's
        // horizontal center (icon-anchor "bottom" relies on that).
        val cx = w / 2f
        val cy = pad + above + r
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
        if (hot) drawFlames(canvas, cx, cy, r, flame)

        canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = frameHex?.let { Color.parseColor(it) } ?: Color.WHITE
            setShadowLayer(3f * d, 0f, 1.5f * d, Color.argb(90, 0, 0, 0))
        })

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex) }
        val inner = r - (if (frameHex != null) FRAME_DP else BORDER_DP) * d
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
            val top = cy - r - tagH * 0.35f
            val rect = android.graphics.RectF(left, top, left + tagW, top + tagH)
            canvas.drawRoundRect(rect, tagH / 2f, tagH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(2f * d, 0f, 1f * d, Color.argb(70, 0, 0, 0))
            })
            canvas.drawText(tag, rect.centerX(), rect.centerY() - (tagPaint.descent() + tagPaint.ascent()) / 2f, tagPaint)
        }
        return bmp
    }

    /** Fire behind the pin's head: three flame tongues (a tall one in the
     * middle, two shorter ones leaning out) shaded like a real flame —
     * yellow at the root, orange, a red tip — over a soft warm glow. Drawn
     * behind the pin so the pin sits *in* the fire. */
    private fun drawFlames(canvas: Canvas, cx: Float, cy: Float, r: Float, height: Float) {
        canvas.drawCircle(cx, cy - r * 0.2f, r * 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                cx, cy - r * 0.2f, r * 1.5f,
                intArrayOf(Color.argb(140, 255, 140, 0), Color.argb(0, 255, 140, 0)),
                floatArrayOf(0.5f, 1f), android.graphics.Shader.TileMode.CLAMP,
            )
        })
        // (lean in degrees, relative height, relative width)
        val tongues = listOf(Triple(-30f, 0.72f, 0.78f), Triple(30f, 0.72f, 0.78f), Triple(0f, 1f, 1f))
        for ((lean, tall, wide) in tongues) {
            val baseY = cy - r * 0.35f
            val tipY = cy - r - height * tall
            val half = r * 0.62f * wide
            canvas.save()
            canvas.rotate(lean, cx, cy)
            val flame = Path().apply {
                moveTo(cx - half, baseY)
                // Left flank bulges out, then pulls into a tip that curls to the right.
                cubicTo(cx - half * 1.15f, baseY - (baseY - tipY) * 0.55f, cx - half * 0.1f, tipY + (baseY - tipY) * 0.25f, cx + half * 0.12f, tipY)
                cubicTo(cx + half * 0.2f, tipY + (baseY - tipY) * 0.35f, cx + half * 1.15f, baseY - (baseY - tipY) * 0.4f, cx + half, baseY)
                close()
            }
            canvas.drawPath(flame, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(
                    cx, baseY, cx, tipY,
                    intArrayOf(Color.parseColor("#FFD54F"), Color.parseColor("#FF9800"), Color.parseColor("#F4511E"), Color.parseColor("#D84315")),
                    floatArrayOf(0f, 0.35f, 0.75f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            })
            // Bright inner core in the lower half of each tongue.
            canvas.scale(0.5f, 0.55f, cx, baseY)
            canvas.drawPath(flame, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 241, 168) })
            canvas.restore()
        }
    }

    /** Paid car park: a rounded square in slate — deliberately not the
     * round green pin, so it never reads as a free spot. */
    fun lotPin(context: Context): Bitmap {
        val d = context.resources.displayMetrics.density
        val side = 26f * d
        val pad = PAD_DP * d
        val size = (side + 2 * pad).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = android.graphics.RectF(pad, pad, pad + side, pad + side)
        canvas.drawRoundRect(rect, 7f * d, 7f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(2.5f * d, 0f, 1f * d, Color.argb(90, 0, 0, 0))
        })
        val inner = android.graphics.RectF(rect).apply { inset(2f * d, 2f * d) }
        canvas.drawRoundRect(inner, 5.5f * d, 5.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(LOT_COLOR) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f * d
        }
        canvas.drawText("P", rect.centerX(), rect.centerY() - (p.descent() + p.ascent()) / 2f, p)
        return bmp
    }

    const val LOT_COLOR = "#4A5B78"

    /** "Your car": a brand-blue disc with a car glyph — round, so it never
     * reads as one of the green "free spot" pins. */
    fun carPin(context: Context): Bitmap {
        val d = context.resources.displayMetrics.density
        val radius = 17f * d
        val pad = PAD_DP * d
        val size = (2 * (radius + pad)).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        canvas.drawCircle(c, c, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(3f * d, 0f, 1f * d, Color.argb(100, 0, 0, 0))
        })
        canvas.drawCircle(c, c, radius - 2.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B4F91") })
        val glyph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_car_marker)!!
        val g = (18f * d).toInt()
        glyph.setBounds((c - g / 2f).toInt(), (c - g / 2f).toInt(), (c + g / 2f).toInt(), (c + g / 2f).toInt())
        glyph.draw(canvas)
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

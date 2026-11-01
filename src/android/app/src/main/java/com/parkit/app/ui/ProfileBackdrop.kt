package com.parkit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Profile header backgrounds bought in the rewards shop ("bg_*"), drawn in
 * code so they stay sharp at any size. All are dark enough for white text;
 * null is the default navy. */
val ProfileBackdropKeys = listOf("bg_sea", "bg_sunset", "bg_desert", "bg_night")

@Composable
fun ProfileBackdrop(key: String?, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawBackdrop(key) }
}

private fun DrawScope.drawBackdrop(key: String?) {
    val w = size.width
    val h = size.height
    when (key) {
        "bg_sea" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B3D63), Color(0xFF1C6E9C))))
            drawCircle(Color(0xFFFFE08A).copy(alpha = 0.85f), h * 0.07f, Offset(w * 0.16f, h * 0.24f))
            // Three bands of water, lighter toward the shore.
            wave(h * 0.62f, Color(0xFF2A86B8).copy(alpha = 0.55f), h * 0.025f, 0f)
            wave(h * 0.70f, Color(0xFF3B9CCB).copy(alpha = 0.55f), h * 0.02f, w * 0.1f)
            wave(h * 0.78f, Color(0xFF5DB4DC).copy(alpha = 0.5f), h * 0.018f, w * 0.2f)
        }
        "bg_sunset" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1846), Color(0xFF7A2E5C), Color(0xFFC75A3A))))
            drawCircle(Color(0xFFFFB45C).copy(alpha = 0.9f), h * 0.11f, Offset(w * 0.22f, h * 0.62f))
            skyline(Color(0xFF2A1430).copy(alpha = 0.92f), windows = null)
        }
        "bg_desert" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF4A2414), Color(0xFF8E4A24), Color(0xFFC07A3A))))
            drawCircle(Color(0xFFFFD27A).copy(alpha = 0.8f), h * 0.08f, Offset(w * 0.8f, h * 0.22f))
            dune(h * 0.66f, Color(0xFF6E3518).copy(alpha = 0.8f), w * 0.35f)
            dune(h * 0.74f, Color(0xFF5A2A12).copy(alpha = 0.9f), w * 0.75f)
        }
        "bg_night" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF070B1E), Color(0xFF16213E))))
            // Fixed star field (no randomness: the same sky every time).
            val stars = listOf(0.08f to 0.12f, 0.2f to 0.3f, 0.33f to 0.08f, 0.47f to 0.22f, 0.6f to 0.1f, 0.71f to 0.28f, 0.86f to 0.14f, 0.93f to 0.34f, 0.4f to 0.4f, 0.14f to 0.46f)
            stars.forEach { (x, y) -> drawCircle(Color.White.copy(alpha = 0.75f), h * 0.007f, Offset(w * x, h * y)) }
            drawCircle(Color(0xFFF1F3F4), h * 0.06f, Offset(w * 0.82f, h * 0.2f))
            drawCircle(Color(0xFF070B1E), h * 0.055f, Offset(w * 0.845f, h * 0.185f))
            skyline(Color(0xFF0E1530), windows = Color(0xFFFFD27A))
        }
        else -> drawRect(Brush.verticalGradient(listOf(Color(0xFF14243A), Color(0xFF1B3556))))
    }
}

private fun DrawScope.wave(y: Float, color: Color, amp: Float, shift: Float) {
    val p = Path().apply {
        moveTo(0f, y)
        var x = -shift
        val step = size.width / 6f
        while (x < size.width + step) {
            quadraticBezierTo(x + step / 4f, y - amp, x + step / 2f, y)
            quadraticBezierTo(x + step * 3f / 4f, y + amp, x + step, y)
            x += step
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.dune(y: Float, color: Color, peakX: Float) {
    val p = Path().apply {
        moveTo(0f, y + size.height * 0.06f)
        quadraticBezierTo(peakX, y - size.height * 0.1f, size.width, y + size.height * 0.04f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(p, color)
}

/** A row of towers standing on the road line (the road covers their feet). */
private fun DrawScope.skyline(color: Color, windows: Color?) {
    val base = size.height * 0.8f
    val towers = listOf(
        0.00f to 0.30f, 0.06f to 0.42f, 0.13f to 0.26f, 0.19f to 0.5f, 0.25f to 0.34f,
        0.62f to 0.38f, 0.69f to 0.55f, 0.76f to 0.3f, 0.83f to 0.46f, 0.9f to 0.36f, 0.96f to 0.28f,
    )
    val tw = size.width * 0.06f
    towers.forEach { (x, hFrac) ->
        val top = base - size.height * hFrac * 0.75f
        drawRect(color, Offset(size.width * x, top), Size(tw, base - top))
        if (windows != null) {
            var wy = top + size.height * 0.04f
            var row = 0
            while (wy < base - size.height * 0.04f) {
                if ((row + (x * 100).toInt()) % 3 != 0) {
                    drawRect(windows.copy(alpha = 0.55f), Offset(size.width * x + tw * 0.25f, wy), Size(tw * 0.18f, size.height * 0.018f))
                    drawRect(windows.copy(alpha = 0.35f), Offset(size.width * x + tw * 0.6f, wy), Size(tw * 0.18f, size.height * 0.018f))
                }
                wy += size.height * 0.05f
                row++
            }
        }
    }
}

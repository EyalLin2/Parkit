package com.parkit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

/** Paint colours for "your car" — picked once in the profile, kept on the device. */
val CarPaints = listOf(
    Color(0xFF1B4F91), // ParkIt blue
    Color(0xFFD93025), // red
    Color(0xFFF2F2F2), // white
    Color(0xFF9AA0A6), // silver
    Color(0xFF2B2F36), // black
    Color(0xFF188038), // green
    Color(0xFFF9AB00), // yellow
    Color(0xFF7B3FC4), // purple
)

/**
 * A side-view illustration of the driver's car, drawn from its size:
 * compact = short hatchback, regular = sedan, large = tall SUV. With no car
 * set ([size] null) it draws a dashed outline — an invitation to add one.
 * Faces right; mirror it with Modifier.graphicsLayer for RTL if needed.
 */
@Composable
fun CarArt(size: String?, paint: Color, outline: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(2f)) {
        if (size == null) drawGhostCar(outline) else drawCar(size, paint)
    }
}

private fun DrawScope.bodyPath(kind: String): Path {
    val w = size.width
    val h = size.height
    // Proportions per body: where the roof starts/ends and how tall the cabin is.
    val (roofTop, roofFrom, roofTo, bonnet) = when (kind) {
        "compact" -> listOf(0.16f, 0.26f, 0.66f, 0.50f)
        "large" -> listOf(0.06f, 0.12f, 0.72f, 0.40f)
        else -> listOf(0.18f, 0.28f, 0.62f, 0.50f)
    }
    val sill = h * 0.78f
    return Path().apply {
        moveTo(w * 0.04f, sill)
        lineTo(w * 0.04f, h * (bonnet + 0.04f))
        quadraticBezierTo(w * 0.05f, h * bonnet, w * 0.12f, h * bonnet)
        lineTo(w * roofFrom, h * bonnet)
        lineTo(w * (roofFrom + 0.08f), h * roofTop)
        lineTo(w * (roofTo - 0.02f), h * roofTop)
        lineTo(w * (roofTo + 0.12f), h * bonnet)
        lineTo(w * 0.90f, h * (bonnet + 0.03f))
        quadraticBezierTo(w * 0.97f, h * (bonnet + 0.05f), w * 0.97f, h * (bonnet + 0.14f))
        lineTo(w * 0.97f, sill)
        close()
    }
}

private fun DrawScope.drawCar(kind: String, paint: Color) {
    val w = size.width
    val h = size.height
    val light = paint.luminance() > 0.7f
    // Ground shadow.
    drawOval(Color.Black.copy(alpha = 0.16f), topLeft = Offset(w * 0.06f, h * 0.84f), size = Size(w * 0.88f, h * 0.1f))
    val body = bodyPath(kind)
    drawPath(body, Brush.verticalGradient(listOf(lerp(paint, Color.White, 0.25f), paint, lerp(paint, Color.Black, 0.25f))))
    if (light) drawPath(body, Color(0xFF9AA0A6), style = Stroke(w * 0.012f))
    // Windows: one glass band under the roof, split by a pillar.
    val (top, from, to) = when (kind) {
        "compact" -> Triple(0.22f, 0.30f, 0.64f)
        "large" -> Triple(0.12f, 0.17f, 0.70f)
        else -> Triple(0.24f, 0.32f, 0.60f)
    }
    val glassBottom = h * (if (kind == "large") 0.38f else 0.47f)
    val glass = Path().apply {
        moveTo(w * from, glassBottom)
        lineTo(w * (from + 0.065f), h * top)
        lineTo(w * (to - 0.03f), h * top)
        lineTo(w * (to + 0.09f), glassBottom)
        close()
    }
    drawPath(glass, Brush.linearGradient(listOf(Color(0xFFBFD9F2), Color(0xFF6E8FB3)), Offset(0f, h * top), Offset(w, glassBottom)))
    val pillar = w * ((from + to) / 2f + 0.02f)
    drawLine(lerp(paint, Color.Black, 0.35f), Offset(pillar, h * top), Offset(pillar, glassBottom), strokeWidth = w * 0.022f)
    // Door line, lights.
    drawLine(lerp(paint, Color.Black, 0.3f), Offset(pillar, glassBottom), Offset(pillar, h * 0.74f), strokeWidth = w * 0.006f)
    drawRoundRect(Color(0xFFFFE082), Offset(w * 0.91f, h * 0.60f), Size(w * 0.05f, h * 0.06f), CornerRadius(w * 0.01f))
    drawRoundRect(Color(0xFFE53935), Offset(w * 0.04f, h * 0.58f), Size(w * 0.03f, h * 0.07f), CornerRadius(w * 0.01f))
    // Wheels.
    val r = h * (if (kind == "large") 0.17f else 0.15f)
    for (cx in listOf(w * 0.23f, w * 0.78f)) {
        val c = Offset(cx, h * 0.78f)
        drawCircle(Color(0xFF202124), r, c)
        drawCircle(Color(0xFFBDC1C6), r * 0.52f, c)
        drawCircle(Color(0xFF5F6368), r * 0.18f, c)
    }
}

private fun DrawScope.drawGhostCar(outline: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.03f, size.width * 0.02f))
    drawPath(bodyPath("regular"), outline, style = Stroke(size.width * 0.014f, pathEffect = dash))
    val r = size.height * 0.15f
    for (cx in listOf(size.width * 0.23f, size.width * 0.78f)) {
        drawCircle(outline, r, Offset(cx, size.height * 0.78f), style = Stroke(size.width * 0.014f, pathEffect = dash))
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

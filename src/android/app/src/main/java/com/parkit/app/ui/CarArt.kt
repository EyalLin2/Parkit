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
    Canvas(modifier.aspectRatio(1.7f)) {
        if (size == null) {
            drawGhostCar(outline)
        } else {
            drawRoad()
            drawCar(size, paint)
        }
    }
}

/** Silhouette numbers per body, as fractions of the canvas (front faces right). */
private class Shape(
    val left: Float, val right: Float, // bumpers
    val belt: Float, // bonnet / waist line height
    val roof: Float, // roof height
    val rearRoof: Float, val frontRoof: Float, // where the flat roof starts / ends
    val rearGlass: Float, val windscreen: Float, // bottom ends of the rear window / windscreen
    val trunk: Float?, // sedan boot lid height, null = hatch / SUV
    val wheelR: Float, val rearWheel: Float, val frontWheel: Float,
    val sill: Float,
)

private fun shapeOf(kind: String) = when (kind) {
    // Short city hatchback: rounded roof that drops almost straight at the back.
    "compact" -> Shape(0.14f, 0.86f, 0.53f, 0.25f, 0.24f, 0.56f, 0.15f, 0.70f, null, 0.135f, 0.29f, 0.71f, 0.79f)
    // Tall SUV: boxy, long flat roof, big wheels, high off the ground.
    "large" -> Shape(0.06f, 0.97f, 0.40f, 0.04f, 0.12f, 0.70f, 0.09f, 0.82f, null, 0.19f, 0.24f, 0.79f, 0.72f)
    // Sedan: bonnet, cabin, boot — three boxes.
    else -> Shape(0.03f, 0.97f, 0.52f, 0.20f, 0.33f, 0.62f, 0.24f, 0.76f, 0.47f, 0.15f, 0.22f, 0.79f, 0.78f)
}

private fun DrawScope.bodyPath(kind: String): Path {
    val w = size.width
    val h = size.height * CAR_H
    val k = shapeOf(kind)
    return Path().apply {
        moveTo(w * k.left, h * k.sill)
        if (k.trunk != null) {
            // Boot: rear bumper up to the lid, lid forward to the rear window.
            lineTo(w * k.left, h * (k.trunk + 0.05f))
            quadraticBezierTo(w * k.left, h * k.trunk, w * (k.left + 0.05f), h * k.trunk)
            lineTo(w * k.rearGlass, h * k.trunk)
        } else {
            lineTo(w * k.left, h * (k.roof + 0.10f))
            quadraticBezierTo(w * k.left, h * k.roof, w * (k.left + 0.06f), h * k.roof)
        }
        lineTo(w * k.rearRoof, h * k.roof)
        lineTo(w * k.frontRoof, h * k.roof)
        lineTo(w * k.windscreen, h * k.belt)
        lineTo(w * (k.right - 0.06f), h * (k.belt + 0.03f))
        quadraticBezierTo(w * k.right, h * (k.belt + 0.05f), w * k.right, h * (k.belt + 0.14f))
        lineTo(w * k.right, h * k.sill)
        close()
    }
}

private fun DrawScope.drawCar(kind: String, paint: Color) {
    val w = size.width
    val h = size.height * CAR_H
    val k = shapeOf(kind)
    val light = paint.luminance() > 0.7f
    drawOval(Color.Black.copy(alpha = 0.16f), topLeft = Offset(w * (k.left + 0.02f), h * 0.85f), size = Size(w * (k.right - k.left - 0.04f), h * 0.09f))
    val body = bodyPath(kind)
    drawPath(body, Brush.verticalGradient(listOf(lerp(paint, Color.White, 0.25f), paint, lerp(paint, Color.Black, 0.25f))))
    if (light) drawPath(body, Color(0xFF9AA0A6), style = Stroke(w * 0.012f))
    // Glass: follows the roof, one pillar in the middle.
    val inset = 0.03f
    val glassTop = h * (k.roof + 0.04f)
    val glassBottom = h * (k.belt - 0.03f)
    val glass = Path().apply {
        moveTo(w * (k.rearGlass + inset), glassBottom)
        lineTo(w * (k.rearRoof + inset * 0.5f), glassTop)
        lineTo(w * (k.frontRoof - inset * 0.5f), glassTop)
        lineTo(w * (k.windscreen - inset * 1.5f), glassBottom)
        close()
    }
    drawPath(glass, Brush.linearGradient(listOf(Color(0xFFBFD9F2), Color(0xFF6E8FB3)), Offset(0f, glassTop), Offset(w, glassBottom)))
    val pillar = w * ((k.rearRoof + k.frontRoof) / 2f + 0.02f)
    val trim = lerp(paint, Color.Black, 0.35f)
    drawLine(trim, Offset(pillar, glassTop), Offset(pillar, glassBottom), strokeWidth = w * 0.022f)
    drawLine(trim, Offset(pillar, glassBottom), Offset(pillar, h * (k.sill - 0.05f)), strokeWidth = w * 0.006f)
    if (kind == "large") {
        // Spare wheel on the tailgate.
        drawRoundRect(Color(0xFF202124), Offset(0f, h * 0.30f), Size(w * 0.075f, h * 0.30f), CornerRadius(w * 0.03f))
        drawRoundRect(Color(0xFF5F6368), Offset(w * 0.018f, h * 0.36f), Size(w * 0.04f, h * 0.18f), CornerRadius(w * 0.02f))
        // Roof rails.
        drawLine(Color(0xFF3C4043), Offset(w * (k.rearRoof + 0.02f), h * (k.roof - 0.025f)), Offset(w * (k.frontRoof - 0.04f), h * (k.roof - 0.025f)), strokeWidth = w * 0.014f)
    }
    drawRoundRect(Color(0xFFFFE082), Offset(w * (k.right - 0.06f), h * (k.belt + 0.08f)), Size(w * 0.05f, h * 0.06f), CornerRadius(w * 0.01f))
    drawRoundRect(Color(0xFFE53935), Offset(w * k.left, h * ((k.trunk ?: k.belt) + 0.07f)), Size(w * 0.03f, h * 0.07f), CornerRadius(w * 0.01f))
    val r = h * k.wheelR * 2f / 2f
    for (cx in listOf(w * k.rearWheel, w * k.frontWheel)) {
        val c = Offset(cx, h * k.sill)
        drawCircle(Color(0xFF202124), r * 1.02f, c)
        drawCircle(Color(0xFFBDC1C6), r * 0.52f, c)
        drawCircle(Color(0xFF5F6368), r * 0.18f, c)
    }
}

/** The car's share of the canvas height; the road takes the strip below. */
private const val CAR_H = 0.86f

/** A short stretch of asphalt with a dashed centre line. */
private fun DrawScope.drawRoad() {
    val top = size.height * (CAR_H - 0.09f)
    val roadH = size.height - top
    drawRoundRect(Color(0xFF3C4043), Offset(0f, top), Size(size.width, roadH), CornerRadius(roadH / 2))
    val dash = size.width * 0.09f
    var x = size.width * 0.06f
    while (x < size.width * 0.94f) {
        drawRect(Color(0xFFF1F3F4), Offset(x, top + roadH * 0.62f), Size(dash, roadH * 0.12f))
        x += dash * 1.9f
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

package com.parkit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.asAndroidBitmap
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
fun CarArt(size: String?, paint: Color, outline: Color, modifier: Modifier = Modifier, special: String? = null) {
    // Driving: the road slides by, the wheels turn and the body bobs a little.
    // Still when the phone has animations turned off.
    val context = androidx.compose.ui.platform.LocalContext.current
    val animate = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val phase = if (animate && size != null) {
        rememberInfiniteTransition(label = "drive").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "phase",
        ).value
    } else {
        0f
    }
    Canvas(modifier.aspectRatio(1.7f)) {
        if (size == null) {
            drawGhostCar(outline)
        } else {
            drawRoad(phase)
            val bob = kotlin.math.sin(phase * 2f * Math.PI.toFloat() * 2f) * this.size.height * 0.008f
            translate(top = bob) { drawCar(size, paint, phase, special) }
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

private fun DrawScope.drawCar(kind: String, paint: Color, phase: Float, special: String? = null) {
    val w = size.width
    val h = size.height * CAR_H
    val k = shapeOf(kind)
    val light = paint.luminance() > 0.7f
    drawOval(Color.Black.copy(alpha = 0.16f), topLeft = Offset(w * (k.left + 0.02f), h * 0.85f), size = Size(w * (k.right - k.left - 0.04f), h * 0.09f))
    val body = bodyPath(kind)
    when (special) {
        // Bought in the rewards shop: finishes no registry colour has.
        "paint_gold" -> drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFF1B8), Color(0xFFE2B33C), Color(0xFF9C7418), Color(0xFFE9C25A))))
        "paint_chrome" -> {
            drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFB8BEC6), Color(0xFF5F6670), Color(0xFFD9DEE3))))
            drawPath(body, Color(0xFF8A9097), style = Stroke(w * 0.01f))
        }
        "paint_galaxy" -> {
            drawPath(body, Brush.linearGradient(listOf(Color(0xFF1A0B3D), Color(0xFF4A1F8C), Color(0xFF0E2A6B)), Offset(0f, 0f), Offset(w, h)))
            // A scatter of stars, fixed so they don't twinkle around.
            clipPath(body) {
                val rnd = java.util.Random(7)
                repeat(26) {
                    drawCircle(Color.White.copy(alpha = 0.55f + rnd.nextFloat() * 0.45f), w * (0.004f + rnd.nextFloat() * 0.006f), Offset(w * rnd.nextFloat(), h * rnd.nextFloat()))
                }
            }
        }
        "paint_flag" -> {
            // White with two blue stripes, like the flag.
            drawPath(body, Color(0xFFF7F9FC))
            clipPath(body) {
                val k = shapeOf(kind)
                val band = h * 0.055f
                drawRect(Color(0xFF0038B8), Offset(0f, h * (k.belt + 0.04f)), Size(w, band))
                drawRect(Color(0xFF0038B8), Offset(0f, h * (k.sill - 0.10f)), Size(w, band))
            }
            drawPath(body, Color(0xFFB0B8C4), style = Stroke(w * 0.01f))
        }
        "paint_neon" -> {
            drawPath(body, Color(0xFF39FF14).copy(alpha = 0.35f), style = Stroke(w * 0.05f)) // glow
            drawPath(body, Brush.verticalGradient(listOf(Color(0xFFB6FF9E), Color(0xFF39FF14), Color(0xFF1FA80A))))
        }
        else -> {
            drawPath(body, Brush.verticalGradient(listOf(lerp(paint, Color.White, 0.25f), paint, lerp(paint, Color.Black, 0.25f))))
            if (light) drawPath(body, Color(0xFF9AA0A6), style = Stroke(w * 0.012f))
            if (special == "paint_flames") drawFlames(kind)
        }
    }
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
    // Trim follows the finish actually on the car, not the registry colour.
    val base = when (special) {
        "paint_gold" -> Color(0xFFE2B33C)
        "paint_chrome" -> Color(0xFF9AA0A6)
        "paint_neon" -> Color(0xFF39FF14)
        "paint_galaxy" -> Color(0xFF4A1F8C)
        "paint_flag" -> Color(0xFF0038B8)
        else -> paint
    }
    val trim = lerp(base, Color.Black, 0.35f)
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
        // Spokes, so turning wheels read as turning.
        rotate(phase * 360f, c) {
            for (a in 0 until 3) {
                rotate(a * 60f, c) { drawLine(Color(0xFF80868B), Offset(c.x - r * 0.48f, c.y), Offset(c.x + r * 0.48f, c.y), strokeWidth = r * 0.14f) }
            }
        }
        drawCircle(Color(0xFF5F6368), r * 0.18f, c)
    }
}

/** The car's share of the canvas height; the road takes the strip below. */
private const val CAR_H = 0.86f

/** A short stretch of asphalt with a dashed centre line. */
private fun DrawScope.drawRoad(phase: Float) {
    val top = size.height * (CAR_H - 0.09f)
    val roadH = size.height - top
    drawRoundRect(Color(0xFF3C4043), Offset(0f, top), Size(size.width, roadH), CornerRadius(roadH / 2))
    // The car faces right, so the markings slide left.
    val dash = size.width * 0.09f
    val step = dash * 1.9f
    clipRect(left = size.width * 0.05f, right = size.width * 0.95f) {
        var x = size.width * 0.05f - phase * step
        while (x < size.width) {
            drawRect(Color(0xFFF1F3F4), Offset(x, top + roadH * 0.62f), Size(dash, roadH * 0.12f))
            x += step
        }
    }
}

/** Hot-rod flames licking back from the front wheel along the lower body. */
private fun DrawScope.drawFlames(kind: String) {
    val w = size.width
    val h = size.height * CAR_H
    val k = shapeOf(kind)
    val base = h * (k.sill - 0.04f)
    val top = h * (k.belt + 0.06f)
    val start = w * (k.right - 0.10f)
    val tongues = 4
    val span = (start - w * (k.left + 0.12f)) / tongues
    for ((colour, shrink) in listOf(Color(0xFFFF5722) to 0f, Color(0xFFFFC107) to 0.35f)) {
        val path = Path().apply {
            moveTo(start, base)
            lineTo(start, top + (base - top) * shrink)
            for (i in 0 until tongues) {
                val x0 = start - span * i
                val tip = top + (base - top) * (shrink + 0.15f * i)
                quadraticBezierTo(x0 - span * 0.7f, tip - h * 0.02f, x0 - span, tip + (base - top) * 0.35f)
            }
            lineTo(start - span * tongues, base)
            close()
        }
        drawPath(path, colour)
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


/**
 * The driver's own car as a map marker: the same art as the profile, in a
 * white bubble with a pointer at the bottom, so "your car" on the map is
 * unmistakably yours. Drawn off-screen with CanvasDrawScope.
 */
fun carMarkerBitmap(context: android.content.Context, kind: String?, paint: Color, permit: Boolean, special: String? = null): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val w = (46 * density).toInt()
    val h = (38 * density).toInt()
    val image = androidx.compose.ui.graphics.ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(density),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        canvas,
        Size(w.toFloat(), h.toFloat()),
    ) {
        val bubbleH = h * 0.82f
        val corner = CornerRadius(bubbleH * 0.3f)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(2f * density, 2f * density), Size(w - 4f * density, bubbleH - 2f * density), corner)
        drawRoundRect(Color.White, Offset(0f, 0f), Size(w - 2f * density, bubbleH - 3f * density), corner)
        val tail = Path().apply {
            moveTo(w / 2f - 7f * density, bubbleH - 4f * density)
            lineTo(w / 2f, h.toFloat())
            lineTo(w / 2f + 7f * density, bubbleH - 4f * density)
            close()
        }
        drawPath(tail, Color.White)
        val pad = 3.5f * density
        translate(left = pad, top = pad) {
            val inner = Size(w - 2 * pad - 2f * density, bubbleH - 2 * pad - 3f * density)
            // Reuse the profile drawing inside the bubble's box.
            drawIntoBox(inner) { drawCar(kind ?: "regular", paint, 0f, special) }
        }
        if (permit) {
            val s = 9f * density
            drawRoundRect(Color(0xFF1565C0), Offset(w - s - 6f * density, 4f * density), Size(s, s), CornerRadius(2f * density))
            drawCircle(Color.White, s * 0.12f, Offset(w - s / 2 - 6f * density, 4f * density + s * 0.25f))
            drawArc(Color.White, 20f, 250f, false, Offset(w - s - 6f * density + s * 0.25f, 4f * density + s * 0.4f), Size(s * 0.5f, s * 0.5f), style = Stroke(s * 0.1f))
        }
    }
    return image.asAndroidBitmap()
}

/** Run [block] as if the canvas were [box] big (drawCar sizes itself from size). */
private fun DrawScope.drawIntoBox(box: Size, block: DrawScope.() -> Unit) {
    val sx = box.width / size.width
    val sy = box.height / size.height
    scale(sx, sy, pivot = Offset.Zero) { block() }
}


package com.parkit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ProfileOut
import kotlin.math.cos
import kotlin.math.sin

/** Milestone badges, earned from numbers the server already tracks — how
 * many spots you reported and how many of them a driver actually parked
 * in. No extra storage: a badge is just "count ≥ goal". */
internal enum class Badge(val goal: Int, val helped: Boolean, val color: Color, val icon: ImageVector, val nameRes: Int) {
    First(1, false, Color(0xFF12A594), Icons.Filled.Flag, R.string.badge_first),
    Reports10(10, false, Color(0xFFB0703C), Icons.Filled.LocalParking, R.string.badge_reports_10),
    Reports50(50, false, Color(0xFF7D8DA3), Icons.Filled.Stars, R.string.badge_reports_50),
    Reports100(100, false, Color(0xFFC99A0E), Icons.Filled.EmojiEvents, R.string.badge_reports_100),
    Helped10(10, true, Color(0xFFE5484D), Icons.Filled.VolunteerActivism, R.string.badge_helped_10),
    Helped50(50, true, Color(0xFF7B3FE4), Icons.Filled.Bolt, R.string.badge_helped_50),
}

@Composable
fun BadgesCard(p: ProfileOut) {
    val earned = Badge.entries.count { (if (it.helped) p.successfulReports else p.totalReports) >= it.goal }
    SectionCard {
        Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
            Text(
                stringResource(R.string.badges_progress, earned, Badge.entries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
            )
            Badge.entries.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { badge ->
                        val have = if (badge.helped) p.successfulReports else p.totalReports
                        BadgeCell(badge, have, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCell(badge: Badge, have: Int, modifier: Modifier) {
    val unlocked = have >= badge.goal
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(horizontal = 4.dp)) {
        BadgeArt(badge.color, badge.icon, unlocked, size = 72.dp)
        Text(
            stringResource(badge.nameRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (!unlocked) {
            Text(
                "${have.coerceAtMost(badge.goal)}/${badge.goal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The medal itself, drawn rather than shipped as images so every badge
 * shares one look: two ribbon tails, a hexagon with a light-to-dark
 * gradient, an inner bevel ring, a diagonal shine and the icon. Locked
 * badges are the same art in grey with a small lock. */
@Composable
private fun BadgeArt(color: Color, icon: ImageVector, unlocked: Boolean, size: Dp) {
    val base = if (unlocked) color else Color(0xFFB8BEC8)
    val light = lerp(base, Color.White, 0.35f)
    val dark = lerp(base, Color.Black, 0.25f)
    val ribbon = lerp(base, Color.Black, if (unlocked) 0.35f else 0.15f)
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val c = Offset(w / 2f, w * 0.44f)
            val r = w * 0.40f

            // Ribbon tails behind the medal.
            for (side in listOf(-1f, 1f)) {
                drawPath(
                    Path().apply {
                        moveTo(c.x + side * r * 0.15f, c.y + r * 0.4f)
                        lineTo(c.x + side * r * 0.75f, c.y + r * 1.25f)
                        lineTo(c.x + side * r * 0.5f, c.y + r * 1.12f)
                        lineTo(c.x + side * r * 0.35f, c.y + r * 1.32f)
                        lineTo(c.x + side * r * -0.15f, c.y + r * 0.5f)
                        close()
                    },
                    ribbon,
                )
            }

            fun hexagon(radius: Float) = Path().apply {
                for (i in 0 until 6) {
                    val a = Math.toRadians((60.0 * i) - 90.0)
                    val p = Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }

            val outer = hexagon(r)
            drawPath(outer, Brush.linearGradient(listOf(light, dark), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)))
            drawPath(hexagon(r * 0.8f), Color.White.copy(alpha = 0.45f), style = Stroke(width = w * 0.025f))
            // Diagonal shine across the top-left of the medal.
            clipPath(outer) {
                drawPath(
                    Path().apply {
                        moveTo(c.x - r, c.y - r * 0.15f)
                        lineTo(c.x - r * 0.1f, c.y - r)
                        lineTo(c.x + r * 0.15f, c.y - r)
                        lineTo(c.x - r, c.y + r * 0.15f)
                        close()
                    },
                    Color.White.copy(alpha = if (unlocked) 0.28f else 0.18f),
                )
            }
        }
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (unlocked) 1f else 0.85f),
            modifier = Modifier.padding(bottom = size * 0.12f).size(size * 0.36f),
        )
        if (!unlocked) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).size(size * 0.3f)
                    .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)
                    .background(Color(0xFF8A93A3), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.15f))
            }
        }
    }
}

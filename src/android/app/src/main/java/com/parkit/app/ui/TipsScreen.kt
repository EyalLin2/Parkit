package com.parkit.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import kotlinx.coroutines.launch

private data class Tip(val title: Int, val body: Int, val tint: Color, val art: @Composable () -> Unit)

/** "How to use ParkIt" — five short tips shown once after sign-up (and from
 * Settings any time). Each one draws the real thing it explains — the same
 * pin bitmaps and buttons the map uses — so it reads at a glance. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TipsScreen(onDone: () -> Unit) {
    val tips = listOf(
        Tip(R.string.tip_map_title, R.string.tip_map_body, Color(0xFF188038)) { MapArt() },
        Tip(R.string.tip_hot_title, R.string.tip_hot_body, Color(0xFFE8480C)) { HotArt() },
        Tip(R.string.tip_go_title, R.string.tip_go_body, Color(0xFF1B4F91)) { GoArt() },
        Tip(R.string.tip_report_title, R.string.tip_report_body, Color(0xFF1B4F91)) { ReportArt() },
        Tip(R.string.tip_close_title, R.string.tip_close_body, Color(0xFF7B3FE4)) { CloseArt() },
    )
    val pager = rememberPagerState(pageCount = { tips.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == tips.size - 1
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(48.dp)) {
            Text(stringResource(R.string.tips_header), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.driver_skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val tip = tips[page]
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .background(Brush.radialGradient(listOf(tip.tint.copy(alpha = 0.16f), Color.Transparent))),
                ) { tip.art() }
                Text(stringResource(tip.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(tip.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            repeat(tips.size) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                )
            }
        }
        Button(
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp).height(52.dp),
        ) {
            Text(stringResource(if (last) R.string.tips_done else R.string.intro_next), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A real map pin, drawn by the same code as the map. */
@Composable
private fun Pin(color: String, tag: String, hot: Boolean = false, frame: String? = null, scale: Float = 1.6f) {
    val context = LocalContext.current
    val bmp = remember(color, tag, hot, frame) { MarkerBitmaps.pin(context, color, tag, hot = hot, frameHex = frame).asImageBitmap() }
    val density = context.resources.displayMetrics.density
    Image(bmp, contentDescription = null, modifier = Modifier.size((bmp.width / density * scale).dp, (bmp.height / density * scale).dp))
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).width(96.dp))
}

@Composable
private fun MapArt() {
    // Equal thirds, so three pins with tags always fit side by side.
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        listOf(
            Triple("#0B5E2B", R.string.tip_tag_fresh, R.string.tip_cap_fresh),
            Triple("#7EC791", R.string.tip_tag_old, R.string.tip_cap_old),
            Triple("#E8710A", R.string.tip_tag_fresh, R.string.tip_cap_claimed),
        ).forEach { (color, tag, caption) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Pin(color, stringResource(tag), scale = 1.3f)
                Caption(stringResource(caption))
            }
        }
    }
}

@Composable
private fun HotArt() {
    Pin("#188038", stringResource(R.string.tip_tag_fresh), hot = true, scale = 2.2f)
}

@Composable
private fun GoArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Pin("#188038", stringResource(R.string.tip_tag_fresh))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp, modifier = Modifier.padding(top = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                Icon(Icons.Filled.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.spot_go), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ReportArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp)) {
                Box(Modifier.size(32.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(R.string.map_report_here), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.tip_photo_points), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun CloseArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF188038), modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.spot_i_took_it), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
            Box(Modifier.size(width = 1.dp, height = 22.dp).background(MaterialTheme.colorScheme.outline))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.DoNotDisturbOn, contentDescription = null, tint = Color(0xFFE8710A), modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.spot_flag_taken), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 22.dp)) {
            Pin("#188038", "", frame = "#B0703C", scale = 1.3f)
            Pin("#188038", "", frame = "#9AA7B8", scale = 1.3f)
            Pin("#188038", "", frame = "#D4A017", scale = 1.3f)
            Pin("#7B3FE4", "", scale = 1.3f)
        }
    }
}

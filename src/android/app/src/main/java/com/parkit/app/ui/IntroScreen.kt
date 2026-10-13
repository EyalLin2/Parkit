package com.parkit.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/** Three screens, once per install, before sign-in: what ParkIt is, how to
 * give back, and why it's worth it. Skippable at any point. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val pages = listOf(
        Triple(R.string.intro_find_title, R.string.intro_find_body, Color(0xFF188038)),
        Triple(R.string.intro_report_title, R.string.intro_report_body, Color(0xFF1B4F91)),
        Triple(R.string.intro_climb_title, R.string.intro_climb_body, Color(0xFFC99A0E)),
    )
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = onDone) { Text(stringResource(R.string.driver_skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val (title, body, color) = pages[page]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp).background(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)), CircleShape),
                ) { IntroArt(page) }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                )
            }
        }
        Button(
            onClick = { if (pager.currentPage == 2) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp),
        ) {
            Text(stringResource(if (pager.currentPage == 2) R.string.intro_start else R.string.intro_next), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The app's own pins and emblems as the illustrations, so the intro shows
 * exactly what the user will meet on the map. */
@Composable
private fun IntroArt(page: Int) {
    val context = LocalContext.current
    when (page) {
        0 -> Row(verticalAlignment = Alignment.Bottom) {
            val fresh = remember { MarkerBitmaps.pin(context, "#0B5E2B", "", hot = true).asImageBitmap() }
            val older = remember { MarkerBitmaps.pin(context, "#55B56E", "").asImageBitmap() }
            androidx.compose.foundation.Image(older, null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.Image(fresh, null, modifier = Modifier.size(104.dp))
        }
        1 -> Box(Modifier.size(112.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(52.dp))
        }
        else -> LevelEmblem(ParkItLevel.Gold, 120.dp)
    }
}

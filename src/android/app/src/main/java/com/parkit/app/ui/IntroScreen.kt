package com.parkit.app.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.locale.LocaleManager
import kotlinx.coroutines.launch

private data class Slide(val image: Int, val title: Int, val body: Int, val tint: Color)

private val SLIDES = listOf(
    Slide(R.drawable.intro_map, R.string.intro_find_title, R.string.intro_find_body, Color(0xFF188038)),
    Slide(R.drawable.intro_card, R.string.intro_go_title, R.string.intro_go_body, Color(0xFFE8480C)),
    Slide(R.drawable.intro_report, R.string.intro_report_title, R.string.intro_report_body, Color(0xFF1B4F91)),
    Slide(R.drawable.intro_profile, R.string.intro_climb_title, R.string.intro_climb_body, Color(0xFF7B3FE4)),
)

/** Before the first sign-in, once per install: pick a language, then four
 * slides that show the real app (screenshots in a phone frame, in the
 * chosen language) — find, go, report, climb. Skippable. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val pages = SLIDES.size + 1
    val pager = rememberPagerState(pageCount = { pages })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages - 1
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(8.dp).height(48.dp)) {
            if (pager.currentPage > 0) TextButton(onClick = onDone) { Text(stringResource(R.string.driver_skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) WelcomePage() else SlidePage(SLIDES[page - 1])
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            repeat(pages) { i ->
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
            Text(
                stringResource(
                    when {
                        pager.currentPage == 0 -> R.string.intro_how_it_works
                        last -> R.string.intro_start
                        else -> R.string.intro_next
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Logo, one line of what ParkIt is, and the language choice — so every
 * following screen (and the screenshots in it) is already in the right one. */
@Composable
private fun WelcomePage() {
    val context = LocalContext.current
    val current = (LocaleManager.getLanguage(context) ?: java.util.Locale.getDefault().language).let { if (it == "iw") "he" else it }
    fun choose(code: String) {
        if (code == current) return
        LocaleManager.setLanguage(context, code)
        (context as? Activity)?.recreate()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
    ) {
        Box(
            modifier = Modifier.size(132.dp).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = stringResource(R.string.logo_cd), modifier = Modifier.size(92.dp))
        }
        Text(
            "ParkIt",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = com.parkit.app.ui.theme.SuezOne),
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.intro_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.intro_choose_language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 36.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LanguageCard("עברית", "🇮🇱", selected = current == "he", modifier = Modifier.weight(1f)) { choose("he") }
            LanguageCard("English", "🇬🇧", selected = current != "he", modifier = Modifier.weight(1f)) { choose("en") }
        }
    }
}

@Composable
private fun LanguageCard(label: String, flag: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(flag, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** A real screenshot in a phone frame on a soft colour glow, then what it shows. */
@Composable
private fun SlidePage(slide: Slide) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(Brush.radialGradient(listOf(slide.tint.copy(alpha = 0.20f), Color.Transparent))),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(0.92f).aspectRatio(540f / 1122f, matchHeightConstraintsFirst = true)
                    .shadow(18.dp, RoundedCornerShape(30.dp))
                    .background(Color(0xFF1B1F27), RoundedCornerShape(30.dp))
                    .padding(6.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(25.dp)),
            ) {
                Image(
                    painterResource(slide.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(slide.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            stringResource(slide.body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

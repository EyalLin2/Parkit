@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.LeaderboardEntry
import com.parkit.app.api.ProfileOut
import com.parkit.app.api.isUnauthorized
import com.parkit.app.locale.LocaleManager

private val Gold = Color(0xFFC9971C)
private val Silver = Color(0xFF8C97A6)
private val Bronze = Color(0xFFB8703C)

private data class BadgeTier(val threshold: Int, val labelRes: Int, val icon: ImageVector, val color: Color)

// Same medal-color language as the leaderboard podium below, so a badge
// reads as "the bronze/silver/gold tier of the same system" instead of
// three identical blue circles that only differ by which icon is inside.
private val BADGE_TIERS = listOf(
    BadgeTier(10, R.string.profile_badge_rookie, Icons.Filled.MilitaryTech, Bronze),
    BadgeTier(50, R.string.profile_badge_pro, Icons.Filled.WorkspacePremium, Silver),
    BadgeTier(200, R.string.profile_badge_legend, Icons.Filled.EmojiEvents, Gold),
)

@Composable
fun ProfileScreen(api: ApiService, onBack: () -> Unit, onSessionExpired: () -> Unit) {
    var profile by remember { mutableStateOf<ProfileOut?>(null) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            profile = api.myProfile()
            leaderboard = api.leaderboard(10)
        } catch (e: Exception) {
            if (e.isUnauthorized()) onSessionExpired() else error = e.message
        }
    }

    val p = profile
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { HeroHeader(profile = p, onBack = onBack) }
            item { LanguageCard() }
            item { ShareCard() }

            error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }

            if (p == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                item { SecondaryStatsRow(p) }
                item { BadgesCard(p) }
                item { MyReportsCard(p.activity) }
            }

            item {
                Text(
                    stringResource(R.string.profile_leaderboard_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 22.dp, bottom = 10.dp),
                )
            }

            if (leaderboard.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.profile_leaderboard_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                item { PodiumRow(leaderboard.take(3)) }
                val rest = leaderboard.drop(3)
                if (rest.isNotEmpty()) {
                    item { RestOfLeaderboardCard(rest, modifier = Modifier.padding(top = 14.dp)) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** A branded cover block instead of a flat pill-header-plus-identity-card
 * pair — the avatar, name, and the single headline metric (Points) are the
 * star of the screen, not one row among three identical stat cards. */
@Composable
private fun HeroHeader(profile: ProfileOut?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            // Inset below the status bar, THEN paint the brand color — so the
            // status bar row itself stays on the plain app background and its
            // icons stay legible, instead of sitting on a dark navy band.
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .padding(bottom = 26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back_cd), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Box(
                modifier = Modifier.size(76.dp)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f), CircleShape)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val initial = profile?.displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(initial, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                profile?.displayName ?: "…",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                stringResource(R.string.profile_member),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
                Text(
                    (profile?.points ?: 0).toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(stringResource(R.string.profile_total_points), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
        }
    }
}

/** Explicit in-app language choice, independent of the device's system
 * language — requested directly ("יכול שיהיה בחירת שפה?"), not just
 * following whatever locale the phone happens to be set to. */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val current = LocaleManager.getLanguage(context) ?: java.util.Locale.getDefault().language
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.profile_language_label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            listOf("en" to stringResource(R.string.profile_language_english), "he" to stringResource(R.string.profile_language_hebrew)).forEach { (code, label) ->
                val selected = current == code
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            LocaleManager.setLanguage(context, code)
                            (context as? Activity)?.recreate()
                        }
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** A plain Android share-sheet hand-off — no fake store link (the app
 * isn't published anywhere), just the honest message a demo project can
 * make: the feature itself, not a URL that doesn't exist yet. */
@Composable
private fun ShareCard() {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.profile_share_title)
    val shareMessage = stringResource(R.string.profile_share_message)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(shareTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareMessage)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
                },
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.profile_share_button), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Two secondary numbers share ONE card (divided, not stacked as two
 * identical full-width cards) — demoting them visually under the hero's
 * Points number instead of giving every stat equal weight. */
@Composable
private fun SecondaryStatsRow(profile: ProfileOut) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatTile(
                Icons.Filled.CheckCircle,
                profile.weeklyPoints.toString(),
                stringResource(R.string.profile_this_week),
                Color(0xFF2C7A4B),
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.width(1.dp).height(56.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
            StatTile(
                Icons.Filled.MilitaryTech,
                profile.successfulReports.toString(),
                stringResource(R.string.profile_successful_reports),
                Color(0xFFB8631A),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BadgesCard(profile: ProfileOut) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.profile_badges_title), style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                BADGE_TIERS.forEach { tier ->
                    BadgeItem(tier, successfulReports = profile.successfulReports, unlocked = profile.badges.contains(tier.threshold))
                }
            }
            Text(
                pluralStringResource(R.plurals.profile_activity_count, profile.activity.size, profile.activity.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun BadgeItem(tier: BadgeTier, successfulReports: Int, unlocked: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(if (unlocked) tier.color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tier.icon,
                contentDescription = stringResource(tier.labelRes),
                tint = if (unlocked) tier.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            stringResource(tier.labelRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (unlocked) {
            Text(stringResource(R.string.profile_unlocked), style = MaterialTheme.typography.labelSmall, color = tier.color)
        } else {
            LinearProgressIndicator(
                progress = { (successfulReports.coerceAtMost(tier.threshold).toFloat() / tier.threshold) },
                modifier = Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).padding(top = 2.dp),
                color = tier.color,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
            )
            Text(
                "${successfulReports.coerceAtMost(tier.threshold)}/${tier.threshold}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** Your own report history — the data (ProfileOut.activity) was already
 * being fetched for the badge-progress count below it, just never shown
 * as an actual list. One card, thin dividers between rows, matching the
 * rest-of-leaderboard pattern rather than a card per report. */
@Composable
private fun MyReportsCard(activity: List<com.parkit.app.api.ActivityItem>) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                stringResource(R.string.profile_my_reports_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (activity.isEmpty()) {
                Text(
                    stringResource(R.string.profile_my_reports_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                activity.forEachIndexed { index, item ->
                    MyReportRow(item, context)
                    if (index != activity.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MyReportRow(item: com.parkit.app.api.ActivityItem, context: android.content.Context) {
    val (statusLabelRes, statusColor) = when {
        item.status == "active" -> R.string.report_status_active to MaterialTheme.colorScheme.primary
        item.status == "claimed" -> R.string.report_status_claimed to Color(0xFFB8631A)
        item.removedReason == "taken_confirmed" -> R.string.report_status_confirmed_taken to Color(0xFF2C7A4B)
        item.removedReason == "flagged_false" -> R.string.report_status_flagged_false to MaterialTheme.colorScheme.error
        else -> R.string.report_status_expired to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val typeLabel = if (item.spotType == "disabled") stringResource(R.string.spot_type_disabled) else stringResource(R.string.spot_type_regular)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(typeLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                MarkerBitmaps.relativeTimeLong(context, item.reportedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(statusLabelRes), style = MaterialTheme.typography.labelMedium, color = statusColor)
    }
}

@Composable
private fun Avatar(name: String, background: Color, size: Dp = 40.dp) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

/** An actual podium — 2nd/1st/3rd side by side on steps of different
 * heights, like Duolingo/Strava weekly leaderboards — instead of the #1
 * entry just being another list row with a different border color. This
 * is the "stacked identical feature cards" problem made most visible: a
 * leaderboard's whole point is relative standing, which a vertical list of
 * same-shaped rows doesn't communicate nearly as well as a podium does. */
@Composable
private fun PodiumRow(top3: List<LeaderboardEntry>) {
    val slots = listOf(top3.getOrNull(1), top3.getOrNull(0), top3.getOrNull(2))
    val stepColors = listOf(Silver, Gold, Bronze)
    val stepHeights = listOf(84.dp, 112.dp, 68.dp)
    val avatarSizes = listOf(52.dp, 64.dp, 48.dp)
    val ranks = listOf(2, 1, 3)

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        slots.forEachIndexed { i, entry ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                if (entry != null) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = stringResource(R.string.profile_rank_prefix, ranks[i]), tint = stepColors[i], modifier = Modifier.size(if (i == 1) 26.dp else 20.dp))
                    Avatar(entry.displayName, stepColors[i], size = avatarSizes[i])
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(stringResource(R.string.profile_points_suffix, entry.weeklyPoints), style = MaterialTheme.typography.labelSmall, color = stepColors[i])
                } else {
                    Spacer(Modifier.height(avatarSizes[i] + 46.dp))
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    color = stepColors[i].copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(stepHeights[i]),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("${ranks[i]}", style = MaterialTheme.typography.headlineSmall, color = stepColors[i])
                    }
                }
            }
        }
    }
}

/** Ranks 4+ as one shared card with thin dividers between rows, instead of
 * a separate full-chrome card per row — the long tail of a leaderboard
 * should read as a list, not as N repeated identical feature-cards. */
@Composable
private fun RestOfLeaderboardCard(entries: List<LeaderboardEntry>, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.profile_rank_prefix, entry.rank),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                    Avatar(entry.displayName, MaterialTheme.colorScheme.primary, size = 36.dp)
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Text(stringResource(R.string.profile_points_suffix, entry.weeklyPoints), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (index != entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

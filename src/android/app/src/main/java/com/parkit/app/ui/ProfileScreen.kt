package com.parkit.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ActivityItem
import com.parkit.app.api.ApiService
import com.parkit.app.api.LeaderboardEntry
import com.parkit.app.api.ProfileOut
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized

private val Gold = Color(0xFFC9971C)
private val Silver = Color(0xFF8C97A6)
private val Bronze = Color(0xFFB8703C)
private val Positive = Color(0xFF188038)

private data class BadgeTier(val threshold: Int, val labelRes: Int, val icon: ImageVector, val color: Color)

private val BADGE_TIERS = listOf(
    BadgeTier(10, R.string.profile_badge_rookie, Icons.Filled.MilitaryTech, Bronze),
    BadgeTier(50, R.string.profile_badge_pro, Icons.Filled.WorkspacePremium, Silver),
    BadgeTier(200, R.string.profile_badge_legend, Icons.Filled.EmojiEvents, Gold),
)

private const val REPORTS_PREVIEW = 4

/** Account screen, rebuilt on the Google-account / Pango pattern: a quiet
 * header (who you are), one hero card (where you stand + what's next), then
 * flat outlined sections. Settings no longer live here at all — they have
 * their own screen behind the gear, like every mainstream app. */
@Composable
fun ProfileScreen(api: ApiService, onBack: () -> Unit, onOpenSettings: () -> Unit, onSessionExpired: () -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<ProfileOut?>(null) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            profile = api.myProfile()
            leaderboard = api.leaderboard(10)
        } catch (e: Exception) {
            if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
        }
    }

    val p = profile
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        item {
            ScreenTopBar(
                title = stringResource(R.string.profile_title),
                onBack = onBack,
                action = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.profile_settings_cd))
                    }
                },
            )
        }

        error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) } }

        if (p == null) {
            if (error == null) {
                item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
        } else {
            item { IdentityHeader(p) }
            item { PointsCard(p) }
            item { StatsCard(p) }
            item { SectionTitle(stringResource(R.string.profile_achievements)) }
            item { AchievementsCard(p) }
            item { SectionTitle(stringResource(R.string.profile_my_reports_title)) }
            item { MyReportsCard(p.activity) }
            item { SectionTitle(stringResource(R.string.profile_leaderboard_title)) }
            item { LeaderboardCard(leaderboard, myUserId = p.userId) }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

@Composable
internal fun ScreenTopBar(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back_cd))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
        action()
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
    )
}

/** Flat card with a hairline border instead of a drop shadow — the
 * Material 3 / Google-account look. Stacked shadows were a big part of
 * the "amateur" feel: everything floated, so nothing stood out. */
@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) { content() }
}

private fun currentTier(p: ProfileOut): BadgeTier? = BADGE_TIERS.lastOrNull { p.successfulReports >= it.threshold }
private fun nextTier(p: ProfileOut): BadgeTier? = BADGE_TIERS.firstOrNull { p.successfulReports < it.threshold }

@Composable
private fun IdentityHeader(p: ProfileOut) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Avatar(p.displayName, MaterialTheme.colorScheme.primary, size = 64.dp, textColor = MaterialTheme.colorScheme.onPrimary)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(p.displayName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tier = currentTier(p)
            val tierColor = tier?.color ?: MaterialTheme.colorScheme.primary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp).background(tierColor.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Icon(tier?.icon ?: Icons.Filled.LocalParking, contentDescription = null, tint = tierColor, modifier = Modifier.size(14.dp))
                Text(
                    tier?.let { stringResource(it.labelRes) } ?: stringResource(R.string.profile_level_none),
                    style = MaterialTheme.typography.labelMedium,
                    color = tierColor,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** The one number that matters (points), what you earned this week, and —
 * the part that actually drives behaviour — exactly how far the next tier
 * is. Same idea as Waze's "x points to next level". */
@Composable
private fun PointsCard(p: ProfileOut) {
    val totalPointsLabel = stringResource(R.string.profile_total_points)
    // In dark mode the light-blue accent as a full card fill glares; a deep
    // navy keeps it the hero without being the brightest thing on screen.
    val dark = com.parkit.app.ui.theme.isDarkThemeActive()
    val cardColor = if (dark) Color(0xFF1E3A5F) else MaterialTheme.colorScheme.primary
    val onCard = Color.White
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "${p.points} $totalPointsLabel" },
                ) {
                    Text(totalPointsLabel, style = MaterialTheme.typography.labelLarge, color = onCard.copy(alpha = 0.8f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.points.toString(), style = MaterialTheme.typography.displaySmall, color = onCard)
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.padding(start = 6.dp).size(26.dp))
                    }
                }
                if (p.weeklyPoints > 0) {
                    Text(
                        stringResource(R.string.profile_week_delta, p.weeklyPoints),
                        style = MaterialTheme.typography.labelLarge,
                        color = onCard,
                        modifier = Modifier.background(onCard.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            val next = nextTier(p)
            val from = currentTier(p)?.threshold ?: 0
            Text(
                next?.let { stringResource(R.string.profile_level_progress, it.threshold - p.successfulReports, stringResource(it.labelRes)) }
                    ?: stringResource(R.string.profile_max_level),
                style = MaterialTheme.typography.bodyMedium,
                color = onCard.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            LinearProgressIndicator(
                progress = { next?.let { (p.successfulReports - from).toFloat() / (it.threshold - from) } ?: 1f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Gold,
                trackColor = onCard.copy(alpha = 0.2f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun StatsCard(p: ProfileOut) {
    SectionCard(modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat(p.successfulReports.toString(), stringResource(R.string.profile_successful_reports), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
            Stat(p.activity.size.toString(), stringResource(R.string.profile_total_reports), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
            Stat(p.weeklyPoints.toString(), stringResource(R.string.profile_this_week), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(vertical = 16.dp, horizontal = 4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AchievementsCard(p: ProfileOut) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            BADGE_TIERS.forEach { tier ->
                val unlocked = p.successfulReports >= tier.threshold || p.badges.contains(tier.threshold)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
                    Box(
                        modifier = Modifier.size(52.dp).background(
                            if (unlocked) tier.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (unlocked) tier.icon else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (unlocked) tier.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(if (unlocked) 28.dp else 20.dp),
                        )
                    }
                    Text(stringResource(tier.labelRes), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        if (unlocked) stringResource(R.string.profile_unlocked)
                        else "${p.successfulReports.coerceAtMost(tier.threshold)}/${tier.threshold}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unlocked) tier.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MyReportsCard(activity: List<ActivityItem>) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(modifier = Modifier.animateContentSize()) {
        Column {
            if (activity.isEmpty()) {
                Text(
                    stringResource(R.string.profile_my_reports_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                val shown = if (expanded) activity else activity.take(REPORTS_PREVIEW)
                shown.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.padding(start = 70.dp))
                    MyReportRow(item)
                }
                if (activity.size > REPORTS_PREVIEW) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    Text(
                        stringResource(if (expanded) R.string.profile_show_less else R.string.profile_see_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MyReportRow(item: ActivityItem) {
    val context = LocalContext.current
    val (statusLabelRes, statusColor) = when {
        item.status == "active" -> R.string.report_status_active to Positive
        item.status == "claimed" -> R.string.report_status_claimed to Color(0xFFE8710A)
        item.removedReason == "taken_confirmed" -> R.string.report_status_confirmed_taken to MaterialTheme.colorScheme.primary
        item.removedReason == "flagged_false" -> R.string.report_status_flagged_false to MaterialTheme.colorScheme.error
        else -> R.string.report_status_expired to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val disabled = item.spotType == "disabled"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (disabled) Icons.AutoMirrored.Filled.Accessible else Icons.Filled.LocalParking,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(stringResource(if (disabled) R.string.spot_type_disabled else R.string.spot_type_regular), style = MaterialTheme.typography.titleSmall)
            Text(MarkerBitmaps.relativeTimeLong(context, item.reportedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(statusLabelRes),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            modifier = Modifier.background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun Avatar(name: String?, background: Color, size: Dp = 40.dp, textColor: Color = Color.White) {
    Box(modifier = Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            initialOf(name),
            style = if (size >= 56.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = textColor,
        )
    }
}

/** A clean ranked list (Waze's own weekly-ranking pattern) with medal
 * colors for the top three and your own row highlighted — a podium looked
 * playful, but the question people actually ask is "where am I?". */
@Composable
private fun LeaderboardCard(entries: List<LeaderboardEntry>, myUserId: String) {
    SectionCard {
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.profile_leaderboard_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            return@SectionCard
        }
        Column {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                val isMe = entry.userId == myUserId
                val medal = when (entry.rank) { 1 -> Gold; 2 -> Silver; 3 -> Bronze; else -> null }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .background(if (isMe) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).background(medal?.copy(alpha = 0.18f) ?: Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            entry.rank.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = medal ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Avatar(entry.displayName, medal ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), size = 36.dp)
                    Text(
                        if (isMe) "${entry.displayName} · ${stringResource(R.string.profile_you)}" else entry.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Text(
                        stringResource(R.string.profile_points_suffix, entry.weeklyPoints),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

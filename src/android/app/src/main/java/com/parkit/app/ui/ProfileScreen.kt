package com.parkit.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
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



private const val REPORTS_PREVIEW = 4

/** Account screen, rebuilt on the Google-account / Pango pattern: a quiet
 * header (who you are), one hero card (where you stand + what's next), then
 * flat outlined sections. Settings no longer live here at all — they have
 * their own screen behind the gear, like every mainstream app. */
@Composable
fun ProfileScreen(
    api: ApiService,
    sessionStore: com.parkit.app.auth.SessionStore,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDriver: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<ProfileOut?>(null) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            profile = api.myProfile().also { sessionStore.syncDriver(it.preferences) }
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
            item { LevelHeroCard(p, dark = com.parkit.app.ui.theme.isDarkThemeActive()) }
            item { StatsCard(p) }
            item { SectionTitle(stringResource(R.string.driver_title)) }
            item { DriverSummaryCard(sessionStore.driver.value.preferences, onOpenDriver) }
            item { SectionTitle(stringResource(R.string.levels_title)) }
            item { LevelLadder(points = p.points, current = ParkItLevel.of(p.level)) }
            item { SectionTitle(stringResource(R.string.profile_leaderboard_title)) }
            item {
                Text(
                    stringResource(R.string.league_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp).offset(y = (-6).dp),
                )
            }
            item { LeagueSection(leaderboard, myUserId = p.userId) }
            item { SectionTitle(stringResource(R.string.profile_my_reports_title)) }
            item { MyReportsCard(p.activity) }
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

/** One-glance summary of the car and permits; the whole card opens the editor. */
@Composable
private fun DriverSummaryCard(prefs: com.parkit.app.api.Preferences, onClick: () -> Unit) {
    val sizeRes = when (prefs.vehicleSize) {
        "compact" -> R.string.report_size_compact
        "large" -> R.string.report_size_large
        "regular" -> R.string.report_size_regular
        else -> null
    }
    val lines = listOfNotNull(
        prefs.carLabel ?: sizeRes?.let { stringResource(R.string.driver_summary_size, stringResource(it)) },
        stringResource(R.string.driver_disabled_permit).takeIf { prefs.hasDisabledPermit },
        prefs.homeCity,
    )
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                if (lines.isEmpty()) {
                    Text(stringResource(R.string.driver_summary_empty_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.driver_summary_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(lines.first(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (lines.size > 1) {
                        Text(lines.drop(1).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.driver_edit_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun IdentityHeader(p: ProfileOut) {
    val level = ParkItLevel.of(p.level)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier.size(72.dp).border(3.dp, level.color, CircleShape).padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(p.displayName, MaterialTheme.colorScheme.primary, size = 62.dp, textColor = MaterialTheme.colorScheme.onPrimary)
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(p.displayName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LevelChip(level, modifier = Modifier.padding(top = 4.dp))
        }
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


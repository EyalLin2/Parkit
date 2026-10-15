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
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationCity
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
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
import kotlinx.coroutines.launch

private val Gold = Color(0xFFC9971C)
private val Silver = Color(0xFF8C97A6)
private val Bronze = Color(0xFFB8703C)
private val Positive = Color(0xFF188038)



private const val REPORTS_PREVIEW = 4

/** Account screen, rebuilt on the Google-account / Pango pattern: a quiet
 * header (who you are), one hero card (where you stand + what's next), then
 * flat outlined sections. Settings no longer live here at all — they have
 * their own screen behind the gear, like every mainstream app. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    api: ApiService,
    sessionStore: com.parkit.app.auth.SessionStore,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDriver: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenRewards: () -> Unit = {},
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<ProfileOut?>(null) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var cityBoard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    val homeCity = sessionStore.driver.value.preferences.homeCity
    // League scope: your city first (a race you can actually win), or all of Israel.
    var cityScope by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    // Keyed on the city: it can arrive (or change) with the profile sync.
    LaunchedEffect(homeCity) {
        cityBoard = homeCity?.let { city -> runCatching { api.leaderboard(10, city = city) }.getOrDefault(emptyList()) } ?: emptyList()
    }
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
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var pickAvatar by remember { mutableStateOf(false) }
    var pickPaint by remember { mutableStateOf(false) }
    var carPaint by remember { mutableStateOf(sessionStore.carPaint) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
            item {
                ProfileHero(
                    p,
                    onChangeAvatar = { pickAvatar = true },
                    car = p.preferences,
                    paint = CarPaints.getOrNull(carPaint)
                        ?: p.preferences.carPaint?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                        ?: CarPaints[0],
                    onCarClick = { if (p.preferences.vehicleSize == null) onOpenDriver() else pickPaint = true },
                    special = sessionStore.carSpecial,
                    onOpenRewards = onOpenRewards,
                )
            }
            item { NextLevelCard(p, onReport = onBack) }
            // Three tabs instead of one endless scroll: what you have, how
            // you rank, what you reported. The tab bar sticks under the top.
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            stickyHeader { ProfileTabs(tab) { tab = it } }
            when (tab) {
                0 -> {
                    // Progress only — account details and the car live in Settings.
                    item { SectionTitle(stringResource(R.string.badges_title)) }
                    item { BadgesCard(p) }
                    item { SectionTitle(stringResource(R.string.levels_title)) }
                    item { LevelLadder(points = p.points, current = ParkItLevel.of(p.level)) }
                }
                1 -> {
                    item {
                        Text(
                            stringResource(R.string.league_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                        )
                    }
                    item {
                        LeagueScopeToggle(
                            cityLabel = homeCity ?: stringResource(R.string.league_my_city),
                            cityScope = cityScope,
                            onSelect = { cityScope = it },
                        )
                    }
                    if (cityScope && homeCity == null) {
                        item { PickCityCard(onOpenDriver) }
                    } else if (cityScope) {
                        item { LeagueSection(cityBoard, me = p, myRank = p.weeklyCityRank) }
                    } else {
                        item { LeagueSection(leaderboard, me = p, myRank = p.weeklyRank) }
                    }
                }
                else -> {
                    item { Spacer(Modifier.height(16.dp)) }
                    item { MyReportsCard(p.activity) }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
    if (pickAvatar) {
        AvatarPickerDialog(
            selected = profile?.avatar,
            onPick = { id ->
                profile = profile?.copy(avatar = id)
                scope.launch { runCatching { api.setAvatar(com.parkit.app.api.AvatarUpdate(id)) } }
            },
            onDismiss = { pickAvatar = false },
        )
    }
    if (pickPaint) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickPaint = false },
            title = { Text(stringResource(R.string.car_art_pick_paint)) },
            text = {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    CarPaints.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(c)
                                .border(if (i == carPaint) 3.dp else 1.dp, if (i == carPaint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    carPaint = i
                                    sessionStore.carPaint = i
                                    pickPaint = false
                                },
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { pickPaint = false; onOpenDriver() }) { Text(stringResource(R.string.car_art_edit)) }
            },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LeagueScopeToggle(cityLabel: String, cityScope: Boolean, onSelect: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        SegmentedButton(
            selected = cityScope,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(cityLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        SegmentedButton(
            selected = !cityScope,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(R.string.league_all_israel)) }
    }
}

/** No preferred city yet: the city league needs one — one tap to set it. */
@Composable
private fun PickCityCard(onOpenDriver: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onOpenDriver)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(R.string.league_pick_city_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.league_pick_city_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileTabs(selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf(R.string.profile_tab_progress, R.string.profile_tab_league, R.string.profile_tab_reports)
    androidx.compose.material3.TabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        titles.forEachIndexed { index, res ->
            androidx.compose.material3.Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = { Text(stringResource(res), style = MaterialTheme.typography.titleSmall) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** The top of "you": avatar in a ring that fills toward the next level
 * (tap to change it), name and level, then three numbers that say how
 * you're doing — total points, drivers you helped park, your place this
 * week. */
@Composable
private fun ProfileHero(
    p: ProfileOut,
    onChangeAvatar: () -> Unit,
    car: com.parkit.app.api.Preferences,
    paint: Color,
    onCarClick: () -> Unit,
    special: String? = null,
    onOpenRewards: () -> Unit = {},
) {
    val level = ParkItLevel.of(p.level)
    val progress = levelProgress(p)
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        // Avatar with "your car" parked beside it: a nudge to add the car.
        Box(contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(108.dp).clip(CircleShape).clickable(onClick = onChangeAvatar)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val tl = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, false, tl, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawArc(level.color, -90f, 360f * progress, false, tl, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Avatar(p.displayName, MaterialTheme.colorScheme.primary, size = 92.dp, textColor = MaterialTheme.colorScheme.onPrimary, avatarId = p.avatar)
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape).padding(3.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = stringResource(R.string.avatar_pick_title), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
            }
        }
        // Size shows at a glance: a small car is drawn small, an SUV big.
        val carW = when (car.vehicleSize) { "compact" -> 62.dp; "large" -> 90.dp; else -> 76.dp }
        val carX = 58.dp + carW / 2 // parked just beside the 108dp avatar ring
        CarArt(
            size = car.vehicleSize,
            paint = paint,
            special = special,
            outline = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = carX, y = 4.dp)
                .width(carW)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCarClick)
                .semantics { contentDescription = car.carLabel ?: "" },
        )
        // Disabled permit: the blue tag on the windscreen, like the real one.
        if (car.hasDisabledPermit && car.vehicleSize != null) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).offset(x = carX - 8.dp, y = -(carW / 1.7f) + 18.dp).size(15.dp)
                    .background(Color(0xFF1565C0), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.Accessible,
                    contentDescription = stringResource(R.string.driver_disabled_permit),
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        // "+" when there's no car yet, a pencil once there is: the paint is editable.
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).offset(x = carX + carW / 2, y = -(carW / 1.7f) + 26.dp).size(22.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(onClick = onCarClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (car.vehicleSize == null) androidx.compose.material.icons.Icons.Filled.Add else androidx.compose.material.icons.Icons.Filled.Edit,
                contentDescription = stringResource(if (car.vehicleSize == null) R.string.car_art_add else R.string.car_art_pick_paint),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(12.dp),
            )
        }
        }
        Text(isolate(p.displayName), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        LevelChip(level, modifier = Modifier.padding(top = 4.dp))
        val carLine = car.carLabel?.let { isolate(it) } ?: if (car.vehicleSize == null) stringResource(R.string.car_art_add) else null
        if (carLine != null) Text(
            carLine,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).clickable(onClick = onCarClick).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            HeroStat(p.points.toString(), stringResource(R.string.hero_points), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline))
            HeroStat(p.successfulReports.toString(), stringResource(R.string.hero_helped), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline))
            HeroStat(p.weeklyRank?.let { "#$it" } ?: "—", stringResource(R.string.hero_rank), Modifier.weight(1f))
        }
        // The rewards shop: what points are for.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFF9AB00), Color(0xFFE37400))))
                .clickable(onClick = onOpenRewards)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(androidx.compose.material.icons.Icons.Filled.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.rewards_entry, p.pointsBalance),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (p.successfulReports == 0) {
            // How "drivers helped" goes up — a zero shouldn't look like a dead number.
            Text(
                stringResource(R.string.hero_helped_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One compact card instead of a second hero: how far to the next level,
 * what it unlocks (its pin), and a one-tap way to earn points. */
@Composable
private fun NextLevelCard(p: ProfileOut, onReport: () -> Unit) {
    val level = ParkItLevel.of(p.level)
    val next = p.nextLevel?.let { ParkItLevel.of(it) }
    SectionCard(modifier = Modifier.padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (next == null || p.nextLevelPoints == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelEmblem(level, 36.dp)
                    Text(stringResource(R.string.level_max), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.level_to_next, p.nextLevelPoints - p.points, stringResource(next.nameRes)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(next.perkRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                LevelPinPreview(next, 40.dp)
            }
            LinearProgressIndicator(
                progress = { levelProgress(p) },
                color = next.color,
                trackColor = next.color.copy(alpha = 0.15f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            androidx.compose.material3.TextButton(onClick = onReport, modifier = Modifier.padding(top = 4.dp).align(Alignment.End)) {
                Icon(androidx.compose.material.icons.Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.profile_report_cta), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
internal fun Avatar(name: String?, background: Color, size: Dp = 40.dp, textColor: Color = Color.White, avatarId: String? = null) {
    avatarResource(avatarId)?.let { res ->
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(res),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
        return
    }
    Box(modifier = Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            initialOf(name),
            style = if (size >= 56.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = textColor,
        )
    }
}



/** Wraps a user-written name in Unicode first-strong isolates, so a Latin
 * name like "Noa L." keeps its punctuation in place inside Hebrew UI
 * (it used to render as ".Noa L"). */
internal fun isolate(text: String): String = "\u2068$text\u2069"

package com.parkit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.LeaderboardEntry
import com.parkit.app.api.ProfileOut

/** The reporter ladder, mirrored from the backend's `levels.py` (the
 * server decides the level and applies the perks; this only presents it).
 * Each level has its own colour so it reads the same everywhere it shows
 * up: the profile hero, the ladder, the league, the spot card, the pin. */
enum class ParkItLevel(
    val key: String,
    val minPoints: Int,
    val color: Color,
    val icon: ImageVector,
    val nameRes: Int,
    val perkRes: Int,
) {
    Newbie("newbie", 0, Color(0xFF7C8794), Icons.Filled.DirectionsCar, R.string.level_newbie, R.string.perk_newbie),
    Bronze("bronze", 100, Color(0xFFB0703C), Icons.Filled.MilitaryTech, R.string.level_bronze, R.string.perk_bronze),
    Silver("silver", 400, Color(0xFF7D8DA3), Icons.Filled.WorkspacePremium, R.string.level_silver, R.string.perk_silver),
    Gold("gold", 1200, Color(0xFFC99A0E), Icons.Filled.EmojiEvents, R.string.level_gold, R.string.perk_gold),
    Legend("legend", 3000, Color(0xFF7B3FE4), Icons.Filled.Diamond, R.string.level_legend, R.string.perk_legend),
    ;

    companion object {
        fun of(key: String?): ParkItLevel = entries.firstOrNull { it.key == key } ?: Newbie
    }
}

/** Round emblem: the level's icon on its colour, with a soft halo. */
@Composable
fun LevelEmblem(level: ParkItLevel, size: Dp, onColor: Boolean = false) {
    Box(
        modifier = Modifier.size(size)
            .background(if (onColor) Color.White.copy(alpha = 0.22f) else level.color.copy(alpha = 0.16f), CircleShape)
            .padding(size * 0.12f)
            .background(if (onColor) Color.White else level.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(level.icon, contentDescription = null, tint = if (onColor) level.color else Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

/** Small pill with the level name, in its colour. */
@Composable
fun LevelChip(level: ParkItLevel, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.background(level.color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(level.icon, contentDescription = null, tint = level.color, modifier = Modifier.size(13.dp))
        Text(
            stringResource(level.nameRes),
            style = MaterialTheme.typography.labelSmall,
            color = level.color,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** The profile's hero: painted in the current level's colour, so moving up
 * literally changes the screen. Shows points, progress to the next level
 * in points, and — the part that pulls people up — what that level unlocks. */
@Composable
fun LevelHeroCard(p: ProfileOut, dark: Boolean) {
    val level = ParkItLevel.of(p.level)
    val next = p.nextLevel?.let { ParkItLevel.of(it) }
    val base = if (dark) lerp(level.color, Color.Black, 0.35f) else level.color
    val brush = Brush.linearGradient(listOf(lerp(base, Color.White, 0.12f), lerp(base, Color.Black, 0.18f)))
    val levelName = stringResource(level.nameRes)

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)).background(brush),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelEmblem(level, 60.dp, onColor = true)
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        stringResource(R.string.level_title, levelName),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.level_points_total, p.points),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (p.weeklyPoints > 0) {
                    Text(
                        stringResource(R.string.profile_week_delta, p.weeklyPoints),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            if (next != null && p.nextLevelPoints != null) {
                val span = (p.nextLevelPoints - p.levelMinPoints).coerceAtLeast(1)
                val progress = ((p.points - p.levelMinPoints).toFloat() / span).coerceIn(0f, 1f)
                val nextName = stringResource(next.nameRes)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                    Text(
                        stringResource(R.string.level_to_next, p.nextLevelPoints - p.points, nextName),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    LevelEmblem(next, 26.dp, onColor = true)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeCap = StrokeCap.Round,
                )
                // What the next level is worth — the reason to climb.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)).padding(12.dp),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.level_next_perk, nextName, stringResource(next.perkRes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.level_max),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** All five levels as a ladder — reached ones ticked, yours highlighted,
 * the rest locked but visible, so people can see exactly what's ahead. */
@Composable
fun LevelLadder(points: Int, current: ParkItLevel) {
    SectionCard {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            ParkItLevel.entries.forEachIndexed { index, level ->
                val reached = points >= level.minPoints
                val isCurrent = level == current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .then(
                            if (isCurrent) Modifier.border(BorderStroke(1.5.dp, level.color), RoundedCornerShape(16.dp))
                                .background(level.color.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                            else Modifier,
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (reached) {
                            LevelEmblem(level, 38.dp)
                        } else {
                            Box(
                                modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(level.icon, contentDescription = null, tint = level.color.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(level.nameRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.level_threshold, level.minPoints),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            if (isCurrent) {
                                Text(
                                    stringResource(R.string.level_yours),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 8.dp).background(level.color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            stringResource(level.perkRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (reached) Icons.Filled.Check else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (reached) level.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (index < ParkItLevel.entries.lastIndex && !isCurrent && ParkItLevel.entries[index + 1] != current) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.padding(start = 68.dp, end = 16.dp))
                }
            }
            Text(
                stringResource(R.string.level_earn_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** Weekly league: a compact podium of the top three, slim ranked rows for
 * the rest, and — always — your own place pinned at the bottom, even when
 * you're outside the top ten or not ranked yet. */
@Composable
fun LeagueSection(entries: List<LeaderboardEntry>, me: ProfileOut) {
    if (entries.isNotEmpty()) {
        val podium = listOf(entries.getOrNull(1), entries.getOrNull(0), entries.getOrNull(2))
        val medal = listOf(Color(0xFF9AA5B4), Color(0xFFD4A017), Color(0xFFB0703C))
        SectionCard {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            ) {
                podium.forEachIndexed { i, entry ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                        if (entry != null) PodiumSpot(entry, medal[i], first = i == 1, isMe = entry.userId == me.userId)
                    }
                }
            }
        }
        val rest = entries.drop(3)
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Column {
                    rest.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), modifier = Modifier.padding(start = 92.dp))
                        LeagueRow(entry.rank.toString(), entry.displayName, ParkItLevel.of(entry.level), entry.weeklyPoints, isMe = entry.userId == me.userId)
                    }
                }
            }
        }
    } else {
        SectionCard {
            Text(
                stringResource(R.string.profile_leaderboard_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    // Your place — unless you're already visible in the list above.
    if (entries.none { it.userId == me.userId }) {
        Spacer(Modifier.height(10.dp))
        val level = ParkItLevel.of(me.level)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = level.color.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, level.color.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            if (me.weeklyRank != null) {
                LeagueRow(me.weeklyRank.toString(), me.displayName, level, me.weeklyPoints, isMe = true, highlight = false)
            } else {
                Text(
                    stringResource(R.string.league_not_ranked),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PodiumSpot(entry: LeaderboardEntry, medal: Color, first: Boolean, isMe: Boolean) {
    val level = ParkItLevel.of(entry.level)
    val ring = if (first) 64.dp else 52.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box {
            Box(
                modifier = Modifier.size(ring).border(2.5.dp, level.color, CircleShape).padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(entry.displayName, level.color, size = ring - 8.dp)
            }
            // Medal with the place number, on the avatar's shoulder.
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)
                    .background(medal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(entry.rank.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Text(
            if (isMe) stringResource(R.string.profile_you) else entry.displayName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.profile_points_suffix, entry.weeklyPoints),
            style = if (first) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = if (first) medal else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One slim league line: place · avatar · name with a small level mark · points. */
@Composable
private fun LeagueRow(rank: String, name: String, level: ParkItLevel, points: Int, isMe: Boolean, highlight: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(if (isMe && highlight) level.color.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            rank,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Box(
            modifier = Modifier.padding(start = 8.dp).size(40.dp).border(2.dp, level.color, CircleShape).padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(name, level.color, size = 34.dp)
        }
        Text(
            if (isMe) "$name · ${stringResource(R.string.profile_you)}" else name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
        )
        Icon(level.icon, contentDescription = stringResource(level.nameRes), tint = level.color, modifier = Modifier.padding(start = 6.dp).size(16.dp))
        Spacer(Modifier.weight(1f).width(8.dp))
        Text(
            stringResource(R.string.profile_points_suffix, points),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

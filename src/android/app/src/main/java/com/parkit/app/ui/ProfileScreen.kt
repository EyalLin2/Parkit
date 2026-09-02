@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.parkit.app.api.ApiService
import com.parkit.app.api.LeaderboardEntry
import com.parkit.app.api.ProfileOut
import com.parkit.app.api.isUnauthorized

private val Gold = Color(0xFFC9971C)
private val GoldTint = Color(0xFFFDF6E3)
private val Silver = Color(0xFF8C97A6)
private val SilverTint = Color(0xFFF3F4F6)
private val Bronze = Color(0xFFB8703C)
private val BronzeTint = Color(0xFFFAEEE3)

private data class BadgeTier(val threshold: Int, val label: String, val icon: ImageVector)
private val BADGE_TIERS = listOf(
    BadgeTier(10, "Rookie", Icons.Filled.MilitaryTech),
    BadgeTier(50, "Pro", Icons.Filled.WorkspacePremium),
    BadgeTier(200, "Legend", Icons.Filled.EmojiEvents),
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Leaderboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val p = profile
            if (p == null) {
                CircularProgressIndicator()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricRow(Icons.Filled.Star, p.points.toString(), "Points", MaterialTheme.colorScheme.primary)
                    MetricRow(Icons.AutoMirrored.Filled.TrendingUp, p.weeklyPoints.toString(), "This week", Color(0xFF2C7A4B))
                    MetricRow(Icons.Filled.CheckCircle, p.successfulReports.toString(), "Successful reports", Color(0xFFB8631A))
                }

                Text(
                    "Badges & Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BADGE_TIERS.forEach { tier ->
                        BadgeItem(tier, successfulReports = p.successfulReports, unlocked = p.badges.contains(tier.threshold))
                    }
                }

                Text(
                    "${p.activity.size} report(s) in your activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Text(
                "Weekly leaderboard",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(leaderboard) { row -> LeaderboardRow(row) }
                if (leaderboard.isEmpty()) {
                    item { Text("No one has weekly points yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** Compact horizontal metric row: a small colored icon badge, a big bold
 * number, and a muted label — a plain white card, not a blocky tint. */
@Composable
private fun MetricRow(icon: ImageVector, value: String, label: String, accent: Color) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 14.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
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
                .background(
                    if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tier.icon,
                contentDescription = "${tier.label} badge",
                tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            tier.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (unlocked) {
            Text("Unlocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else {
            LinearProgressIndicator(
                progress = { (successfulReports.coerceAtMost(tier.threshold).toFloat() / tier.threshold) },
                modifier = Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).padding(top = 2.dp),
                color = MaterialTheme.colorScheme.primary,
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

@Composable
private fun Avatar(name: String, background: Color, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape)
            .border(BorderStroke(2.dp, Color.White), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    if (entry.rank == 1) {
        PodiumCard(entry)
        return
    }

    val (medalColor, tint) = when (entry.rank) {
        2 -> Silver to SilverTint
        3 -> Bronze to BronzeTint
        else -> null to null
    }
    val avatarColor = when (entry.rank) {
        2 -> Silver
        3 -> Bronze
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = tint ?: MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (tint != null) 2.dp else 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(entry.displayName, avatarColor)
            Text(
                entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (medalColor != null) {
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = "Rank ${entry.rank}",
                    tint = medalColor,
                    modifier = Modifier.size(22.dp).padding(end = 6.dp),
                )
            } else {
                Text(
                    "#${entry.rank}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text("${entry.weeklyPoints} pts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The #1 spot gets its own dedicated treatment — bigger, gold-tinted, with
 * a soft glow (a colored shadow) instead of just another list row. */
@Composable
private fun PodiumCard(entry: LeaderboardEntry) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = GoldTint),
        border = BorderStroke(1.5.dp, Gold),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = MaterialTheme.shapes.large, ambientColor = Gold, spotColor = Gold),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(entry.displayName, Gold, size = 48.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(entry.displayName, style = MaterialTheme.typography.titleMedium)
                Text("Top parker this week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = "Rank 1", tint = Gold, modifier = Modifier.size(30.dp))
                Text("${entry.weeklyPoints} pts", style = MaterialTheme.typography.titleSmall, color = Gold)
            }
        }
    }
}

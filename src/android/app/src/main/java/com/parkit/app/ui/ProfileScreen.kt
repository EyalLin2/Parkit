@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.parkit.app.api.ApiService
import com.parkit.app.api.LeaderboardEntry
import com.parkit.app.api.ProfileOut

private val Gold = Color(0xFFC9971C)
private val GoldTint = Color(0xFFFBF1D6)
private val Silver = Color(0xFF8C97A6)
private val SilverTint = Color(0xFFF0F2F4)
private val Bronze = Color(0xFFB8703C)
private val BronzeTint = Color(0xFFF7E9DC)

private data class BadgeTier(val threshold: Int, val label: String, val icon: ImageVector)
private val BADGE_TIERS = listOf(
    BadgeTier(10, "Rookie", Icons.Filled.MilitaryTech),
    BadgeTier(50, "Pro", Icons.Filled.WorkspacePremium),
    BadgeTier(200, "Legend", Icons.Filled.EmojiEvents),
)

@Composable
fun ProfileScreen(api: ApiService, onBack: () -> Unit) {
    var profile by remember { mutableStateOf<ProfileOut?>(null) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            profile = api.myProfile()
            leaderboard = api.leaderboard(10)
        } catch (e: Exception) {
            error = e.message
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(Icons.Filled.Star, p.points.toString(), "Points", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricCard(Icons.AutoMirrored.Filled.TrendingUp, p.weeklyPoints.toString(), "This week", Color(0xFF2C7A4B), Modifier.weight(1f))
                    MetricCard(Icons.Filled.CheckCircle, p.successfulReports.toString(), "Reports", Color(0xFFB8631A), Modifier.weight(1f))
                }

                Text(
                    "Badges & Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    BADGE_TIERS.forEach { tier -> BadgeItem(tier, unlocked = p.badges.contains(tier.threshold)) }
                }

                Text(
                    "${p.activity.size} report(s) in your activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Text(
                "Weekly leaderboard",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            LazyColumn {
                items(leaderboard) { row -> LeaderboardRow(row) }
                if (leaderboard.isEmpty()) {
                    item { Text("No one has weekly points yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BadgeItem(tier: BadgeTier, unlocked: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
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
            modifier = Modifier.padding(top = 4.dp),
        )
        Text("${tier.threshold}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val (medalColor, tint) = when (entry.rank) {
        1 -> Gold to GoldTint
        2 -> Silver to SilverTint
        3 -> Bronze to BronzeTint
        else -> null to null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint ?: MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (tint != null) 2.dp else 0.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (medalColor != null) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = "Rank ${entry.rank}", tint = medalColor, modifier = Modifier.size(26.dp))
            } else {
                Text(
                    "#${entry.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(entry.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Text("${entry.weeklyPoints} pts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

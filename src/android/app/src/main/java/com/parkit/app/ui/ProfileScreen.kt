@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parkit.app.api.ApiService
import com.parkit.app.api.LeaderboardEntry
import com.parkit.app.api.ProfileOut

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
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let { Text(it) }
            val p = profile
            if (p == null) {
                CircularProgressIndicator()
            } else {
                Text("Points: ${p.points}  ·  Weekly: ${p.weeklyPoints}", style = MaterialTheme.typography.titleMedium)
                Text("Successful reports: ${p.successfulReports}")
                Text("Badges: ${if (p.badges.isEmpty()) "none yet" else p.badges.joinToString(", ")}")
                Text("Activity: ${p.activity.size} report(s)")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Weekly leaderboard", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(leaderboard) { row ->
                    Text("#${row.rank}  ${row.displayName}  —  ${row.weeklyPoints} pts", modifier = Modifier.padding(vertical = 4.dp))
                }
                if (leaderboard.isEmpty()) {
                    item { Text("No one has weekly points yet.") }
                }
            }
        }
    }
}

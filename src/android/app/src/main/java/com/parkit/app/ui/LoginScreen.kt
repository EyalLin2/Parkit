package com.parkit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.parkit.app.api.ApiService
import com.parkit.app.api.DevLoginRequest
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LoginScreen(api: ApiService, sessionStore: SessionStore, onLoggedIn: () -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ParkIt — Demo", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Backend-only project — this is a thin demo client, not the real native app.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.padding(top = 16.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        )
        Button(
            onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        val name = displayName.ifBlank { "Demo User" }
                        val externalId = "android-" + name.lowercase().replace(" ", "-") + "-" + UUID.randomUUID().toString().take(6)
                        val body = DevLoginRequest(externalId = externalId, displayName = name)
                        val result = api.devLogin(body)
                        sessionStore.save(result.accessToken, result.userId, name)
                        onLoggedIn()
                    } catch (e: Exception) {
                        error = "Login failed: ${e.message}. Is the backend running and reachable at 10.0.2.2:8000?"
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
        ) {
            Text("Dev login")
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
        }
        error?.let {
            Text(it, color = Color.Red, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

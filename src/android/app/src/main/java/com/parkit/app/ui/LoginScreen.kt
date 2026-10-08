package com.parkit.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.DevLoginRequest
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LoginScreen(api: ApiService, sessionStore: SessionStore, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.login_default_name)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = stringResource(R.string.logo_cd),
            modifier = Modifier.size(88.dp),
        )
        Text(
            "ParkIt",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.login_demo_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.login_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        )

        Button(
            onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        val name = displayName.ifBlank { defaultName }
                        val externalId = "android-" + name.lowercase().replace(" ", "-") + "-" + UUID.randomUUID().toString().take(6)
                        val result = api.devLogin(DevLoginRequest(externalId = externalId, displayName = name))
                        sessionStore.save(result.accessToken, result.userId, name)
                        onLoggedIn()
                    } catch (e: Exception) {
                        error = context.getString(R.string.login_error_backend, "10.0.2.2:8000", e.message)
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(R.string.login_continue), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }

        error?.let {
            Text(it, color = Color(0xFFB8631A), modifier = Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
        }
    }
}

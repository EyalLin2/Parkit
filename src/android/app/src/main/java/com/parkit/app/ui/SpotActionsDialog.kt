package com.parkit.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parkit.app.api.ApiService
import com.parkit.app.api.FeedbackCreate
import com.parkit.app.api.SpotOut
import kotlinx.coroutines.launch

@Composable
fun SpotActionsDialog(
    api: ApiService,
    spot: SpotOut,
    myUserId: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isMine = spot.reporterId == myUserId

    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
                onChanged()
            } catch (e: Exception) {
                error = e.message ?: "Action failed"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${spot.spotType} · ${spot.payment}") },
        text = {
            Column {
                Text("Status: ${spot.status}")
                if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }

                if (spot.status == "active" && !isMine) {
                    Button(
                        onClick = { run { api.claimSpot(spot.id) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Claim") }
                }
                if (!isMine) {
                    Button(
                        onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("confirmed_taken")) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("I took it") }
                    Button(
                        onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("flagged_false")) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Spot is taken (flag)") }
                }
                if (isMine && spot.status == "active") {
                    Button(
                        onClick = { run { api.cancelSpot(spot.id) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Cancel my report") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

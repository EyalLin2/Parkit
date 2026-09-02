@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.parkit.app.api.ApiService
import com.parkit.app.api.FeedbackCreate
import com.parkit.app.api.GeocodingClient
import com.parkit.app.api.SpotOut
import com.parkit.app.api.isUnauthorized
import kotlinx.coroutines.launch

/** A "callout" for a tapped pin — full address, type, how long ago it was
 * reported, and either the trust actions (claim / confirm / flag) or a
 * cancel action if it's your own report, plus a Navigate hand-off. */
@Composable
fun SpotActionsSheet(
    api: ApiService,
    spot: SpotOut,
    myUserId: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isMine = spot.reporterId == myUserId
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(spot.id) {
        address = try {
            GeocodingClient.service.reverse(spot.lat, spot.lng).shortLabel()
        } catch (_: Exception) {
            null
        }
    }

    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
                onChanged()
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.message ?: "Action failed"
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(address ?: "%.5f, %.5f".format(spot.lat, spot.lng), style = MaterialTheme.typography.headlineSmall)
            Row {
                Text(
                    if (spot.spotType == "disabled") "Disabled" else "Regular",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "  ·  ${MarkerBitmaps.relativeTimeLong(spot.reportedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Status: ${spot.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = {
                    val uri = Uri.parse("geo:${spot.lat},${spot.lng}?q=${spot.lat},${spot.lng}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 16.dp),
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null)
                Text("Navigate", modifier = Modifier.padding(start = 8.dp))
            }

            if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }

            if (spot.status == "active" && !isMine) {
                Button(
                    onClick = { run { api.claimSpot(spot.id) } },
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp),
                ) { Text("Claim this spot") }
            }
            if (!isMine) {
                Button(
                    onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("confirmed_taken")) } },
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp),
                ) { Text("I took it") }
                OutlinedButton(
                    onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("flagged_false")) } },
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp),
                ) { Text("Spot is taken (flag)") }
            }
            if (isMine && spot.status == "active") {
                OutlinedButton(
                    onClick = { run { api.cancelSpot(spot.id) } },
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp),
                ) { Text("Cancel my report") }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

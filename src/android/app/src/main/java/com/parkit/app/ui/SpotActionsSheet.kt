@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.FeedbackCreate
import com.parkit.app.api.GeocodingClient
import com.parkit.app.api.SpotOut
import com.parkit.app.api.isUnauthorized
import kotlinx.coroutines.launch

private val StatusActive = Color(0xFF2C7A4B)
private val StatusClaimed = Color(0xFFB8631A)
private val StatusMine = Color(0xFF1B4F91)
private val WarnFlag = Color(0xFFB8631A)

/** A "callout" for a tapped pin — the one piece of the core loop that
 * hadn't been touched since the very first build, while everything around
 * it (the map, the pin itself, Profile) went through several polish
 * passes. Rebuilt to use the same icon-badge + color-coded-action language
 * as the rest of the app instead of plain text rows and uniform buttons. */
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

    val isDisabledSpot = spot.spotType == "disabled"
    val statusColor = when {
        spot.status == "claimed" -> StatusClaimed
        isMine -> StatusMine
        else -> StatusActive
    }

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
                if (e.isUnauthorized()) onSessionExpired() else error = e.message ?: context.getString(R.string.spot_action_failed)
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).background(statusColor.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isDisabledSpot) Icons.AutoMirrored.Filled.Accessible else Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        address ?: "%.5f, %.5f".format(spot.lat, spot.lng),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${if (isDisabledSpot) stringResource(R.string.spot_type_disabled) else stringResource(R.string.spot_type_regular)}  ·  ${MarkerBitmaps.relativeTimeLong(context, spot.reportedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusTag(stringResource(R.string.spot_status, spot.status), statusColor)
                spot.vehicleSize?.let {
                    StatusTag(stringResource(R.string.spot_fits, it.replaceFirstChar(Char::uppercase)), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedButton(
                onClick = {
                    val uri = Uri.parse("geo:${spot.lat},${spot.lng}?q=${spot.lat},${spot.lng}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.spot_navigate), modifier = Modifier.padding(start = 8.dp))
            }

            if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (spot.status == "active" && !isMine) {
                ActionButton(
                    label = stringResource(R.string.spot_claim),
                    icon = Icons.Filled.LocalParking,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    enabled = !busy,
                    onClick = { run { api.claimSpot(spot.id) } },
                )
            }
            if (!isMine) {
                ActionButton(
                    label = stringResource(R.string.spot_i_took_it),
                    icon = Icons.Filled.CheckCircle,
                    containerColor = StatusActive,
                    contentColor = Color.White,
                    enabled = !busy,
                    onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("confirmed_taken")) } },
                )
                OutlinedActionButton(
                    label = stringResource(R.string.spot_flag_taken),
                    icon = Icons.Filled.Flag,
                    tint = WarnFlag,
                    enabled = !busy,
                    onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("flagged_false")) } },
                )
            }
            if (isMine && spot.status == "active") {
                OutlinedActionButton(
                    label = stringResource(R.string.spot_cancel_report),
                    icon = Icons.Filled.Close,
                    tint = MaterialTheme.colorScheme.error,
                    enabled = !busy,
                    onClick = { run { api.cancelSpot(spot.id) } },
                )
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, containerColor: Color, contentColor: Color, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun OutlinedActionButton(label: String, icon: ImageVector, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

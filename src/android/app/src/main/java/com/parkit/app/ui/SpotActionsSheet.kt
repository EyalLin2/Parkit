@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.parkit.app.ApiConfig
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.FeedbackCreate
import com.parkit.app.api.SpotAddressCache
import com.parkit.app.api.SpotOut
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized
import kotlinx.coroutines.launch

private val Free = Color(0xFF188038)
private val Claimed = Color(0xFFE8710A)
private val Mine = Color(0xFF1B4F91)

/** The tapped-spot card, rebuilt on Google Maps' place-sheet pattern:
 * where it is (address as the title), how trustworthy it is (freshness +
 * the reporter's photo), then one primary action (Navigate) and the
 * crowd-feedback question asked plainly — "is it still free?" — instead of
 * a stack of five equally-loud full-width buttons. */
@Composable
fun SpotActionsSheet(
    api: ApiService,
    spot: SpotOut,
    myUserId: String?,
    distanceLabel: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf(spot.address ?: SpotAddressCache.get(spot.id)) }
    val scope = rememberCoroutineScope()
    val isMine = spot.reporterId == myUserId
    val isClaimed = spot.status == "claimed"
    val isDisabledSpot = spot.spotType == "disabled"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = when {
        isClaimed -> Claimed
        isMine -> Mine
        else -> Free
    }

    LaunchedEffect(spot.id) {
        if (address == null) address = SpotAddressCache.resolve(spot.id, spot.lat, spot.lng)
    }

    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
                onChanged()
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(48.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isDisabledSpot) Icons.AutoMirrored.Filled.Accessible else Icons.Filled.LocalParking,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        address ?: stringResource(R.string.map_locating),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            stringResource(if (isDisabledSpot) R.string.map_spot_kind_disabled else R.string.map_spot_kind_regular),
                            distanceLabel,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.spot_close_cd), modifier = Modifier.size(18.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                Tag(Icons.Filled.Schedule, MarkerBitmaps.relativeTimeLong(context, spot.reportedAt), freshnessColor(spot.reportedAt))
                when {
                    isClaimed -> Tag(Icons.Filled.DirectionsCar, stringResource(R.string.spot_someone_on_the_way), Claimed)
                    isMine -> Tag(Icons.Filled.CheckCircle, stringResource(R.string.map_spot_yours), Mine)
                }
                spot.vehicleSize?.let { size ->
                    val label = stringResource(
                        when (size) {
                            "compact" -> R.string.report_size_compact
                            "large" -> R.string.report_size_large
                            else -> R.string.report_size_regular
                        },
                    )
                    Tag(Icons.Filled.DirectionsCar, stringResource(R.string.spot_fits, label), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            spot.photoUrl?.let { path ->
                AsyncImage(
                    model = ApiConfig.BASE_URL.trimEnd('/') + path,
                    contentDescription = stringResource(R.string.spot_photo_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                Button(
                    onClick = {
                        val uri = Uri.parse("geo:${spot.lat},${spot.lng}?q=${spot.lat},${spot.lng}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.spot_navigate), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
                if (spot.status == "active" && !isMine) {
                    OutlinedButton(
                        onClick = { run { api.claimSpot(spot.id) } },
                        enabled = !busy,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.spot_claim), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (!isMine) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.padding(top = 20.dp))
                Text(
                    stringResource(R.string.spot_still_free_question),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeedbackOption(
                        label = stringResource(R.string.spot_i_took_it),
                        icon = Icons.Filled.CheckCircle,
                        color = Free,
                        enabled = !busy,
                        onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("confirmed_taken")) } },
                        modifier = Modifier.weight(1f),
                    )
                    FeedbackOption(
                        label = stringResource(R.string.spot_flag_taken),
                        icon = Icons.Filled.DoNotDisturbOn,
                        color = Claimed,
                        enabled = !busy,
                        onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("flagged_false")) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (busy) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }

            if (isMine && spot.status == "active") {
                TextButton(
                    onClick = { run { api.cancelSpot(spot.id) } },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.spot_cancel_report), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Green while a report is fresh, amber once it's getting old, grey when
 * it's probably gone — the same reading the map's fading pins give. */
private fun freshnessColor(iso: String): Color {
    val minutes = try {
        java.time.Duration.between(java.time.Instant.parse(iso), java.time.Instant.now()).toMinutes()
    } catch (_: Exception) {
        0
    }
    return when {
        minutes < 10 -> Free
        minutes < 30 -> Claimed
        else -> Color(0xFF80868B)
    }
}

@Composable
private fun Tag(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun FeedbackOption(label: String, icon: ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier.height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

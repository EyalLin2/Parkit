@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.material.icons.filled.Flag
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
import com.parkit.app.api.ContentReport
import com.parkit.app.api.FeedbackCreate
import com.parkit.app.api.SpotAddressCache
import com.parkit.app.api.SpotOut
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized
import kotlinx.coroutines.launch

private val Free = Color(0xFF188038)
private val Claimed = Color(0xFFE8710A)
private val Mine = Color(0xFF1B4F91)
private val Hot = Color(0xFFE8480C)

/** The tapped-spot card, top to bottom: where (address), status (one
 * pill + distance), proof (photo), what matters to you, one big action
 * ("On my way" claims *and* navigates), then "still free?" as one quiet
 * two-part control. */
@Composable
fun SpotActionsSheet(
    api: ApiService,
    spot: SpotOut,
    myUserId: String?,
    fit: DriverFit,
    distanceLabel: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onClaimed: (SpotOut) -> Unit,
    onParked: () -> Unit,
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
    val photoUrl = spot.photoUrl?.let { ApiConfig.BASE_URL.trimEnd('/') + it }
    var photoFullScreen by remember { mutableStateOf(false) }
    // A photo that fails to load (e.g. deleted) falls back to the header slot.
    var photoFailed by remember { mutableStateOf(false) }
    val showHero = photoUrl != null && !photoFailed
    var showContentReport by remember { mutableStateOf(false) }

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

    // Claim and navigate are one tap: saying "on my way" is the reason you
    // open navigation, so they shouldn't be two buttons to choose between.
    fun navigate() = DriveTo.open(context, spot.lat, spot.lng)
    val canClaim = spot.status == "active" && !isMine

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            // 1. Where — the address, with the close button.
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    address ?: stringResource(R.string.map_locating),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.spot_close_cd), modifier = Modifier.size(18.dp))
                }
            }

            // 2. Status — one pill (free / on the way / yours, with its age)
            // and the distance. Everything else the card used to list is gone.
            val age = if (MarkerBitmaps.relativeTimeShort(context, spot.reportedAt) == stringResource(R.string.time_now_short)) {
                stringResource(R.string.time_now_long)
            } else {
                stringResource(R.string.spot_age_short, MarkerBitmaps.relativeTimeShort(context, spot.reportedAt))
            }
            val statusText = when {
                isClaimed -> stringResource(R.string.spot_someone_on_the_way)
                isMine -> stringResource(R.string.map_spot_yours)
                else -> stringResource(R.string.spot_status_free)
            }
            val statusColor = if (isClaimed || isMine) accent else freshnessColor(spot.reportedAt)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                    Text(
                        "$statusText · $age",
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                distanceLabel?.let { extra ->
                    Text(
                        extra,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }

            // 3. Proof — the reporter's photo, or a quiet "no photo" strip so
            // the card has the same shape either way.
            if (showHero) {
                Box(
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { photoFullScreen = true },
                ) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = stringResource(R.string.spot_photo_cd),
                        contentScale = ContentScale.Crop,
                        onError = { photoFailed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.spot_photo_taken, MarkerBitmaps.relativeTimeLong(context, spot.reportedAt)),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(32.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.OpenInFull, contentDescription = stringResource(R.string.spot_photo_expand_cd), tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)).padding(horizontal = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.NoPhotography,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.spot_no_photo_cd),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // 4. Details — each on its own line with an icon, in one soft
            // panel, so nothing is lost and nothing competes with the button.
            val details = buildList {
                spot.vehicleSize?.let { size ->
                    val label = stringResource(
                        when (size) {
                            "compact" -> R.string.report_size_compact
                            "large" -> R.string.report_size_large
                            else -> R.string.report_size_regular
                        },
                    )
                    add(Triple(Icons.Filled.DirectionsCar, stringResource(R.string.spot_fits, label.lowercase()), null))
                }
                if (isDisabledSpot) add(Triple(Icons.AutoMirrored.Filled.Accessible, stringResource(R.string.map_spot_kind_disabled), null))
                if (spot.payment == "paid") add(Triple(Icons.Filled.Payments, stringResource(R.string.spot_blue_white), null))
                ParkItLevel.of(spot.reporterLevel).takeIf { it != ParkItLevel.Newbie }?.let { level ->
                    add(Triple(level.icon, stringResource(R.string.spot_reported_by_level, stringResource(level.nameRes)), level.color))
                }
            }
            DriverNotices(fit, spot.hotMinutes, details)

            // 5. One big action.
            Button(
                onClick = {
                    if (!canClaim) {
                        navigate()
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val claimed = api.claimSpot(spot.id).copy(address = spot.address ?: address, reporterLevel = spot.reporterLevel)
                            navigate()
                            onClaimed(claimed)
                        } catch (e: Exception) {
                            if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth().height(54.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(if (canClaim) R.string.spot_go else R.string.spot_navigate),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }

            // 6. The crowd question, as one quiet two-part control.
            if (!isMine) {
                Text(
                    stringResource(R.string.spot_still_free_question),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                ) {
                    FeedbackHalf(
                        label = stringResource(R.string.spot_i_took_it),
                        icon = Icons.Filled.CheckCircle,
                        color = Free,
                        enabled = !busy,
                        onClick = {
                            run {
                                api.submitFeedback(spot.id, FeedbackCreate("confirmed_taken"))
                                onParked()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(width = 1.dp, height = 22.dp).background(MaterialTheme.colorScheme.outline))
                    FeedbackHalf(
                        label = stringResource(R.string.spot_flag_taken),
                        icon = Icons.Filled.DoNotDisturbOn,
                        color = Claimed,
                        enabled = !busy,
                        onClick = { run { api.submitFeedback(spot.id, FeedbackCreate("flagged_false")) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Google Play's rule for anything users post: a way to report it.
            if (!isMine) {
                TextButton(
                    onClick = { showContentReport = true },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.content_report_action), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
            }

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

    if (showContentReport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showContentReport = false },
            title = { Text(stringResource(R.string.content_report_title)) },
            text = {
                Column {
                    listOf(
                        "photo" to R.string.content_report_photo,
                        "user" to R.string.content_report_user,
                        "other" to R.string.content_report_other,
                    ).filter { (reason, _) -> reason != "photo" || photoUrl != null }.forEach { (reason, label) ->
                        TextButton(
                            onClick = {
                                showContentReport = false
                                run {
                                    api.reportContent(spot.id, ContentReport(reason))
                                    android.widget.Toast.makeText(context, R.string.content_report_sent, android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(label), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContentReport = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (photoFullScreen && photoUrl != null) {
        PhotoViewer(url = photoUrl, onDismiss = { photoFullScreen = false })
    }
}

/** Full-screen look at the reporter's photo — pinch to zoom, tap or back
 * to close. The card shows a crop; this is where you actually check the
 * spot (curb paint, signs, whether a car fits). */
@Composable
private fun PhotoViewer(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                    }
                },
        ) {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.spot_photo_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                    .size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.spot_close_cd), tint = Color.White)
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

/** The card's details panel: first what matters to *this* driver (hot
 * area, disabled permit, car fit — coloured, full sentences), then the
 * spot's own facts (size, kind, reporter) in quiet grey. One soft panel,
 * one line each, so the card stays calm and the button stays loudest. */
@Composable
private fun DriverNotices(fit: DriverFit, hotMinutes: Int?, facts: List<Triple<ImageVector, String, Color?>>) {
    val notices = buildList<Triple<ImageVector, String, Color?>> {
        hotMinutes?.let { add(Triple(Icons.Filled.LocalFireDepartment, androidx.compose.ui.res.pluralStringResource(R.plurals.hot_long, it, it), Hot)) }
        if (fit.needsDisabledPermit) add(Triple(Icons.AutoMirrored.Filled.Accessible, stringResource(R.string.fit_needs_disabled_permit_long), MaterialTheme.colorScheme.error))
        if (fit.tooSmall) add(Triple(Icons.Filled.DirectionsCar, stringResource(R.string.fit_too_small_long), Claimed))
        addAll(facts)
    }
    if (notices.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        notices.forEach { (icon, text, color) ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (color != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/** One half of the "still free?" control: icon + label, no border. */
@Composable
private fun FeedbackHalf(label: String, icon: ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}

package com.parkit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.SignReading
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * "Can I park here?" — photograph a parking sign, get a plain answer for a
 * regular driver right now. Opens the camera straight away; the answer is
 * advice, so the sheet always says to check the sign itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignReaderSheet(api: ApiService, onDismiss: () -> Unit, onSessionExpired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hebrew = context.resources.configuration.locales[0].language in setOf("he", "iw")
    var reading by remember { mutableStateOf<SignReading?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reminderSet by remember { mutableStateOf<String?>(null) } // "18:50" once set

    fun setReminder(r: SignReading) {
        val at = r.changesAt ?: r.until ?: return
        val change = com.parkit.app.remind.ParkingReminder.changeTime(at) ?: return
        val text = context.getString(
            when (r.changesTo) {
                "paid" -> R.string.reminder_text_paid
                "allowed" -> R.string.reminder_text_allowed
                else -> R.string.reminder_text_forbidden
            },
            at,
        )
        val ringAt = com.parkit.app.remind.ParkingReminder.schedule(context, change, text) ?: return
        reminderSet = java.time.Instant.ofEpochMilli(ringAt).atZone(java.time.ZoneId.of("Asia/Jerusalem"))
            .toLocalTime().toString().take(5)
    }
    // Android 13+: ask for notifications only now, when the driver wants one.
    var pendingReminder by remember { mutableStateOf<SignReading?>(null) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingReminder?.let { setReminder(it) }
        pendingReminder = null
    }
    fun askReminder(r: SignReading) {
        val needs = android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pendingReminder = r
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            setReminder(r)
        }
    }

    fun send(bitmap: Bitmap) {
        busy = true
        error = null
        scope.launch {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val part = MultipartBody.Part.createFormData("photo", "sign.jpg", stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull()))
                reminderSet = null // a new sign: its own reminder
                reading = api.readSign(part)
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
            } finally {
                busy = false
            }
        }
    }

    val photoFile = remember { java.io.File(context.cacheDir, "report_photos/sign.jpg").apply { parentFile?.mkdirs() } }
    val photoUri = remember { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".photos", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) scope.launch {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadReportPhoto(photoFile, maxSide = 1280) }
            if (bitmap != null) send(bitmap)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(photoUri)
    }
    fun shoot() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) camera.launch(photoUri) else permission.launch(Manifest.permission.CAMERA)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        ) {
            val r = reading
            when {
                busy -> {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    Text(stringResource(R.string.sign_reading), style = MaterialTheme.typography.bodyMedium)
                }
                r != null -> {
                    val (color, icon, title) = when (r.verdict) {
                        "allowed" -> Triple(Color(0xFF188038), Icons.Filled.CheckCircle, stringResource(R.string.sign_allowed))
                        "not_allowed" -> Triple(Color(0xFFD93025), Icons.Filled.DoNotDisturbOn, stringResource(R.string.sign_not_allowed))
                        else -> Triple(Color(0xFFE37400), Icons.Filled.HelpOutline, stringResource(R.string.sign_unclear))
                    }
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 12.dp))
                    r.until?.let { Text(stringResource(R.string.sign_until, it), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
                    if (r.paid == true) {
                        Text(stringResource(R.string.sign_paid), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1565C0), modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        if (hebrew) r.summaryHe else r.summaryEn,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    // The useful part: don't get a ticket when the rules change.
                    val changeAt = r.changesAt ?: r.until
                    if (changeAt != null && com.parkit.app.remind.ParkingReminder.changeTime(changeAt) != null) {
                        val set = reminderSet
                        if (set == null) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { askReminder(r) },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.padding(top = 14.dp),
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.reminder_button, changeAt), modifier = Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Text(
                                stringResource(R.string.reminder_set, set),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF188038),
                                modifier = Modifier.padding(top = 14.dp),
                            )
                        }
                    }
                }
                else -> {
                    SignScanIcon(72.dp)
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Text(stringResource(R.string.sign_title), style = MaterialTheme.typography.titleLarge)
                        AiBadge(Modifier.padding(start = 8.dp))
                    }
                    Text(
                        stringResource(R.string.sign_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
            if (!busy) {
                Button(onClick = { shoot() }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp)) {
                    Text(stringResource(if (reading == null) R.string.sign_shoot else R.string.sign_again), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                stringResource(R.string.sign_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** "AI" — marks what a model decided, so nobody mistakes it for a rule.
 * [small] is the plain lettering on the sign button; the full size is a pill. */
@Composable
fun AiBadge(modifier: Modifier = Modifier, small: Boolean = false) {
    if (small) {
        // A thin purple→blue frame with the letters in the same gradient.
        val ai = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF7B3FC4), Color(0xFF2E7BD6), Color(0xFF00B4D8)))
        Text(
            "AI",
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Black,
            style = androidx.compose.ui.text.TextStyle(brush = ai),
            modifier = modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White)
                .border(1.5.dp, ai, RoundedCornerShape(5.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp),
        )
        return
    }
    Text(
        "AI",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF7B3FC4), Color(0xFF2E7BD6))))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A blue "P" parking sign on its pole, with a small camera badge — "photograph
 * the parking sign", readable at a glance. [animate] adds a soft scan line. */
@Composable
fun SignScanIcon(size: androidx.compose.ui.unit.Dp, animate: Boolean = true) {
    val sweep = if (animate) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0.15f,
            targetValue = 0.85f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1400),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "line",
        ).value
    } else {
        -1f
    }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val pStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
    Box(Modifier.size(size)) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val w = this.size.width
            // Pole.
            drawRoundRect(Color(0xFF9AA0A6), androidx.compose.ui.geometry.Offset(w * 0.44f, w * 0.55f), androidx.compose.ui.geometry.Size(w * 0.08f, w * 0.43f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
            // The sign: blue, white edge, big P.
            val left = w * 0.18f
            val top = w * 0.04f
            val side = w * 0.6f
            drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(left - w * 0.03f, top - w * 0.03f), androidx.compose.ui.geometry.Size(side + w * 0.06f, side + w * 0.06f), androidx.compose.ui.geometry.CornerRadius(side * 0.2f))
            drawRoundRect(Color(0xFF1565C0), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(side, side), androidx.compose.ui.geometry.CornerRadius(side * 0.16f))
            val text = measurer.measure("P", pStyle)
            drawText(text, topLeft = androidx.compose.ui.geometry.Offset(left + (side - text.size.width) / 2f, top + (side - text.size.height) / 2f))
            if (sweep >= 0f) {
                val y = top + side * sweep
                drawLine(Color(0xFFE53935).copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(left - w * 0.06f, y), androidx.compose.ui.geometry.Offset(left + side + w * 0.06f, y), w * 0.03f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        // Small camera badge: "take a photo of it".
        Box(
            Modifier.align(Alignment.BottomEnd).size(size * 0.42f).clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White).padding(size * 0.035f).clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF1B4F91)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(androidx.compose.material.icons.Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.22f))
        }
    }
}

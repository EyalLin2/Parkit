package com.parkit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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

    fun send(bitmap: Bitmap) {
        busy = true
        error = null
        scope.launch {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val part = MultipartBody.Part.createFormData("photo", "sign.jpg", stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull()))
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

/** "✨ AI" — marks what a model decided, so nobody mistakes it for a rule. */
@Composable
fun AiBadge(modifier: Modifier = Modifier) {
    Text(
        "✨ AI",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF7B3FC4), Color(0xFF2E7BD6))))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A blue parking sign with a "P", inside scanner corners, a scan line
 * sweeping over it — "point the camera at the sign", at a glance. */
@Composable
fun SignScanIcon(size: androidx.compose.ui.unit.Dp, animate: Boolean = true) {
    val sweep = if (animate) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0.2f,
            targetValue = 0.8f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1400),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "line",
        ).value
    } else {
        0.5f
    }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val pStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.36f).sp)
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val corner = w * 0.26f
        val stroke = w * 0.07f
        val bracket = Color(0xFF1B4F91)
        // Scanner corners.
        for ((x, y, dx, dy) in listOf(
            listOf(0f, 0f, 1f, 1f), listOf(w, 0f, -1f, 1f), listOf(0f, w, 1f, -1f), listOf(w, w, -1f, -1f),
        )) {
            drawLine(bracket, androidx.compose.ui.geometry.Offset(x, y + dy * stroke / 2), androidx.compose.ui.geometry.Offset(x + dx * corner, y + dy * stroke / 2), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(bracket, androidx.compose.ui.geometry.Offset(x + dx * stroke / 2, y), androidx.compose.ui.geometry.Offset(x + dx * stroke / 2, y + dy * corner), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        // The parking sign.
        val inset = w * 0.2f
        val sign = w - 2 * inset
        drawRoundRect(Color(0xFF1565C0), androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Size(sign, sign), androidx.compose.ui.geometry.CornerRadius(sign * 0.18f))
        val text = measurer.measure("P", pStyle)
        drawText(text, topLeft = androidx.compose.ui.geometry.Offset((w - text.size.width) / 2f, (w - text.size.height) / 2f))
        // Scan line.
        val y = w * sweep
        drawLine(Color(0xFFE53935), androidx.compose.ui.geometry.Offset(w * 0.08f, y), androidx.compose.ui.geometry.Offset(w * 0.92f, y), w * 0.045f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}


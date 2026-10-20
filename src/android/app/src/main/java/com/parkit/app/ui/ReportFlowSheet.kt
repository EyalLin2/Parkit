@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.SpotCreate
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

private val Gold = Color(0xFFC9971C)

private const val NOTE_MAX = 120

/**
 * Reporting stays the fewest possible decisions — photo (mandatory, it's
 * what makes reports trustworthy), Regular vs Disabled, car size, confirm —
 * but laid out as one calm form instead of a reveal-after-photo wizard:
 * every choice is visible up front, and the confirm button itself says
 * what's still missing. The street address goes along with the report so
 * other drivers' lists can show it without geocoding every spot.
 */
@Composable
fun ReportFlowSheet(
    api: ApiService,
    lat: Double,
    lng: Double,
    addressLabel: String?,
    streetAddress: String?,
    defaultVehicleSize: String?,
    /** The phone's latest fix, read at the moment of sending. */
    currentFix: () -> android.location.Location?,
    onDismiss: () -> Unit,
    onReported: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var blurredPreview by remember { mutableStateOf<Bitmap?>(null) }
    var stagingId by remember { mutableStateOf<String?>(null) }
    // Faces + licence plates the server blurred in this photo.
    var facesBlurred by remember { mutableStateOf<Int?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("street") }
    var selectedVehicleSize by remember { mutableStateOf(defaultVehicleSize ?: "regular") }
    // Optional, off by default: one tap if the curb is blue-white (paid).
    // Deliberately not a required question — the form stays short.
    var blueWhite by remember { mutableStateOf(false) }
    // Optional heads-up for the next driver: a hazard, a low branch…
    var note by remember { mutableStateOf("") }
    // AI hints from the photo: they fill only what the driver hasn't touched.
    var typeTouched by remember { mutableStateOf(false) }
    var blueWhiteTouched by remember { mutableStateOf(false) }
    var autoFilled by remember { mutableStateOf(false) }
    var notASpot by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun uploadPhoto(bitmap: Bitmap) {
        uploading = true
        error = null
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val body = stream.toByteArray().toRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "photo.jpg", body)
            val staged = api.stagePhoto(part)
            stagingId = staged.stagingId
            // Ask what the photo shows in the background; the form never waits for it.
            val id = staged.stagingId
            scope.launch {
                val hints = runCatching { api.analyzePhoto(id) }.getOrNull() ?: return@launch
                if (stagingId != id) return@launch // a newer photo was taken meanwhile
                notASpot = !hints.looksLikeFreeSpot
                var filled = false
                if (!typeTouched && hints.disabledSpot) { selectedType = "disabled"; filled = true }
                if (!blueWhiteTouched && hints.blueWhiteCurb && selectedType == "street") { blueWhite = true; filled = true }
                autoFilled = filled
            }
            facesBlurred = staged.facesBlurred + staged.platesBlurred
            val previewBytes = Base64.decode(staged.previewBase64, Base64.DEFAULT)
            blurredPreview = BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size)
        } catch (e: Exception) {
            if (e.isUnauthorized()) onSessionExpired() else error = context.getString(R.string.report_photo_upload_failed, e.friendlyMessage(context))
        } finally {
            uploading = false
        }
    }

    // Full-size photo (TakePicturePreview only returns a thumbnail, which made
    // report photos small and blurry): the camera writes into our cache, we
    // fix its rotation and scale it to ≤1600 px before uploading.
    val photoFile = remember { java.io.File(context.cacheDir, "report_photos/report.jpg").apply { parentFile?.mkdirs() } }
    val photoUri = remember { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".photos", photoFile) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) scope.launch {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadReportPhoto(photoFile) }
            if (bitmap != null) uploadPhoto(bitmap) else error = context.getString(R.string.report_photo_upload_failed, "")
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(photoUri)
    }

    fun startCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) cameraLauncher.launch(photoUri) else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    fun submit() {
        submitting = true
        error = null
        scope.launch {
            try {
                val fix = currentFix()
                api.reportSpot(
                    SpotCreate(
                        lat = lat,
                        lng = lng,
                        spotType = selectedType,
                        payment = if (blueWhite && selectedType == "street") "paid" else "free",
                        vehicleSize = selectedVehicleSize,
                        photoStagingId = stagingId,
                        address = streetAddress,
                        note = note.trim().ifEmpty { null },
                        reporterLat = fix?.latitude,
                        reporterLng = fix?.longitude,
                        reporterAccuracyM = fix?.takeIf { it.hasAccuracy() }?.accuracy,
                    ),
                )
                onReported()
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
            } finally {
                submitting = false
            }
        }
    }

    ModalBottomSheet(
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0), onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                // What you get for it, right where you decide — Waze shows the
                // same "+points" promise on its report screen.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(Gold.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.report_points_reward), style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(start = 4.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    addressLabel ?: stringResource(R.string.map_locating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            PhotoArea(
                preview = blurredPreview,
                facesBlurred = facesBlurred,
                uploading = uploading,
                onTakePhoto = { startCamera() },
                modifier = Modifier.padding(top = 18.dp),
            )
            // Said before the photo is taken, not only after: what happens to it.
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
                Icon(Icons.Filled.FaceRetouchingOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                Text(
                    stringResource(R.string.report_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            if (autoFilled) {
                Text(
                    stringResource(R.string.report_auto_filled),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (notASpot) {
                Text(
                    stringResource(R.string.report_not_a_spot),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB06000),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            FieldLabel(stringResource(R.string.report_type_question))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeOption(
                    label = stringResource(R.string.map_spot_kind_regular),
                    icon = Icons.Filled.DirectionsCar,
                    selected = selectedType == "street",
                    onClick = { selectedType = "street"; typeTouched = true },
                    modifier = Modifier.weight(1f),
                )
                TypeOption(
                    label = stringResource(R.string.map_spot_kind_disabled),
                    icon = Icons.AutoMirrored.Filled.Accessible,
                    selected = selectedType == "disabled",
                    onClick = { selectedType = "disabled"; typeTouched = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (selectedType == "street") {
                androidx.compose.material3.FilterChip(
                    selected = blueWhite,
                    onClick = { blueWhite = !blueWhite; blueWhiteTouched = true },
                    label = { Text(stringResource(R.string.report_blue_white)) },
                    leadingIcon = {
                        Icon(
                            if (blueWhite) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            FieldLabel(stringResource(R.string.report_size_question))
            val sizes = listOf(
                "compact" to stringResource(R.string.report_size_compact),
                "regular" to stringResource(R.string.report_size_regular),
                "large" to stringResource(R.string.report_size_large),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                sizes.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = selectedVehicleSize == value,
                        onClick = { selectedVehicleSize = value },
                        shape = SegmentedButtonDefaults.itemShape(index, sizes.size),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                }
            }

            FieldLabel(stringResource(R.string.report_note_label))
            androidx.compose.material3.OutlinedTextField(
                value = note,
                onValueChange = { note = it.replace('\n', ' ').take(NOTE_MAX) },
                placeholder = { Text(stringResource(R.string.report_note_hint)) },
                supportingText = { Text("${note.length}/$NOTE_MAX") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }

            val ready = stagingId != null
            Button(
                onClick = { submit() },
                enabled = ready && !submitting && !uploading,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(56.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                } else {
                    Text(
                        stringResource(if (ready) R.string.report_confirm else R.string.report_needs_photo),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}

/** One wide tile: empty-state invitation before the photo, the blurred
 * preview (with what was blurred, and a retake action) after it. */
@Composable
private fun PhotoArea(preview: Bitmap?, facesBlurred: Int?, uploading: Boolean, onTakePhoto: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    if (preview == null) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = modifier.fillMaxWidth().height(150.dp).clip(shape).clickable(enabled = !uploading, onClick = onTakePhoto),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (uploading) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.report_blurring), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                } else {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.report_take_photo_cd), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text(stringResource(R.string.report_take_photo), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                    Text(stringResource(R.string.report_photo_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth().height(190.dp).clip(shape)) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = stringResource(R.string.report_preview_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(190.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Icon(Icons.Filled.FaceRetouchingOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    if ((facesBlurred ?: 0) > 0) pluralStringResource(R.plurals.report_items_blurred, facesBlurred!!, facesBlurred)
                    else stringResource(R.string.report_privacy_checked),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .background(Color.White, RoundedCornerShape(50)).clickable(onClick = onTakePhoto).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFF202124), modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.report_retake), style = MaterialTheme.typography.labelLarge, color = Color(0xFF202124), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun TypeOption(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
        modifier = modifier.height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}


/** Decode the camera's full-size JPEG at a sensible size (≤ ~1600 px on the
 * long side), upright according to its EXIF orientation. */
internal fun loadReportPhoto(file: java.io.File, maxSide: Int = 1600): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rotation = when (androidx.exifinterface.media.ExifInterface(file.path).getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
    )) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val upright = if (rotation == 0f) decoded else Bitmap.createBitmap(
        decoded, 0, 0, decoded.width, decoded.height, android.graphics.Matrix().apply { postRotate(rotation) }, true,
    )
    val scale = maxSide.toFloat() / maxOf(upright.width, upright.height)
    return if (scale >= 1f) upright else Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt(), (upright.height * scale).toInt(), true)
}

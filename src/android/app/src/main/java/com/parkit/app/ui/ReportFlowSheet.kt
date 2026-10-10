@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.SpotCreate
import com.parkit.app.api.isUnauthorized
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * Reporting is deliberately reduced to the fewest possible decisions:
 * take a photo (mandatory — trust in reports depends on it, per the
 * user's explicit call), pick Regular vs Disabled, confirm. No payment
 * question, no spot-type menu — those can come back later if needed.
 */
@Composable
fun ReportFlowSheet(
    api: ApiService,
    lat: Double,
    lng: Double,
    addressLabel: String,
    onDismiss: () -> Unit,
    onReported: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var blurredPreview by remember { mutableStateOf<Bitmap?>(null) }
    var stagingId by remember { mutableStateOf<String?>(null) }
    var facesBlurred by remember { mutableStateOf<Int?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("street") }
    var selectedVehicleSize by remember { mutableStateOf("regular") }
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
            facesBlurred = staged.facesBlurred
            val previewBytes = Base64.decode(staged.previewBase64, Base64.DEFAULT)
            blurredPreview = BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size)
        } catch (e: Exception) {
            if (e.isUnauthorized()) onSessionExpired() else error = context.getString(R.string.report_photo_upload_failed, e.message)
        } finally {
            uploading = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) scope.launch { uploadPhoto(bitmap) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    fun startCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) cameraLauncher.launch(null) else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    fun submit() {
        submitting = true
        error = null
        scope.launch {
            try {
                api.reportSpot(
                    SpotCreate(
                        lat = lat,
                        lng = lng,
                        spotType = selectedType,
                        payment = "free",
                        vehicleSize = selectedVehicleSize,
                        photoStagingId = stagingId,
                    )
                )
                onReported()
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.message
            } finally {
                submitting = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall)
            Text(addressLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.padding(top = 20.dp))

            if (blurredPreview == null) {
                Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                            .padding(6.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uploading) {
                            CircularProgressIndicator()
                        } else {
                            IconButton(onClick = { startCamera() }, modifier = Modifier.size(108.dp)) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = stringResource(R.string.report_take_photo_cd),
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (!uploading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.report_photo_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            } else {
                Image(
                    bitmap = blurredPreview!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.report_preview_cd),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
                facesBlurred?.let {
                    Text(
                        pluralStringResource(R.plurals.report_faces_blurred, it, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.padding(top = 18.dp))
                Text(stringResource(R.string.report_type_question), style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        Triple("street", stringResource(R.string.report_type_regular), Icons.Filled.DirectionsCar),
                        Triple("disabled", stringResource(R.string.report_type_disabled), Icons.AutoMirrored.Filled.Accessible),
                    ).forEach { (value, label, icon) ->
                        SelectableTile(
                            label = label,
                            icon = icon,
                            selected = selectedType == value,
                            onClick = { selectedType = value },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.padding(top = 18.dp))
                Text(stringResource(R.string.report_size_question), style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("compact", stringResource(R.string.report_size_compact), 16.dp),
                        Triple("regular", stringResource(R.string.report_size_regular), 20.dp),
                        Triple("large", stringResource(R.string.report_size_large), 24.dp),
                    ).forEach { (value, label, iconSize) ->
                        SelectableTile(
                            label = label,
                            icon = Icons.Filled.DirectionsCar,
                            iconSize = iconSize,
                            selected = selectedVehicleSize == value,
                            onClick = { selectedVehicleSize = value },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.padding(top = 22.dp))
                Button(
                    onClick = { submit() },
                    enabled = !submitting,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (submitting) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text(stringResource(R.string.report_confirm), style = MaterialTheme.typography.titleMedium)
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

/** A real visual tile, not a text button — icon on its own colored badge,
 * label underneath, strong filled-color state when selected. Matches the
 * "icon on a tinted circle" language used for badges/stat-tiles elsewhere
 * instead of a plain bordered rectangle of text. */
@Composable
private fun SelectableTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) accent else MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = if (selected) 3.dp else 0.dp,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize + 20.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else accent.copy(alpha = 0.14f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else accent,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

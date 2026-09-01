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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.parkit.app.api.ApiService
import com.parkit.app.api.SpotCreate
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var blurredPreview by remember { mutableStateOf<Bitmap?>(null) }
    var stagingId by remember { mutableStateOf<String?>(null) }
    var facesBlurred by remember { mutableStateOf<Int?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("street") }
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
            error = "Photo upload failed: ${e.message}"
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
                api.reportSpot(SpotCreate(lat = lat, lng = lng, spotType = selectedType, payment = "free", photoStagingId = stagingId))
                onReported()
            } catch (e: Exception) {
                error = e.message
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
            Text("Report parking", style = MaterialTheme.typography.headlineSmall)
            Text(addressLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.padding(top = 20.dp))

            if (blurredPreview == null) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uploading) {
                        CircularProgressIndicator()
                    } else {
                        IconButton(onClick = { startCamera() }, modifier = Modifier.size(120.dp)) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Take a photo",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Text(
                    "A photo is required — it's what makes reports trustworthy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            } else {
                Image(
                    bitmap = blurredPreview!!.asImageBitmap(),
                    contentDescription = "Blurred photo preview",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
                facesBlurred?.let {
                    Text(
                        "$it face(s) blurred (local stand-in, not real Rekognition)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.padding(top = 18.dp))
                Text("What kind of spot?", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("street" to "Regular", "disabled" to "Disabled").forEach { (value, label) ->
                        val selected = selectedType == value
                        Button(
                            onClick = { selectedType = value },
                            shape = RoundedCornerShape(14.dp),
                            colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text(label) }
                    }
                }

                Spacer(Modifier.padding(top = 22.dp))
                Button(
                    onClick = { submit() },
                    enabled = !submitting,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (submitting) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Confirm Report", style = MaterialTheme.typography.titleMedium)
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

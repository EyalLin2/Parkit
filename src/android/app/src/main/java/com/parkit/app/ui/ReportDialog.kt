package com.parkit.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.parkit.app.api.ApiService
import com.parkit.app.api.SpotCreate
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

private val SPOT_TYPES = listOf("street", "lot", "disabled", "ev_charging")
private val PAYMENTS = listOf("free", "paid")

@Composable
fun ReportDialog(
    api: ApiService,
    lat: Double,
    lng: Double,
    onDismiss: () -> Unit,
    onReported: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var spotType by remember { mutableStateOf(SPOT_TYPES[0]) }
    var payment by remember { mutableStateOf(PAYMENTS[0]) }
    var spotTypeMenuOpen by remember { mutableStateOf(false) }
    var paymentMenuOpen by remember { mutableStateOf(false) }

    var stagingId by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var facesBlurred by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            try {
                val tempFile = File.createTempFile("parkit_upload", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                val body = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "photo.jpg", body)
                val staged = api.stagePhoto(part)
                stagingId = staged.stagingId
                facesBlurred = staged.facesBlurred
                val bytes = Base64.decode(staged.previewBase64, Base64.DEFAULT)
                previewBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                tempFile.delete()
            } catch (e: Exception) {
                error = "Photo upload failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report a spot") },
        text = {
            Column {
                Text("Location: %.5f, %.5f".format(lat, lng))

                Box {
                    OutlinedButton(onClick = { spotTypeMenuOpen = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Type: $spotType")
                    }
                    DropdownMenu(expanded = spotTypeMenuOpen, onDismissRequest = { spotTypeMenuOpen = false }) {
                        SPOT_TYPES.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { spotType = t; spotTypeMenuOpen = false })
                        }
                    }
                }

                Box {
                    OutlinedButton(onClick = { paymentMenuOpen = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Payment: $payment")
                    }
                    DropdownMenu(expanded = paymentMenuOpen, onDismissRequest = { paymentMenuOpen = false }) {
                        PAYMENTS.forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = { payment = p; paymentMenuOpen = false })
                        }
                    }
                }

                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !busy,
                ) {
                    Text(if (stagingId == null) "Add photo (optional)" else "Replace photo")
                }

                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Blurred preview",
                        modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp),
                    )
                    Text("$facesBlurred face(s) blurred (local stand-in — not real Rekognition)")
                }

                if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            api.reportSpot(SpotCreate(lat = lat, lng = lng, spotType = spotType, payment = payment, photoStagingId = stagingId))
                            onReported()
                        } catch (e: Exception) {
                            error = "Report failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Report spot") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

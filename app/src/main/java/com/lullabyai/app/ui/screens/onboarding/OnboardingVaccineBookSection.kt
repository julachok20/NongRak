package com.lullabyai.app.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.HealthGood
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.util.ImageUtils
import com.lullabyai.app.viewmodel.OnboardingViewModel
import java.io.File
import java.io.FileOutputStream

/** Optional ministry vaccine book — photo or document; uploaded after the child row is created. */
@Composable
fun OnboardingVaccineBookSection(
    onboardingViewModel: OnboardingViewModel,
    pendingCount: Int,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    var pageIndex by remember { mutableIntStateOf(pendingCount) }

    fun addBytes(name: String, mime: String?, bytes: ByteArray) {
        if (bytes.size > 10 * 1024 * 1024) return
        onboardingViewModel.addPendingDoc(name, mime, bytes)
        pageIndex++
    }

    fun addFromUri(uri: Uri) {
        runCatching {
            val cr = context.contentResolver
            var display = "vaccine_book_page_$pageIndex"
            cr.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) display = c.getString(idx) ?: display
            }
            val mime = cr.getType(uri) ?: "image/jpeg"
            val bytes = if (mime.startsWith("image/")) {
                ImageUtils.downscaleToJpeg(context, uri)
            } else {
                ImageUtils.readBytes(context, uri)
            } ?: return
            val ext = if (mime.contains("pdf")) "pdf" else "jpg"
            val safeName = if (display.contains('.')) display else "vaccine_book_page_${pageIndex}.$ext"
            addBytes(safeName, mime, bytes)
        }
    }

    fun cacheCameraPhoto(bitmap: Bitmap) {
        val file = File(context.cacheDir, "vaccine_book_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        addFromUri(Uri.fromFile(file))
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(8)
    ) { uris -> uris.forEach { addFromUri(it) } }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) addFromUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) cacheCameraPhoto(bmp)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }
    val openCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("สมุดวัคซีน (ไม่บังคับ)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Vaccine book (optional) — take a photo or upload PDF/images from the ministry health book. Stored securely in the medical vault.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = openCamera,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("ถ่ายรูป", fontWeight = FontWeight.SemiBold, color = Primary)
            }
            OutlinedButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = enabled,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("เลือกรูป", fontWeight = FontWeight.SemiBold, color = Primary)
            }
        }
        OutlinedButton(
            onClick = { documentPicker.launch("*/*") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.UploadFile, null, tint = Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("อัปโหลดเอกสาร (PDF / รูป)", fontWeight = FontWeight.SemiBold, color = Primary)
        }
    }
}

@Composable
fun OnboardingVaccineBookPendingList(
    onboardingViewModel: OnboardingViewModel,
    pendingDocs: List<com.lullabyai.app.viewmodel.PendingDoc>,
    enabled: Boolean = true
) {
    pendingDocs.forEachIndexed { i, d ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Description, null, tint = Primary, modifier = Modifier.size(18.dp))
            Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            IconButton(onClick = { onboardingViewModel.removePendingDoc(i) }, enabled = enabled) {
                Icon(Icons.Filled.Close, "ลบ", tint = Error, modifier = Modifier.size(16.dp))
            }
        }
    }
    if (pendingDocs.isNotEmpty()) {
        Text(
            "${pendingDocs.size} file(s) attached",
            style = MaterialTheme.typography.labelMedium,
            color = HealthGood
        )
    }
}

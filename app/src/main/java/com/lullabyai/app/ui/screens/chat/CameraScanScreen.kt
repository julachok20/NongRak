package com.lullabyai.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.data.repository.SurroundingsScanService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val SCAN_INTERVAL_MS = 8_000L  // rate-limited: one AI call per 8s, not per frame

/** Live camera feed with periodic (not per-frame) AI safety-advice scanning of the surroundings.
 *  Rate-limited deliberately — a real "analyze every frame" loop would mean an AI API call every
 *  ~30ms, which is both prohibitively expensive and not how any responsible use of this feature
 *  should work. Explicitly NOT a substitute for a parent's own supervision — see the disclaimer
 *  in the overlay. */
@Composable
fun CameraScanScreen(isThai: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var paused by remember { mutableStateOf(false) }
    var advice by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val capture = ImageCapture.Builder().build()
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        }
                        imageCapture = capture
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isThai) "ต้องอนุญาตให้ใช้กล้องก่อน" else "Camera permission is needed",
                    color = Color.White
                )
            }
        }

        // Rate-limited scan loop — see SCAN_INTERVAL_MS note above.
        LaunchedEffect(imageCapture, paused) {
            val capture = imageCapture ?: return@LaunchedEffect
            while (isActive) {
                if (!paused) {
                    analyzing = true
                    val bytes = capture.captureJpegBytesOrNull()
                    if (bytes != null) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        advice = SurroundingsScanService.analyzeSurroundings(base64, isThai) ?: advice
                    }
                    analyzing = false
                }
                delay(SCAN_INTERVAL_MS)
            }
        }

        Row(
            Modifier.fillMaxWidth().systemBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(0.4f))) {
                Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
            }
            IconButton(onClick = { paused = !paused }, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(0.4f))) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, tint = Color.White)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(0.65f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (analyzing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Text(
                        advice ?: (if (paused) (if (isThai) "หยุดชั่วคราว" else "Paused") else (if (isThai) "กำลังสแกน..." else "Scanning...")),
                        color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (isThai) "วิเคราะห์ทุก ${SCAN_INTERVAL_MS / 1000} วินาที — ไม่ใช่คำแนะนำทางการแพทย์หรือความปลอดภัยที่สมบูรณ์ ผู้ปกครองต้องดูแลลูกด้วยตนเองเสมอ"
                else "Analyzes every ${SCAN_INTERVAL_MS / 1000}s — not a substitute for actual supervision. Always watch your child yourself.",
                color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Bridges CameraX's callback-based capture API to a suspend call. Returns raw JPEG bytes (the
 *  in-memory ImageCapture.OnImageCapturedCallback path already delivers JPEG-encoded planes by
 *  default, so no bitmap decode/re-encode round trip is needed) or null on failure. */
internal suspend fun ImageCapture.captureJpegBytesOrNull(): ByteArray? = suspendCancellableCoroutine { cont ->
    takePicture(
        ContextCompat.getMainExecutor(NongRakApp.instance),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                if (cont.isActive) cont.resume(bytes)
            }
            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    )
}

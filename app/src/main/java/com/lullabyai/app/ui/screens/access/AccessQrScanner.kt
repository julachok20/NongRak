@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner overlay (CameraX + ML Kit on YUV frames).
 * Camera permission must be granted **before** showing this composable — never request it from
 * inside a [Dialog], which crashes on many devices.
 */
@Composable
fun AccessQrScannerOverlay(
    isThai: Boolean,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val handled = remember { AtomicBoolean(false) }
    val onCodeState by rememberUpdatedState(onCode)
    var scanning by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            runCatching { barcodeScanner.close() }
            analyzerExecutor.shutdown()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "ปิด" else "Close")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (isThai) "สแกน QR Code" else "Scan QR Code",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(72.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                cameraProvider = provider
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    // Default YUV_420_888 — required for ML Kit InputImage.fromMediaImage.
                                    .build()
                                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                    if (handled.get()) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    decodeFrameMlKit(
                                        imageProxy = imageProxy,
                                        scanner = barcodeScanner,
                                        mainExecutor = mainExecutor,
                                        onDecoded = { text ->
                                            if (handled.compareAndSet(false, true)) {
                                                mainExecutor.execute {
                                                    scanning = false
                                                    onCodeState(text.trim())
                                                }
                                            }
                                        }
                                    )
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }.onFailure { e ->
                                mainExecutor.execute {
                                    cameraError = e.message ?: "camera_bind_failed"
                                }
                            }
                        }, mainExecutor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (scanning && cameraError == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .size(28.dp),
                        strokeWidth = 3.dp
                    )
                }
            }

            Text(
                when {
                    cameraError != null ->
                        if (isThai) "เปิดกล้องไม่ได้ — ลองพิมพ์รหัส 6 หลักแทน"
                        else "Could not open camera — try typing the 6-character code instead"
                    scanning ->
                        if (isThai) "จ่อ QR ให้อยู่กลางจอ — สแกนแล้วจะส่งคำขออัตโนมัติ"
                        else "Center the QR code — a sync request will be sent automatically"
                    else ->
                        if (isThai) "สแกนสำเร็จ กำลังส่งคำขอ…"
                        else "Scan OK — sending request…"
                },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (cameraError != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun decodeFrameMlKit(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    mainExecutor: java.util.concurrent.Executor,
    onDecoded: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val rotation = imageProxy.imageInfo.rotationDegrees
    val input = InputImage.fromMediaImage(mediaImage, rotation)
    scanner.process(input)
        .addOnSuccessListener(mainExecutor) { barcodes ->
            val raw = barcodes.firstOrNull()?.rawValue?.trim()
            if (!raw.isNullOrBlank()) {
                onDecoded(raw)
            } else {
                decodeFrameZxingYuv(imageProxy)?.let(onDecoded)
            }
        }
        .addOnFailureListener(mainExecutor) {
            decodeFrameZxingYuv(imageProxy)?.let(onDecoded)
        }
        .addOnCompleteListener { imageProxy.close() }
}

/** ZXing decode from CameraX YUV plane (fallback when ML Kit misses a frame). */
private fun decodeFrameZxingYuv(image: ImageProxy): String? {
    if (image.planes.isEmpty()) return null
    val plane = image.planes[0]
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null
    val yBytes = ByteArray(width * height)
    if (rowStride == width) {
        buffer.get(yBytes, 0, minOf(buffer.remaining(), yBytes.size))
    } else {
        var offset = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(yBytes, offset, width)
            offset += width
        }
    }
    val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.TRY_HARDER, true)
    }
    return try {
        val source = PlanarYUVLuminanceSource(yBytes, width, height, 0, 0, width, height, false)
        MultiFormatReader().apply { setHints(hints) }
            .decode(BinaryBitmap(HybridBinarizer(source)))
            .text
    } catch (_: Exception) {
        null
    }
}

/** @deprecated Use [AccessQrScannerOverlay] — Dialog + in-composable permission request crashes. */
@Composable
fun AccessQrScannerDialog(
    isThai: Boolean,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit
) = AccessQrScannerOverlay(isThai, onCode, onDismiss)

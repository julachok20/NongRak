package com.lullabyai.app.ui.screens.diary

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fifteen seconds, and not by taste.
 *
 * `diary-media` has a hard 10MB ceiling (`file_size_limit` in the 20260725 migration), enforced by
 * Storage, so a longer clip records fine and is then refused on upload. The design offers a 60s
 * option; at any watchable bitrate that clip cannot be saved, and a button that records something
 * the app will not keep is worse than no button.
 */
private const val MAX_CLIP_SEC = 15

/**
 * ~4 Mbps, which puts 15 seconds of 720p plus audio at roughly 8MB - inside the ceiling with room
 * to spare on devices whose encoders overshoot.
 *
 * Capping the bitrate rather than dropping to SD keeps the resolution: this is a keepsake, and a
 * first step remembered at 480p is a worse thing to still have in ten years.
 */
private const val TARGET_BITRATE_BPS = 4_000_000

/** Self-timer options, in seconds. 0 means start immediately. */
private val TIMER_OPTIONS_SEC = listOf(0, 3, 10)

/**
 * Records a clip in-app, then hands it straight to the editor.
 *
 * Deliberately does NOT offer a capture-speed control, though the Stitch design shows one. CameraX
 * records at the sensor's rate; a "0.5x" button would have to either drop frames on the way in or
 * quietly retime afterwards. Retiming in the editor gives the same picture, keeps one code path,
 * and - the real reason - stays undoable. A recording made at the wrong speed cannot be taken back.
 *
 * Beauty and เทมเพลต from that design are not here either: a face-smoothing shader and a template
 * system are each their own feature, and a button that does nothing is worse than no button.
 */
@Composable
fun NongRakVideoCaptureScreen(
    isThai: Boolean,
    onDismiss: () -> Unit,
    onCaptured: (Uri) -> Unit,
    onPickInstead: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasCamera = granted[Manifest.permission.CAMERA] ?: hasCamera
        hasMic = granted[Manifest.permission.RECORD_AUDIO] ?: hasMic
    }

    LaunchedEffect(Unit) {
        if (!hasCamera || !hasMic) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchOn by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Binding lives in an effect keyed on the lens, not in the AndroidView factory. The factory
    // runs once, so binding there meant Flip changed the state, highlighted the button, and left
    // the same camera running.
    LaunchedEffect(previewView, lensFacing, hasCamera) {
        val view = previewView ?: return@LaunchedEffect
        if (!hasCamera) return@LaunchedEffect
        runCatching {
            // getInstance().get() blocks until the provider is ready; off the main thread so the
            // preview surface is not held up behind it.
            val provider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(context).get()
            }
            val preview = Preview.Builder().build()
                .also { it.surfaceProvider = view.surfaceProvider }
            val recorder = Recorder.Builder()
                // HD, not UHD: a 4K clip is hundreds of MB to encode and then re-encode again in
                // the editor, on a phone held one-handed.
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .setTargetVideoEncodingBitRate(TARGET_BITRATE_BPS)
                .build()
            val capture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                capture
            )
            videoCapture = capture
        }.onFailure {
            errorText = if (isThai) "เปิดกล้องไม่ได้: ${it.message}"
            else "Could not open the camera: ${it.message}"
        }
    }

    // Torch belongs to the bound camera, not to our state, so it has to be pushed whenever either
    // changes - including after a lens flip, which rebinds and silently drops it.
    LaunchedEffect(camera, torchOn) {
        runCatching { camera?.cameraControl?.enableTorch(torchOn) }
    }

    // Stop cleanly if the parent backs out mid-take, or the file is left half-muxed and unplayable.
    DisposableEffect(Unit) {
        onDispose { runCatching { recording?.stop() } }
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "nongrak_capture_${System.currentTimeMillis()}.mp4")

        val options = FileOutputOptions.Builder(file)
            // The recorder enforces the cap itself. A coroutine racing the encoder drifts, and the
            // drift always lands on the wrong side - a 16-second "15 second" clip.
            .setDurationLimitMillis(MAX_CLIP_SEC * 1000L)
            .build()

        val pending = capture.output
            .prepareRecording(context, options)
            .apply { if (hasMic) withAudioEnabled() }

        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                    elapsedMs = 0L
                }

                is VideoRecordEvent.Status ->
                    elapsedMs = event.recordingStats.recordedDurationNanos / 1_000_000L

                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    recording = null
                    // Hitting the cap reports as an error but produces a complete file, so it is
                    // only a real failure when there is nothing usable on disk.
                    val fatal = event.hasError() &&
                        event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED &&
                        event.error != VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                    if (fatal || !file.exists() || file.length() == 0L) {
                        // Names the code the recorder gave us rather than a shrug.
                        errorText = if (isThai) "บันทึกวิดีโอไม่สำเร็จ (รหัส ${event.error})"
                        else "Recording failed (code ${event.error})"
                        runCatching { file.delete() }
                    } else {
                        onCaptured(
                            FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                        )
                    }
                }
            }
        }
    }

    // The self-timer counts down out here rather than inside startRecording, so the parent can see
    // the number and still cancel by tapping the shutter again.
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
            if (countdown == 0) startRecording()
        }
    }

    Dialog(
        onDismissRequest = { if (!isRecording) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCamera) {
                AndroidView(
                    factory = { ctx -> PreviewView(ctx).also { previewView = it } },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isThai) "ต้องอนุญาตให้ใช้กล้องก่อนถึงจะถ่ายได้"
                        else "Camera permission is needed to record",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            CaptureTopBar(
                isThai = isThai,
                recording = isRecording,
                elapsedMs = elapsedMs,
                onClose = { if (!isRecording) onDismiss() }
            )

            CaptureSideRail(
                isThai = isThai,
                enabled = !isRecording,
                lensFacing = lensFacing,
                torchOn = torchOn,
                timerSeconds = timerSeconds,
                onFlip = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                onToggleTorch = { torchOn = !torchOn },
                onCycleTimer = {
                    val next = (TIMER_OPTIONS_SEC.indexOf(timerSeconds) + 1) % TIMER_OPTIONS_SEC.size
                    timerSeconds = TIMER_OPTIONS_SEC[next]
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            )

            CaptureBottomBar(
                isThai = isThai,
                isRecording = isRecording,
                progress = elapsedMs / (MAX_CLIP_SEC * 1000f),
                enabled = hasCamera,
                onShutter = {
                    when {
                        isRecording -> recording?.stop()
                        countdown > 0 -> countdown = 0
                        timerSeconds > 0 -> countdown = timerSeconds
                        else -> startRecording()
                    }
                },
                onPickInstead = onPickInstead,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (countdown > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$countdown",
                        color = Color.White,
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            errorText?.let { message ->
                Surface(
                    color = Color(0xFFB3261E),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 84.dp, start = 20.dp, end = 20.dp)
                        .clickable { errorText = null }
                ) {
                    Text(
                        message,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureTopBar(
    isThai: Boolean,
    recording: Boolean,
    elapsedMs: Long,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, enabled = !recording) {
            Icon(
                Icons.Filled.Close,
                if (isThai) "ปิด" else "Close",
                tint = if (recording) Color.White.copy(0.3f) else Color.White
            )
        }

        if (recording) {
            Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFFB3261E)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Text(
                        "%d / %ds".format(elapsedMs / 1000, MAX_CLIP_SEC),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun CaptureSideRail(
    isThai: Boolean,
    enabled: Boolean,
    lensFacing: Int,
    torchOn: Boolean,
    timerSeconds: Int,
    onFlip: () -> Unit,
    onToggleTorch: () -> Unit,
    onCycleTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailButton(
            icon = Icons.Filled.FlipCameraAndroid,
            label = if (isThai) "สลับ" else "Flip",
            enabled = enabled,
            active = lensFacing == CameraSelector.LENS_FACING_FRONT,
            onClick = onFlip
        )
        RailButton(
            icon = Icons.Filled.Timer,
            label = if (timerSeconds == 0) (if (isThai) "จับเวลา" else "Timer") else "${timerSeconds}s",
            enabled = enabled,
            active = timerSeconds > 0,
            onClick = onCycleTimer
        )
        RailButton(
            icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            label = if (isThai) "แฟลช" else "Flash",
            enabled = enabled,
            active = torchOn,
            onClick = onToggleTorch
        )
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.Black.copy(alpha = 0.35f))
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                tint = when {
                    !enabled -> Color.White.copy(0.3f)
                    active -> Color.Black
                    else -> Color.White
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CaptureBottomBar(
    isThai: Boolean,
    isRecording: Boolean,
    progress: Float,
    enabled: Boolean,
    onShutter: () -> Unit,
    onPickInstead: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isRecording) {
            // A label, not a chooser: there is only one length the app can actually save.
            Text(
                if (isThai) "สูงสุด ${MAX_CLIP_SEC} วินาที" else "Up to ${MAX_CLIP_SEC}s",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }

        Box(contentAlignment = Alignment.Center) {
            if (isRecording) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = Color(0xFFB3261E),
                    trackColor = Color.White.copy(0.25f),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(84.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(if (isRecording) RoundedCornerShape(10.dp) else CircleShape)
                    .background(if (enabled) Color(0xFFB3261E) else Color.Gray)
                    .clickable(enabled = enabled) { onShutter() }
            )
        }

        if (!isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onPickInstead() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    if (isThai) "เลือกจากคลัง" else "Upload",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

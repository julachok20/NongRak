package com.lullabyai.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val VIDEO_FRAME_INTERVAL_MS = 5_000L

private enum class CallState { LISTENING, THINKING, SPEAKING, IDLE }

/**
 * Full-screen "call" with the AI: continuous listen → send → speak → re-listen loop, so the parent
 * never has to type or tap during a turn. This is turn-based, not true low-latency streaming voice
 * (the AI chain is request/response, not a live audio stream) — see AiAvatar.kt's isSpeaking note.
 * "Video call" = the same screen with a self-facing camera preview; while on, the latest captured
 * frame is attached to the next message sent, so the AI can also see what the camera sees.
 */
@Composable
fun CallScreen(chatViewModel: ChatViewModel, childViewModel: ChildViewModel, isThai: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val chatState by chatViewModel.uiState.collectAsState()
    val childState by childViewModel.uiState.collectAsState()
    val aiVoice = rememberAiVoice()

    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAudioPermission = it }
    LaunchedEffect(Unit) { if (!hasAudioPermission) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
    var videoEnabled by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var latestFrame by remember { mutableStateOf<ByteArray?>(null) }

    var callState by remember { mutableStateOf(CallState.IDLE) }
    var muted by remember { mutableStateOf(false) }
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }

    val recognizerIntent = remember {
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
        }
    }
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    fun restartListening() {
        if (muted) { callState = CallState.IDLE; return }
        callState = CallState.LISTENING
        runCatching { recognizer.startListening(recognizerIntent) }
    }

    DisposableEffect(Unit) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) {
                    restartListening()
                } else {
                    callState = CallState.THINKING
                    chatViewModel.sendMessage(text, childState.child, imageBytes = latestFrame, imageMime = if (latestFrame != null) "image/jpeg" else null)
                }
            }
            override fun onError(error: Int) = restartListening()
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer.destroy() }
    }

    LaunchedEffect(hasAudioPermission) { if (hasAudioPermission) restartListening() }

    // AI reply arrived while we were "thinking" → speak it.
    LaunchedEffect(chatState.messages) {
        val latest = chatState.messages.lastOrNull { it.role != "user" && it.content.isNotBlank() }
        if (callState == CallState.THINKING && latest != null && latest.id != lastSpokenMessageId) {
            lastSpokenMessageId = latest.id
            callState = CallState.SPEAKING
            aiVoice.speak(latest.content)
        }
    }
    // AI finished speaking → listen again.
    LaunchedEffect(aiVoice.isSpeaking) {
        if (!aiVoice.isSpeaking && callState == CallState.SPEAKING) restartListening()
    }

    // Video mode: periodic frame capture, attached to the next message sent (see onResults above).
    LaunchedEffect(imageCapture, videoEnabled) {
        val capture = imageCapture ?: return@LaunchedEffect
        while (isActive) {
            latestFrame = if (videoEnabled) capture.captureJpegBytesOrNull() else null
            delay(VIDEO_FRAME_INTERVAL_MS)
        }
    }

    fun toggleMute() {
        muted = !muted
        if (muted) { runCatching { recognizer.cancel() }; if (callState == CallState.LISTENING) callState = CallState.IDLE }
        else if (callState == CallState.IDLE) restartListening()
    }

    fun toggleVideo() {
        if (!hasCameraPermission) { cameraPermissionLauncher.launch(Manifest.permission.CAMERA); return }
        videoEnabled = !videoEnabled
    }

    fun endCall() {
        aiVoice.stop()
        runCatching { recognizer.cancel() }
        onBack()
    }

    val statusText = when {
        muted -> if (isThai) "ปิดไมค์อยู่" else "Muted"
        callState == CallState.LISTENING -> if (isThai) "กำลังฟัง..." else "Listening..."
        callState == CallState.THINKING -> if (isThai) "กำลังคิด..." else "Thinking..."
        callState == CallState.SPEAKING -> if (isThai) "กำลังพูด..." else "Speaking..."
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = { endCall() }, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            AiAvatarView(isSpeaking = aiVoice.isSpeaking, modifier = Modifier.size(180.dp))
            Spacer(Modifier.height(20.dp))
            Text(statusText, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (callState == CallState.SPEAKING) {
                Spacer(Modifier.height(12.dp))
                Text(
                    chatState.messages.lastOrNull { it.role != "user" }?.content.orEmpty(),
                    color = Color.White.copy(0.75f), style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { toggleMute() }, modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(0.15f))) {
                    Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (isThai) "ปิด/เปิดไมค์" else "Mute", tint = Color.White)
                }
                IconButton(onClick = { endCall() }, modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE53935))) {
                    Icon(Icons.Filled.CallEnd, if (isThai) "วางสาย" else "End call", tint = Color.White)
                }
                IconButton(onClick = { toggleVideo() }, modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(0.15f))) {
                    Icon(if (videoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff, if (isThai) "เปิด/ปิดวิดีโอ" else "Video", tint = Color.White)
                }
            }
        }

        // Self-facing camera preview, video-call-style corner box.
        if (videoEnabled && hasCameraPermission) {
            AndroidView(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 20.dp).size(110.dp, 150.dp).clip(RoundedCornerShape(14.dp)),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val capture = ImageCapture.Builder().build()
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                        }
                        imageCapture = capture
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }
    }
}

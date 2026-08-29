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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

private val VIDEO_FRAME_INTERVAL_MS = 5_000L

private enum class CallState { LISTENING, THINKING, SPEAKING, IDLE }

/**
 * How long a real pause lasts before the turn is treated as finished.
 *
 * Android's recogniser is told to segment after a short silence, which makes it return often; this
 * is what actually decides the turn is over. Splitting the two is the point: a parent who pauses
 * to think, or to look at the baby mid-sentence, keeps their turn instead of having half a
 * question sent. Any new speech cancels it, so you can keep going as long as you like.
 */
private const val TURN_END_SILENCE_MS = 900L

/**
 * Longest a turn may run before it is sent regardless.
 *
 * A backstop, not a policy. If speech detection keeps reporting activity — a television, a sibling,
 * a fan — the silence clock never expires and the parent waits forever. Better to answer what was
 * heard than to sit there.
 */
private const val TURN_MAX_MS = 12_000L

/**
 * Full-screen "call" with the AI: continuous listen → send → speak → re-listen loop, so the parent
 * never has to type or tap during a turn. This is turn-based, not true low-latency streaming voice
 * (the AI chain is request/response, not a live audio stream) — see AiAvatar.kt's isSpeaking note.
 *
 * Layout follows Stitch "AI Call Simulation (Large User Video)": the parent's own camera fills the
 * whole screen, the mascot sits in a small ring top-right, a wave-bar pill shows speaking status
 * and a floating glass pill holds the call controls. On top of that mock:
 *  - an INSTRUCTION panel over the video, large enough for the parent to read or point at while
 *    following the AI's steps (toggle with the notes button);
 *  - an APPROVAL banner below it whenever the AI proposes an action ("action mode") — nothing is
 *    written until the parent taps อนุมัติ, same guarantee as the chat screen's confirm card.
 * While video is off the mascot stays centre-stage, as before.
 */
@Composable
fun CallScreen(chatViewModel: ChatViewModel, childViewModel: ChildViewModel, isThai: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current

    // The same companion the parent is chatting with answers the call. Read from the one place the
    // choice lives, so switching in chat is felt here without anything being passed along.
    val callMascotKey by remember {
        com.lullabyai.app.data.settings.SettingsManager(context).selectedMascot
    }.collectAsState(initial = null)
    val callMascot = MascotId.fromKey(callMascotKey)
    val lifecycleOwner = LocalLifecycleOwner.current
    val chatState by chatViewModel.uiState.collectAsState()
    val childState by childViewModel.uiState.collectAsState()
    val tts = com.lullabyai.app.util.rememberTtsSpeaker()

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
    var speakerOn by remember { mutableStateOf(true) }
    // Hidden by default: during a call the answer is spoken, and a wall of text over the
    // mascot is something to dismiss rather than read. A button opens it.
    var showInstructions by remember { mutableStateOf(false) }
    var pendingUtterance by remember { mutableStateOf("") }
    /** When speech was last actually heard. A clock, not a countdown — see the ticker below. */
    var lastVoiceAtMs by remember { mutableStateOf(0L) }
    var turnStartedAtMs by remember { mutableStateOf(0L) }
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }
    var userSpeaking by remember { mutableStateOf(false) }
    var partialSpeech by remember { mutableStateOf("") }

    val recognizerIntent = remember {
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isThai) "th-TH" else "en-US")
            // Short, so the recogniser hands back a chunk quickly rather than owning the turn.
            // Whether the turn is FINISHED is decided by TURN_END_SILENCE_MS below, which survives
            // a pause; 1800 here meant one pause ended the sentence, and adding a wait on top of
            // it would have made every reply feel a second slower.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    fun restartListening() {
        if (muted) {
            if (callState == CallState.LISTENING) callState = CallState.IDLE
            return
        }
        if (callState == CallState.THINKING) return
        // After TTS ends callState may still be SPEAKING until we reset it — allow mic reopen then.
        if (callState == CallState.SPEAKING && tts.isSpeaking) return
        partialSpeech = ""
        callState = CallState.LISTENING
        runCatching { recognizer.startListening(recognizerIntent) }
    }

    fun interruptAiAndListen() {
        tts.stop()
        lastVoiceAtMs = 0L
        turnStartedAtMs = 0L
        runCatching { recognizer.cancel() }
        partialSpeech = ""
        // Interrupting starts a fresh question, not an addition to the one just answered.
        pendingUtterance = ""
        callState = CallState.LISTENING
        if (!muted) runCatching { recognizer.startListening(recognizerIntent) }
    }

    DisposableEffect(Unit) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                userSpeaking = false
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?: partialSpeech
                partialSpeech = ""
                // A chunk, not necessarily the whole turn. It joins what has been said so far and
                // the mic reopens immediately, so a pause to think does not send half a question.
                if (!text.isNullOrBlank()) {
                    if (pendingUtterance.isBlank()) turnStartedAtMs = System.currentTimeMillis()
                    pendingUtterance = (pendingUtterance + " " + text).trim()
                    lastVoiceAtMs = System.currentTimeMillis()
                }
                restartListening()
            }
            override fun onError(error: Int) {
                if (callState == CallState.SPEAKING || callState == CallState.THINKING) return
                val capturedPartial = partialSpeech
                userSpeaking = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Heard nothing THIS segment, which during a pause is normal rather than a
                        // failure. Keep whatever was captured, reopen the mic, and let the turn
                        // timer decide whether the parent has actually finished.
                        if (capturedPartial.isNotBlank()) {
                            partialSpeech = ""
                            if (pendingUtterance.isBlank()) turnStartedAtMs = System.currentTimeMillis()
                            pendingUtterance = (pendingUtterance + " " + capturedPartial).trim()
                            lastVoiceAtMs = System.currentTimeMillis()
                        }
                        restartListening()
                    }
                    else -> restartListening()
                }
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {
                userSpeaking = true
                lastVoiceAtMs = System.currentTimeMillis()
                if (callState == CallState.SPEAKING) interruptAiAndListen()
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { userSpeaking = false }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        partialSpeech = it
                        // Words are still arriving, so the parent is mid-sentence.
                        lastVoiceAtMs = System.currentTimeMillis()
                    }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer.destroy() }
    }

    LaunchedEffect(hasAudioPermission) { if (hasAudioPermission) restartListening() }

    /**
     * Decides when the parent has finished, by asking how long ago speech was last heard.
     *
     * This replaced a countdown that was cancelled and restarted from the recogniser callbacks.
     * Android emits ERROR_NO_MATCH roughly once a second while nobody is talking, and each one
     * restarted the countdown, so it never reached the end and the AI never took its turn. A clock
     * cannot be reset by an event that carries no speech, which is the whole point: only real
     * words move `lastVoiceAtMs`, and silence simply accumulates.
     */
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(120)
            if (callState != CallState.LISTENING) continue
            if (userSpeaking) continue
            val text = pendingUtterance.trim()
            if (text.isBlank()) continue

            val now = System.currentTimeMillis()
            val quietFor = now - lastVoiceAtMs
            val turnRanFor = now - turnStartedAtMs
            if (quietFor < TURN_END_SILENCE_MS && turnRanFor < TURN_MAX_MS) continue

            pendingUtterance = ""
            partialSpeech = ""
            lastVoiceAtMs = 0L
            turnStartedAtMs = 0L
            runCatching { recognizer.cancel() }
            callState = CallState.THINKING
            chatViewModel.sendMessage(
                text,
                // Naming a child in the sentence asks about THAT child, without making the parent
                // hang up and switch first. The app's own selection is left alone.
                com.lullabyai.app.util.ChildMention.resolve(
                    text, childState.children, childState.child
                ),
                imageBytes = latestFrame,
                imageMime = if (latestFrame != null) "image/jpeg" else null,
                spoken = true
            )
        }
    }

    LaunchedEffect(callState) {
        if (callState == CallState.SPEAKING || callState == CallState.THINKING) {
            runCatching { recognizer.cancel() }
        }
    }

    val latestAiMessage = chatState.messages.lastOrNull { it.role != "user" && it.content.isNotBlank() }
    // "Action mode": the AI proposed a write and is waiting for the parent's approval.
    val pendingAction = chatState.messages.lastOrNull { it.agentAction != null && it.actionStatus == null }

    // AI reply arrived while we were "thinking" → speak it (unless the speaker is off).
    LaunchedEffect(chatState.messages) {
        val latest = latestAiMessage
        if (callState == CallState.THINKING && latest != null && latest.id != lastSpokenMessageId) {
            lastSpokenMessageId = latest.id
            if (speakerOn) {
                callState = CallState.SPEAKING
                tts.speak(
                    latest.content, isThai,
                    chatState.mode.speechRate, chatState.mode.speechPitch,
                    // A call is a conversation: start talking now rather than after a round trip.
                    instant = true
                )
            } else {
                restartListening()
            }
        }
    }
    // AI finished speaking → user's turn to speak.
    LaunchedEffect(tts.isSpeaking) {
        if (!tts.isSpeaking && callState == CallState.SPEAKING) {
            callState = CallState.IDLE
            restartListening()
        }
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
        if (muted) {
            runCatching { recognizer.cancel() }
            if (callState == CallState.LISTENING) callState = CallState.IDLE
        } else {
            if (callState == CallState.SPEAKING) interruptAiAndListen()
            else if (callState == CallState.IDLE) restartListening()
        }
    }

    fun toggleVideo() {
        if (!hasCameraPermission) { cameraPermissionLauncher.launch(Manifest.permission.CAMERA); return }
        videoEnabled = !videoEnabled
    }

    fun endCall() {
        tts.stop()
        runCatching { recognizer.cancel() }
        onBack()
    }

    val statusText = when {
        muted -> if (isThai) "ปิดไมค์อยู่" else "Muted"
        callState == CallState.LISTENING -> if (isThai) "กำลังฟัง..." else "Listening..."
        callState == CallState.THINKING -> if (isThai) "กำลังคิด..." else "Thinking..."
        callState == CallState.SPEAKING -> if (isThai) "น้องรัก AI กำลังพูด..." else "NONGRAK AI IS SPEAKING..."
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
        // The parent's own camera, full screen (Stitch "Large User Video").
        if (videoEnabled && hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
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
            // Keeps white overlay text readable over any camera scene.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.25f)))
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column {
                    IconButton(onClick = { endCall() }) {
                        Icon(Icons.Filled.KeyboardArrowDown, if (isThai) "ย่อ" else "Collapse", tint = Color.White)
                    }
                    IconButton(onClick = { showInstructions = !showInstructions }) {
                        Icon(
                            Icons.Filled.Notes,
                            if (isThai) "แสดง/ซ่อนคำแนะนำ" else "Show/hide instructions",
                            tint = if (showInstructions) Primary else Color.White
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // Mascot: small ring top-right while the video is large, centre-stage otherwise.
                if (videoEnabled) {
                    Box(
                        Modifier.size(128.dp).clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color.White, CircleShape)
                    ) {
                        com.lullabyai.app.ui.mascot.Mascot(
                            art = callMascot.rigArt(),
                            mood = if (tts.isSpeaking) com.lullabyai.app.ui.mascot.MascotMood.SPEAKING
                            else com.lullabyai.app.ui.mascot.MascotMood.IDLE,
                            speaking = tts.isSpeaking,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            ChatModeChips(
                mode = chatState.mode,
                onSelect = { chatViewModel.setMode(it) },
                isThai = isThai,
                onDark = true
            )

            // Tap the center to interrupt AI speech and take the user's turn to speak.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (callState == CallState.SPEAKING || callState == CallState.THINKING) {
                            interruptAiAndListen()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!videoEnabled) {
                    com.lullabyai.app.ui.mascot.Mascot(
                        art = callMascot.rigArt(),
                        mood = if (tts.isSpeaking) com.lullabyai.app.ui.mascot.MascotMood.SPEAKING
                        else com.lullabyai.app.ui.mascot.MascotMood.IDLE,
                        speaking = tts.isSpeaking,
                        modifier = Modifier.size(180.dp)
                    )
                }
            }

            // Shown whenever a tap would actually do something, camera or not. Previously this
            // only appeared with video on, so in the common case the affordance was invisible and
            // the parent had no way to know they could cut in.
            if (callState == CallState.SPEAKING || callState == CallState.THINKING) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isThai) "แตะเพื่อขัดจังหวะและพูดต่อได้เลย"
                    else "Tap to interrupt — you can keep talking",
                    color = Color.White.copy(0.75f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // The reply is spoken, so the text is opt-in. During a call a wall of text over the
            // mascot is something to dismiss, not something to read.
            if (!showInstructions && latestAiMessage?.content?.isNotBlank() == true &&
                callState != CallState.THINKING
            ) {
                TextButton(onClick = { showInstructions = true }) {
                    Icon(
                        Icons.Filled.Article, null,
                        tint = Color.White.copy(0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isThai) "ดูข้อความ" else "See transcript",
                        color = Color.White.copy(0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Instruction overlay — big, readable, pointable.
            if (showInstructions && latestAiMessage?.content?.isNotBlank() == true) {
                Surface(
                    color = Color.White.copy(0.88f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.TipsAndUpdates, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(
                                if (isThai) "คำแนะนำจากน้องรัก AI" else "Instructions from NongRak AI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showInstructions = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, if (isThai) "ปิด" else "Close", tint = Color(0xFF5C5F66), modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            latestAiMessage?.content.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF1A1C1C),
                            modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Action mode → approval banner, directly below the instruction area.
            pendingAction?.let { message ->
                val action = message.agentAction!!
                Surface(
                    color = Color.White.copy(0.94f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Bolt, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(
                                action.confirmText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1C)
                            )
                        }
                        Text(
                            if (isThai) "คุณอนุมัติการดำเนินการนี้หรือไม่?" else "Do you approve this action?",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF444652)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { chatViewModel.cancelAgentAction(message.id) },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (isThai) "ไม่อนุมัติ" else "Deny") }
                            Button(
                                onClick = { childState.child?.id?.let { chatViewModel.confirmAgentAction(message.id, it) } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                enabled = childState.child?.id != null
                            ) { Text(if (isThai) "อนุมัติ" else "Approve", color = Color.White) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Speaking status pill (wave bars, per Stitch).
            if (statusText.isNotBlank()) {
                Surface(color = Color.White.copy(0.55f), shape = CircleShape) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (callState == CallState.SPEAKING) VoiceWave()
                        Text(
                            statusText,
                            color = Primary, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (isThai && !tts.thaiVoiceAvailable) {
                Text(
                    "อุปกรณ์นี้ยังไม่มีเสียงพูดภาษาไทย จึงอ่านออกเสียงไม่ได้ (ติดตั้งที่ ตั้งค่า > เสียงพูด)",
                    color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
            }

            // Floating glass control pill.
            Surface(color = Color.White.copy(0.75f), shape = CircleShape) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { toggleVideo() },
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(if (videoEnabled) Primary.copy(0.18f) else Color.Black.copy(0.06f))
                    ) {
                        Icon(
                            if (videoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            if (isThai) "เปิด/ปิดวิดีโอ" else "Video",
                            tint = if (videoEnabled) Primary else Color(0xFF444652)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (callState == CallState.SPEAKING || callState == CallState.THINKING) interruptAiAndListen()
                            else toggleMute()
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(0.06f))
                    ) {
                        Icon(
                            when {
                                muted -> Icons.Filled.MicOff
                                callState == CallState.SPEAKING -> Icons.Filled.RecordVoiceOver
                                else -> Icons.Filled.Mic
                            },
                            if (isThai) {
                                when {
                                    callState == CallState.SPEAKING -> "ขัดจังหวะเพื่อพูด"
                                    muted -> "เปิดไมค์"
                                    else -> "ปิดไมค์"
                                }
                            } else {
                                when {
                                    callState == CallState.SPEAKING -> "Interrupt to speak"
                                    muted -> "Unmute"
                                    else -> "Mute"
                                }
                            },
                            tint = when {
                                muted -> Color(0xFFBA1A1A)
                                callState == CallState.SPEAKING -> Primary
                                else -> Color(0xFF444652)
                            }
                        )
                    }
                    IconButton(
                        onClick = { endCall() },
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFBA1A1A))
                    ) {
                        Icon(Icons.Filled.CallEnd, if (isThai) "วางสาย" else "End call", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            speakerOn = !speakerOn
                            if (!speakerOn) { tts.stop(); if (callState == CallState.SPEAKING) restartListening() }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(if (speakerOn) Primary.copy(0.18f) else Color.Black.copy(0.06f))
                    ) {
                        Icon(
                            if (speakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            if (isThai) "เปิด/ปิดเสียงพูด" else "Speaker",
                            tint = if (speakerOn) Primary else Color(0xFF444652)
                        )
                    }
                }
            }
        }
    }
}

/** Five bars bouncing while the AI talks — the Stitch mock's .voice-wave. */
@Composable
private fun VoiceWave() {
    val transition = rememberInfiniteTransition(label = "wave")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val h by transition.animateFloat(
                initialValue = 8f, targetValue = 22f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 100), RepeatMode.Reverse),
                label = "bar$i"
            )
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(Primary))
        }
    }
}

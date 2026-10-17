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
import androidx.compose.foundation.BorderStroke
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
import com.lullabyai.app.BuildConfig
import com.lullabyai.app.data.remote.GeminiLiveClient
import com.lullabyai.app.data.remote.NongRakMedicalRouter
import com.lullabyai.app.data.remote.SparkChatClient
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.runtime.snapshotFlow
import com.lullabyai.app.util.VoiceTurnRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    LaunchedEffect(callMascot, isThai) {
        chatViewModel.setCompanion(callMascot.assetPrefix, callMascot.personaPrompt(isThai))
    }
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


    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }

    // Optimistic: try Live, and drop to the turn-based path the moment a token cannot be minted.
    // There is no key on the device to test any more - whether Live is available is something only
    // the server can answer, and it answers by minting a token or refusing to.
    var useGeminiLive by remember { mutableStateOf(true) }
    var liveInstructionText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val recorder = remember { VoiceTurnRecorder() }

    val liveClient = remember(useGeminiLive) {
        if (useGeminiLive) {
            var client: GeminiLiveClient? = null
            client = GeminiLiveClient(
                onTextChunk = { chunk ->
                    liveInstructionText = (liveInstructionText + chunk).takeLast(1200)
                    callState = CallState.SPEAKING
                },
                onInterrupted = {
                    callState = CallState.LISTENING
                },
                onToolCall = { name, callId, args ->
                    if (name == "consult_nongrak_brain") {
                        val symptoms = args["symptoms"]?.jsonPrimitive?.contentOrNull ?: ""
                        coroutineScope.launch {
                            // Emergencies short-circuit the model entirely.
                            //
                            // The turn-based call path has always run through NongRakMedicalRouter;
                            // the Live path was added without it, so a parent saying ลูกชักเกร็ง into
                            // a live call got a chatty answer and no 1669. On a voice call - where
                            // a frightened parent is most likely to be - that is the worst place to
                            // lose this check.
                            val ageMonths = childState.child?.birthdate?.let { birthdate ->
                                runCatching {
                                    val born = java.time.LocalDate.parse(birthdate.take(10))
                                    java.time.temporal.ChronoUnit.MONTHS
                                        .between(born, java.time.LocalDate.now()).toInt()
                                }.getOrNull()
                            }

                            if (NongRakMedicalRouter.isEmergencyForAge(symptoms, ageMonths)) {
                                client?.sendToolResponse(
                                    callId,
                                    NongRakMedicalRouter.emergencyWarning(isThai)
                                )
                                return@launch
                            }

                            val answer = runCatching {
                                SparkChatClient.chat(
                                    question = symptoms,
                                    prompt = "คำถามด่วนจากสายสด Live: $symptoms\nข้อมูลน้อง: ${childState.child?.name ?: "น้อง"} อายุ: ${childState.child?.birthdate ?: ""}",
                                    system = "คุณคือสมองการแพทย์กุมารเวช สรุปคำแนะนำทางการแพทย์สั้นๆ 4-6 บรรทัดที่นำไปใช้ได้ทันที",
                                    childIds = listOfNotNull(childState.child?.id)
                                ).getOrNull()?.text
                            }.getOrNull()

                            // When the consult fails we say so. The previous fallbacks invented
                            // clinical guidance out of nothing ("watch closely, see a doctor if
                            // there is a high fever") - advice with no examination and no source
                            // behind it, delivered in a voice the parent will trust.
                            val reply = answer?.takeIf { it.isNotBlank() }
                                ?: if (isThai) {
                                    "ตอนนี้ยังดึงข้อมูลให้ไม่ได้ค่ะ ถ้ากังวลเรื่องอาการของน้อง " +
                                        "แนะนำให้ปรึกษาแพทย์ และหากเป็นเหตุฉุกเฉินโทร 1669"
                                } else {
                                    "I could not look that up just now. If you are worried about " +
                                        "your child, please speak to a doctor - and call 1669 in " +
                                        "an emergency."
                                }
                            client?.sendToolResponse(callId, reply)
                        }
                    }
                },
                onFallbackRequested = {
                    // Seamless fallback to Spark engine
                    useGeminiLive = false
                }
            )
            client
        } else null
    }

    DisposableEffect(liveClient) {
        liveClient?.connect()
        onDispose {
            liveClient?.disconnect()
        }
    }

    val elapsedSeconds by (liveClient?.elapsedSeconds ?: remember { MutableStateFlow(0L) }).collectAsState()
    val isApproachingLimit by (liveClient?.isApproachingLimit ?: remember { MutableStateFlow(false) }).collectAsState()
    val showRefreshDialog by (liveClient?.showRefreshDialog ?: remember { MutableStateFlow(false) }).collectAsState()

    fun interruptAiAndListen() {
        tts.stop()
        recorder.stop()
        liveClient?.flushAudioOutput()
        callState = CallState.LISTENING
    }

    // Audio capture loop: streams PCM to Gemini Live if enabled, or turns for Spark fallback
    LaunchedEffect(hasAudioPermission, useGeminiLive, muted, callState) {
        if (!hasAudioPermission) return@LaunchedEffect
        if (useGeminiLive && liveClient != null) {
            if (muted) return@LaunchedEffect
            callState = CallState.LISTENING
            withContext(Dispatchers.IO) {
                recorder.streamPcm(
                    shouldStop = { !useGeminiLive || muted || !isActive }
                ) { pcmChunk ->
                    liveClient.sendAudioChunk(pcmChunk)
                }
            }
        } else {
            while (isActive) {
                if (muted || callState == CallState.THINKING || callState == CallState.SPEAKING) {
                    delay(120)
                    continue
                }
                callState = CallState.LISTENING
                val wav = withContext(Dispatchers.IO) {
                    recorder.recordTurn(TURN_END_SILENCE_MS) {
                        muted || callState == CallState.THINKING || callState == CallState.SPEAKING
                    }
                }
                if (wav == null || callState != CallState.LISTENING) continue

                callState = CallState.THINKING
                chatViewModel.sendMessage(
                    "",
                    childState.child,
                    imageBytes = latestFrame,
                    imageMime = if (latestFrame != null) "image/jpeg" else null,
                    spoken = true,
                    audioBytes = wav,
                    audioMime = VoiceTurnRecorder.MIME,
                    family = childState.children
                )
            }
        }
    }

    val latestAiMessage = chatState.messages.lastOrNull { it.role != "user" && it.content.isNotBlank() }
    val pendingAction = chatState.messages.lastOrNull { it.agentAction != null && it.actionStatus == null }

    // Fallback turn-based playback
    LaunchedEffect(chatState.messages) {
        if (useGeminiLive) return@LaunchedEffect
        val latest = latestAiMessage
        if (callState == CallState.THINKING && latest != null && latest.id != lastSpokenMessageId) {
            lastSpokenMessageId = latest.id
            if (speakerOn) {
                callState = CallState.SPEAKING
                tts.speak(
                    latest.content, isThai,
                    chatState.mode.speechRate, chatState.mode.speechPitch,
                    instant = true
                )
            } else {
                callState = CallState.LISTENING
            }
        }
    }
    LaunchedEffect(tts.isSpeaking) {
        if (!useGeminiLive && !tts.isSpeaking && callState == CallState.SPEAKING) {
            callState = CallState.LISTENING
        }
    }

    // Video mode: periodic frame capture, sent to Gemini Live or attached to next turn
    LaunchedEffect(imageCapture, videoEnabled, useGeminiLive) {
        val capture = imageCapture ?: return@LaunchedEffect
        while (isActive) {
            val frame = if (videoEnabled) capture.captureJpegBytesOrNull() else null
            latestFrame = frame
            if (frame != null && useGeminiLive && liveClient != null) {
                liveClient.sendVideoFrame(frame)
            }
            delay(VIDEO_FRAME_INTERVAL_MS)
        }
    }

    fun toggleMute() {
        muted = !muted
        if (muted) {
            recorder.stop()
            if (callState == CallState.LISTENING) callState = CallState.IDLE
        } else {
            if (callState == CallState.SPEAKING) interruptAiAndListen()
            else if (callState == CallState.IDLE) callState = CallState.LISTENING
        }
    }

    fun toggleVideo() {
        if (!hasCameraPermission) { cameraPermissionLauncher.launch(Manifest.permission.CAMERA); return }
        videoEnabled = !videoEnabled
    }

    fun endCall() {
        tts.stop()
        recorder.stop()
        liveClient?.disconnect()
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.weight(1f))

                // Engine Badge & Duration
                Surface(
                    color = Color.Black.copy(0.45f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (useGeminiLive) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                        Text(
                            if (useGeminiLive) "Gemini Live" else "Spark (Local)",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text("•", color = Color.White.copy(0.5f), fontSize = 10.sp)
                        val m = elapsedSeconds / 60
                        val s = elapsedSeconds % 60
                        val timeStr = String.format("%02d:%02d", m, s)
                        Text(
                            timeStr,
                            color = Color.White.copy(0.85f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Mascot: small ring top-right while the video is large, centre-stage otherwise.
                if (videoEnabled) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color.White, CircleShape)
                    ) {
                        com.lullabyai.app.ui.mascot.Mascot(
                            art = callMascot.rigArt(),
                            mood = if (callState == CallState.SPEAKING) com.lullabyai.app.ui.mascot.MascotMood.SPEAKING
                            else com.lullabyai.app.ui.mascot.MascotMood.IDLE,
                            speaking = (callState == CallState.SPEAKING),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Approaching 15-minute limit warning banner
            if (isApproachingLimit) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                        Text(
                            if (isThai) "สายจะหมดอายุในอีก 2-3 นาที (ระบบจะถามเพื่อต่อสายให้อัตโนมัติ)"
                            else "Call will expire in 2-3 mins (system will prompt to resume)",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.labelSmall
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
                        mood = if (callState == CallState.SPEAKING) com.lullabyai.app.ui.mascot.MascotMood.SPEAKING
                        else com.lullabyai.app.ui.mascot.MascotMood.IDLE,
                        speaking = (callState == CallState.SPEAKING),
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

            val displayInstruction = if (useGeminiLive && liveInstructionText.isNotBlank()) {
                liveInstructionText
            } else {
                latestAiMessage?.content.orEmpty()
            }

            // The reply is spoken, so the text is opt-in. During a call a wall of text over the
            // mascot is something to dismiss, not something to read.
            if (!showInstructions && displayInstruction.isNotBlank() && callState != CallState.THINKING) {
                TextButton(onClick = { showInstructions = true }) {
                    Icon(
                        Icons.Filled.Article, null,
                        tint = Color.White.copy(0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isThai) "ดูข้อความ/คำแนะนำ" else "See transcript/instructions",
                        color = Color.White.copy(0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Instruction overlay — big, readable, pointable.
            if (showInstructions && displayInstruction.isNotBlank()) {
                Surface(
                    color = Color.White.copy(0.88f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.TipsAndUpdates, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(
                                if (isThai) "คำแนะนำสดจากน้องรัก AI" else "Live Instructions from NongRak AI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showInstructions = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, if (isThai) "ปิด" else "Close", tint = Color(0xFF5C5F66), modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            displayInstruction,
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
                            if (!speakerOn) {
                                tts.stop()
                                if (callState == CallState.SPEAKING) callState = CallState.LISTENING
                            }
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

        // 10-15 minute session refresh dialog to continue the call seamlessly
        if (showRefreshDialog) {
            AlertDialog(
                onDismissRequest = { liveClient?.dismissRefreshDialog() },
                icon = { Icon(Icons.Filled.Timer, null, tint = Primary) },
                title = {
                    Text(
                        if (isThai) "ต้องการคุยสายต่อหรือไม่?" else "Resume the call?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (isThai) "สายสนทนาสดใกล้ครบเวลา 15 นาที เพื่อความต่อเนื่องของคำแนะนำ เซสชันจะรีเฟรชใหม่โดยคงความจำและบริบทเดิมไว้ค่ะ"
                        else "This live session is approaching the 15-minute limit. Would you like to resume the call to refresh the session while keeping your conversation context?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val summary = liveInstructionText.ifBlank { latestAiMessage?.content.orEmpty() }
                            liveClient?.rolloverSession(summary)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(if (isThai) "คุยสายต่อ (รีเฟรช)" else "Resume call")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { endCall() }) {
                        Text(if (isThai) "วางสาย" else "End call")
                    }
                }
            )
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

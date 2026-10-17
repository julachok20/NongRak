package com.lullabyai.app.data.remote

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.lullabyai.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * High-performance bidirectional client for Google's Gemini Multimodal Live API.
 * Streams real-time audio (16kHz PCM in, 24kHz PCM out) and periodic camera frames (JPEG) over WebSockets.
 *
 * Implements:
 * 1. Low-latency voice streaming with instant barge-in / interruption handling.
 * 2. Visual understanding: Camera frames processed to generate live instructions on screen.
 * 3. 10-15 minute session lifetime watcher with a graceful rollover dialog trigger.
 * 4. Deep-thinking bridge (`consult_nongrak_brain`) allowing Gemma 4 & Typhoon 2.5 to guide responses.
 * 5. Automatic fallback notification if the Live service is unavailable.
 */
class GeminiLiveClient(
    /**
     * Mints a short-lived Gemini Live token, server side.
     *
     * This used to be `BuildConfig.GEMINI_LIVE_API_KEY` - a real Google API key compiled into the
     * APK, extractable by anyone who downloaded the app, and billable to this project. That broke
     * the rule stated in app/build.gradle.kts: AI keys live as Supabase secrets and never reach a
     * device. The key now stays in the `gemini-live-token` edge function and the device receives a
     * single-use token instead.
     *
     * Returns null when no token can be minted, which makes the caller fall back to the
     * turn-based call path rather than failing the call.
     */
    private val mintToken: suspend () -> String? = { GeminiLiveToken.fetch() },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onTextChunk: (String) -> Unit = {},
    private val onInterrupted: () -> Unit = {},
    private val onToolCall: (name: String, callId: String, args: JsonObject) -> Unit = { _, _, _ -> },
    private val onFallbackRequested: (reason: String) -> Unit = {}
) {
    private val tag = "GeminiLiveClient"

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        REFRESHING_SESSION,
        ERROR
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Session duration tracker (in seconds)
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    // Emitted at 12 minutes (720s) to give parents a heads up
    private val _isApproachingLimit = MutableStateFlow(false)
    val isApproachingLimit: StateFlow<Boolean> = _isApproachingLimit.asStateFlow()

    // Emitted at 14 minutes (840s) to show the session refresh dialog
    private val _showRefreshDialog = MutableStateFlow(false)
    val showRefreshDialog: StateFlow<Boolean> = _showRefreshDialog.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var webSocket: WebSocket? = null
    private var timerJob: Job? = null

    // Audio Output Playback (24kHz Mono 16-bit PCM returned by Gemini Live)
    private var audioTrack: AudioTrack? = null
    private val sampleRateOut = 24000
    private var isPlayingAudio = false

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        runCatching {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRateOut,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateOut)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf.coerceAtLeast(8192) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        }.onFailure {
            Log.e(tag, "Failed to initialize AudioTrack", it)
        }
    }

    /**
     * Connects to the Gemini Multimodal Live WebSocket endpoint.
     * @param contextSummary Optional prior conversation summary when rolling over a session.
     */
    fun connect(contextSummary: String? = null) {
        if (_connectionState.value == ConnectionState.CONNECTED) return
        _connectionState.value = ConnectionState.CONNECTING

        // Minting the token is a network call, so connecting is now asynchronous. The websocket is
        // opened inside the coroutine once a token is in hand.
        scope.launch { openSocket(contextSummary) }
    }

    private suspend fun openSocket(contextSummary: String?) {
        val token = runCatching { mintToken() }.getOrNull()
        if (token.isNullOrBlank()) {
            // Named, not silent: the parent gets the turn-based call, and the log says why.
            Log.w(tag, "No Gemini Live token could be minted; falling back to the turn-based call.")
            _connectionState.value = ConnectionState.ERROR
            onFallbackRequested("no_live_token")
            return
        }

        // access_token, not key: this credential is single-use and expires in minutes, so a copy
        // pulled out of a capture later is worth nothing.
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent" +
            "?access_token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(tag, "Connected to Gemini Multimodal Live API")
                _connectionState.value = ConnectionState.CONNECTED
                sendSetupFrame(ws, contextSummary)
                startSessionTimer()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(tag, "WebSocket closing: $code / $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message} (status: ${response?.code})", t)
                _connectionState.value = ConnectionState.ERROR
                stopSessionTimer()
                flushAudioOutput()
                onFallbackRequested(t.message ?: "network_error")
            }
        })
    }

    private fun sendSetupFrame(ws: WebSocket, contextSummary: String?) {
        val extraContext = if (!contextSummary.isNullOrBlank()) {
            "\n\nบริบทก่อนหน้าของสายนี้ (คงความจำต่อเนื่อง):\n$contextSummary"
        } else ""

        val systemPrompt = """
            คุณคือ "น้องรัก" (NongRak) ผู้ช่วยสดทางเสียงและภาพสำหรับพ่อแม่ในการดูแลลูกน้อยแรกเกิดถึง 3 ปี
            - โต้ตอบสดทางเสียงด้วยน้ำเสียงอบอุ่น เป็นมิตร และให้กำลังใจเหมือนเพื่อนสนิทที่คอยช่วยเหลือ
            - หากพ่อแม่เปิดกล้อง ให้สังเกตภาพ (เช่น อาการผื่น, ท่าทางของน้อง, ฉลากยา หรือสมุดวัคซีน) และให้คำแนะนำทีละขั้นตอนสั้นๆ ชัดเจน
            - ข้อกำหนดสำคัญทางการแพทย์: หากพ่อแม่ถามเรื่องอาการป่วย ยา หรือพัฒนาการที่น่ากังวล ให้เรียกใช้เครื่องมือ consult_nongrak_brain เพื่อดึงข้อมูลทางการแพทย์จากสมองกุมารเวชเสมอ
            $extraContext
        """.trimIndent()

        val setupJson = buildJsonObject {
            putJsonObject("setup") {
                put("model", "models/gemini-2.0-flash-exp")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") {
                        add("AUDIO")
                    }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", "Puck")
                            }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", systemPrompt)
                        }
                    }
                }
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            addJsonObject {
                                put("name", "consult_nongrak_brain")
                                put("description", "ปรึกษาสมองการแพทย์กุมารเวช (Gemma 4) และระบบเรียบเรียงภาษาไทย (Typhoon) สำหรับอาการป่วย สุขภาพ ยา และพัฒนาการเด็ก")
                                putJsonObject("parameters") {
                                    put("type", "OBJECT")
                                    putJsonObject("properties") {
                                        putJsonObject("symptoms") {
                                            put("type", "STRING")
                                            put("description", "อาการ คำถาม หรือข้อสงสัยของผู้ปกครอง")
                                        }
                                    }
                                    putJsonArray("required") {
                                        add("symptoms")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ws.send(setupJson.toString())
    }

    private fun handleServerMessage(rawText: String) {
        val root = runCatching { json.parseToJsonElement(rawText).jsonObject }.getOrNull() ?: return

        // 1. Handle tool calls (Gemma 4 / Typhoon bridge)
        root["toolCall"]?.jsonObject?.get("functionCalls")?.jsonArray?.forEach { callElement ->
            val callObj = callElement.jsonObject
            val name = callObj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val id = callObj["id"]?.jsonPrimitive?.contentOrNull ?: ""
            val args = callObj["args"]?.jsonObject ?: JsonObject(emptyMap())
            onToolCall(name, id, args)
        }

        // 2. Handle server content (audio parts, text, interruption)
        val serverContent = root["serverContent"]?.jsonObject ?: return

        if (serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull == true) {
            Log.d(tag, "Model interrupted by user voice -> Flushing audio")
            flushAudioOutput()
            onInterrupted()
            return
        }

        val parts = serverContent["modelTurn"]?.jsonObject?.get("parts")?.jsonArray
        parts?.forEach { part ->
            val partObj = part.jsonObject
            // Audio data
            partObj["inlineData"]?.jsonObject?.let { inline ->
                val base64Data = inline["data"]?.jsonPrimitive?.contentOrNull
                if (!base64Data.isNullOrBlank()) {
                    val pcmBytes = runCatching { Base64.decode(base64Data, Base64.DEFAULT) }.getOrNull()
                    if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                        playPcmAudio(pcmBytes)
                    }
                }
            }
            // Text data (transcriptions or live instruction steps)
            partObj["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
                if (text.isNotBlank()) {
                    onTextChunk(text)
                }
            }
        }
    }

    private fun playPcmAudio(pcmBytes: ByteArray) {
        val track = audioTrack ?: return
        scope.launch(Dispatchers.IO) {
            isPlayingAudio = true
            track.write(pcmBytes, 0, pcmBytes.size)
        }
    }

    fun flushAudioOutput() {
        isPlayingAudio = false
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        }
    }

    /**
     * Streams an incoming 16kHz PCM audio chunk from the device's microphone.
     */
    fun sendAudioChunk(pcm16Bytes: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val base64 = Base64.encodeToString(pcm16Bytes, Base64.NO_WRAP)
        val message = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    addJsonObject {
                        put("mimeType", "audio/pcm")
                        put("data", base64)
                    }
                }
            }
        }
        webSocket?.send(message.toString())
    }

    /**
     * Streams a sampled JPEG camera frame (e.g. 1 frame every 3-5 seconds).
     */
    fun sendVideoFrame(jpegBytes: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val message = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    addJsonObject {
                        put("mimeType", "image/jpeg")
                        put("data", base64)
                    }
                }
            }
        }
        webSocket?.send(message.toString())
    }

    /**
     * Sends tool response back to Gemini Live after consulting Gemma 4 / Typhoon.
     */
    fun sendToolResponse(callId: String, result: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val responseMsg = buildJsonObject {
            putJsonObject("toolResponse") {
                putJsonArray("functionResponses") {
                    addJsonObject {
                        putJsonObject("response") {
                            putJsonObject("output") {
                                put("advice", result)
                            }
                        }
                        put("id", callId)
                    }
                }
            }
        }
        webSocket?.send(responseMsg.toString())
    }

    /**
     * Performs a seamless session refresh/rollover when approaching the 10-15 minute ceiling.
     * Preserves continuity without hanging up on the parent.
     */
    fun rolloverSession(contextSummary: String) {
        Log.i(tag, "Rolling over session to refresh duration...")
        _connectionState.value = ConnectionState.REFRESHING_SESSION
        _showRefreshDialog.value = false
        _isApproachingLimit.value = false

        stopSessionTimer()
        webSocket?.close(1000, "session_refresh")
        webSocket = null

        // Reconnect fresh with carried context
        connect(contextSummary)
    }

    fun dismissRefreshDialog() {
        _showRefreshDialog.value = false
    }

    private fun startSessionTimer() {
        stopSessionTimer()
        _elapsedSeconds.value = 0L
        _isApproachingLimit.value = false
        _showRefreshDialog.value = false

        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _elapsedSeconds.value + 1
                _elapsedSeconds.value = current

                // Warn at 12 minutes (720 seconds)
                if (current >= 720 && !_isApproachingLimit.value) {
                    _isApproachingLimit.value = true
                }
                // Trigger refresh dialog at 14 minutes (840 seconds)
                if (current >= 840 && !_showRefreshDialog.value) {
                    _showRefreshDialog.value = true
                }
            }
        }
    }

    private fun stopSessionTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun disconnect() {
        stopSessionTimer()
        flushAudioOutput()
        _connectionState.value = ConnectionState.DISCONNECTED
        webSocket?.close(1000, "user_hangup")
        webSocket = null
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
    }
}

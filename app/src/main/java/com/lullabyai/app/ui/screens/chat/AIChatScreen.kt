@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lullabyai.app.data.brief.BriefKind
import com.lullabyai.app.data.brief.ProactiveBrief
import com.lullabyai.app.data.brief.ProactiveBriefUseCase
import com.lullabyai.app.data.model.ChatMode
import com.lullabyai.app.data.model.ChatSession
import com.lullabyai.app.data.model.UiChatMessage
import com.lullabyai.app.data.settings.AppSettings
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

// Same normalization ChatViewModel.dispatchAgentAction uses — models don't reproduce a literal
// type string reliably ("Navigate" / "navigate-to" instead of "navigate").
private fun normalizeActionType(type: String) = type.trim().lowercase().replace(Regex("[ -]"), "_")

/** Keyword match (English or Thai) from the AI's free-form "screen" param to a real route —
 *  same forgiving approach as ChatViewModel's param lookups, since the model won't always emit
 *  an exact route string. Covers the main destinations reachable from chat; null falls through to
 *  a no-op rather than crashing on an unrecognized screen name. */
private fun resolveNavRoute(key: String): String? {
    val k = key.trim().lowercase()
    return when {
        k.isBlank() -> null
        "nutrition" in k || "food" in k || "โภชนาการ" in k -> Screen.Nutrition.route
        "vaccine" in k || "วัคซีน" in k -> Screen.VaccineBook.route
        "growth" in k || "เติบโต" in k -> Screen.GrowthHistory.route
        "sleep" in k || "นอน" in k -> Screen.SleepTracker.route
        "exercise" in k || "ออกกำลัง" in k -> Screen.Exercise.route
        // Checked before the generic screens below: "add expense" must reach the form, not the
        // list, or the parent lands one tap short of what they asked for.
        "add_expense" in k || "เพิ่มค่าใช้จ่าย" in k || "บันทึกค่าใช้จ่าย" in k -> Screen.AddExpense.route
        "expense" in k || "cost" in k || "budget" in k ||
            "ค่าใช้จ่าย" in k || "งบ" in k -> Screen.Expenses.route
        "add_activity" in k || "เพิ่มกิจกรรม" in k || "เพิ่มนัด" in k -> Screen.AddActivity.route
        // Deliberately not "กิจกรรม" alone — that word also means exercise, which is handled above.
        "schedule" in k || "calendar" in k || "appointment" in k ||
            "ปฏิทิน" in k || "ตาราง" in k || "นัดหมาย" in k -> Screen.ActivitySchedule.route
        "disease" in k || "risk" in k || "chronic" in k || "ความเสี่ยง" in k || "โรคประจำตัว" in k -> Screen.ChronicRegistry.route
        "health" in k || "สุขภาพ" in k -> Screen.ChildHealth.route
        "setting" in k || "ตั้งค่า" in k -> Screen.Settings.route
        "stat" in k || "สถิติ" in k -> Screen.StatisticsInsights.route
        "pdf" in k || "export" in k -> Screen.PdfExport.route
        "child" in k || "ข้อมูลลูก" in k -> Screen.ChildSummary.route
        "home" in k || "dashboard" in k || "หน้าหลัก" in k -> Screen.Dashboard.route
        else -> null
    }
}

/**
 * Spoken direction for Gemini TTS. It has no rate or pitch parameter — delivery is steered by
 * plain language in the prompt, which is what stops it sounding like a screen reader.
 */
private fun voiceStyle(mode: ChatMode): String = when (mode) {
    ChatMode.STORY -> "เล่านิทานด้วยน้ำเสียงนุ่มนวล ช้าๆ อบอุ่น เหมือนกำลังกล่อมเด็กก่อนนอน"
    ChatMode.CHILD -> "พูดกับเด็กเล็กด้วยน้ำเสียงสดใส ร่าเริง เป็นมิตร ช้าและชัดเจน"
    ChatMode.NORMAL -> "พูดด้วยน้ำเสียงอบอุ่น เป็นธรรมชาติ เหมือนเพื่อนที่ห่วงใยกำลังคุยด้วย ไม่ต้องอ่านเหมือนหุ่นยนต์"
}

/** The pose the companion wears, given what it is currently doing or warning about. */
private fun companionEmotion(
    hasQuestion: Boolean,
    briefKind: BriefKind,
    mode: ChatMode
): CompanionEmotion = when {
    hasQuestion -> CompanionEmotion.CONFUSED          // waiting on a detail before it can log
    briefKind != BriefKind.NONE -> CompanionEmotion.CARING
    mode == ChatMode.STORY -> CompanionEmotion.LEARNING
    else -> CompanionEmotion.GREETING
}

@Composable
fun AIChatScreen(
    chatViewModel: ChatViewModel,
    childViewModel: ChildViewModel,
    onBack: () -> Unit,
    onNavHome: () -> Unit = {},
    onNavChild: () -> Unit = {},
    onNavSettings: () -> Unit = {},
    onNavStats: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val chatState by chatViewModel.uiState.collectAsState()
    val childState by childViewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var sessionToDelete by remember { mutableStateOf<String?>(null) }
    var sessionToRename by remember { mutableStateOf<Pair<String, String>?>(null) }  // id to current title
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedIsDocument by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isThai = com.lullabyai.app.ui.localization.LocalStrings.current.isThai
    val tts = com.lullabyai.app.util.rememberTtsSpeaker()
    var voiceEnabled by remember { mutableStateOf(false) }
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }

    // Which companion the parent picked during onboarding.
    val mascotKey by remember { SettingsManager(context).selectedMascot }.collectAsState(initial = null)
    val mascot = MascotId.fromKey(mascotKey)

    // Switching companion switches voice: each one answers in its own manner from the next message
    // onward. Set here rather than at each send, so every path through the screen picks it up.
    LaunchedEffect(mascot) { chatViewModel.setCompanion(mascot.assetPrefix, mascot.personaPrompt()) }

    // Auto Record vs Confirm before Record. Read here and pushed down so ChatViewModel stays
    // Context-free, and so flipping the setting takes effect without restarting the chat.
    val appSettings by remember { SettingsManager(context).settings }.collectAsState(initial = AppSettings())
    LaunchedEffect(appSettings.autoRecord) { chatViewModel.setAutoRecord(appSettings.autoRecord) }

    // The one pending thing worth greeting the parent with. Pre-rendered, so it is on screen
    // before the AI chain is ever touched.
    var brief by remember { mutableStateOf(ProactiveBrief.GREETING) }
    LaunchedEffect(childState.child?.id) {
        brief = runCatching { ProactiveBriefUseCase().load(childState.child?.name) }
            .getOrDefault(ProactiveBrief.GREETING)
    }

    // The AI's most recent question wins if several are pending (e.g. a slow reply from an older
    // turn) — showing more than one popup stacked would be confusing.
    val pendingNeedInfo = chatState.messages.lastOrNull { it.needInfo != null && !it.needInfoAnswered }
    val emotion = companionEmotion(pendingNeedInfo != null, brief.kind, chatState.mode)

    // ponytail: bonding tracks how many conversations the parent has had — the only cumulative
    // signal already stored. Swap for a real interaction counter when one exists.
    val conversations = chatState.sessions.size
    val bondingLevel = 1 + conversations / 5
    val bondingPercent = (conversations % 5) * 20

    // The companion card stands alone until the first real turn exists.
    val showCompanion = chatState.messages.none { it.id != "welcome" }

    // Images via system photo picker (Play policy — no READ_MEDIA_IMAGES). PDFs via OpenDocument.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            selectedIsDocument = false
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            selectedIsDocument = context.contentResolver.getType(uri) == "application/pdf"
        }
    }
    var attachMenuOpen by remember { mutableStateOf(false) }
    // Speech-to-text via Google's recognizer dialog (Thai), result fills the input field.
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) inputText = (inputText + (if (inputText.isBlank()) "" else " ") + spoken)
        }
    }
    fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "พูดข้อความของคุณ...")
        }
        runCatching { speechLauncher.launch(intent) }
    }

    // Send the current text + optional image/document, then clear both.
    fun sendCurrent() {
        val text = inputText.trim()
        val uri = selectedImageUri
        if (text.isBlank() && uri == null) return
        val bytes = uri?.let {
            if (selectedIsDocument) com.lullabyai.app.util.ImageUtils.readBytes(context, it)
            else com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, it)
        }
        val mime = if (selectedIsDocument) "application/pdf" else "image/jpeg"
        chatViewModel.sendMessage(text, childState.child, imageBytes = bytes, imageUri = uri?.toString(), imageMime = mime)
        inputText = ""
        selectedImageUri = null
        selectedIsDocument = false
    }

    LaunchedEffect(Unit) { chatViewModel.loadSessions() }

    // Reads the latest AI reply aloud when voice is enabled — one utterance per new message.
    LaunchedEffect(chatState.messages, voiceEnabled) {
        if (!voiceEnabled) return@LaunchedEffect
        val latest = chatState.messages.lastOrNull { it.role != "user" && it.content.isNotBlank() }
        if (latest != null && latest.id != lastSpokenMessageId) {
            lastSpokenMessageId = latest.id
            tts.speak(
                latest.content, isThai,
                chatState.mode.speechRate, chatState.mode.speechPitch,
                style = voiceStyle(chatState.mode)
            )
        }
    }

    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    // Stitch puts the history drawer on the right. ModalNavigationDrawer only opens from the
    // layout-direction start edge, so the drawer is built RTL and the content flipped back.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    HistoryDrawer(
                        sessions = chatState.sessions,
                        currentSessionId = chatState.currentSessionId,
                        onOpen = { scope.launch { drawerState.close() }; chatViewModel.openSession(it) },
                        onRename = { id, title -> sessionToRename = id to title },
                        onDelete = { sessionToDelete = it },
                        onNewChat = { scope.launch { drawerState.close() }; chatViewModel.newChat() },
                        onClose = { scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        CompanionTopBar(
                            childMode = chatState.mode == ChatMode.CHILD,
                            onChildModeChange = { on ->
                                chatViewModel.setMode(if (on) ChatMode.CHILD else ChatMode.NORMAL)
                            },
                            title = "คุยกับ${mascot.thaiName}",
                            onSwitchMascot = { onNavigate(Screen.MascotSwitch.route) },
                            voiceEnabled = voiceEnabled,
                            onToggleVoice = {
                                voiceEnabled = !voiceEnabled
                                if (!voiceEnabled) tts.stop()
                            },
                            onHistory = { scope.launch { drawerState.open() } }
                        )
                    },
                    bottomBar = {
                        Column {
                            // Device has no Thai voice data — say so instead of failing silently.
                            if (voiceEnabled && isThai && !tts.thaiVoiceAvailable && !tts.cloudVoiceReady) {
                                Surface(color = Error.copy(0.08f)) {
                                    Text(
                                        "อุปกรณ์นี้ยังไม่มีเสียงพูดภาษาไทย ติดตั้งได้ที่ ตั้งค่า > ภาษาและการป้อนข้อมูล > เสียงพูด",
                                        fontSize = 11.sp,
                                        color = StitchInkSoft,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            // Selected image/document preview (above the input) with a remove button.
                            selectedImageUri?.let { uri ->
                                Surface(color = Color.White) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (selectedIsDocument) {
                                            Box(
                                                Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                                                    .background(StitchPrimaryLight),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.PictureAsPdf, null, tint = StitchPrimary)
                                            }
                                        } else {
                                            AsyncImage(
                                                model = uri, contentDescription = null,
                                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Text(
                                            if (selectedIsDocument) "แนบเอกสารแล้ว" else "แนบรูปภาพแล้ว",
                                            fontSize = 12.sp, color = StitchInkSoft, modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { selectedImageUri = null; selectedIsDocument = false }) {
                                            Icon(Icons.Filled.Close, "ลบไฟล์แนบ", tint = StitchInkFaint)
                                        }
                                    }
                                }
                            }
                            ChatInputBar(
                                value = inputText,
                                onValueChange = { inputText = it },
                                attachMenuOpen = attachMenuOpen,
                                onAttachMenu = { attachMenuOpen = it },
                                onPickImage = {
                                    attachMenuOpen = false
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onPickDocument = {
                                    attachMenuOpen = false
                                    documentPicker.launch(arrayOf("application/pdf"))
                                },
                                onMic = { startSpeech() },
                                onSend = { sendCurrent() },
                                placeholder = "พิมพ์ข้อความถึง${mascot.thaiName}...",
                                sendEnabled = (inputText.isNotBlank() || selectedImageUri != null) && !chatState.isLoading
                            )
                            com.lullabyai.app.ui.components.AppBottomNav(
                                selected = com.lullabyai.app.ui.components.BottomTab.CHATBOT,
                                onHome = onNavHome,
                                onChatbot = {},
                                onChild = onNavChild,
                                onStats = onNavStats,
                                onSettings = onNavSettings,
                                onConnect = { onNavigate(Screen.Maintenance.route) }
                            )
                        }
                    },
                    containerColor = StitchSurface
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        // The pet is never scrolled away. It is the screen's subject, not a
                        // welcome state — hiding it the moment the parent types would make the
                        // companion feel like a banner instead of something being cared for.
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            if (showCompanion) {
                                CompanionCard(
                                    mascot = mascot,
                                    emotion = emotion,
                                    isSpeaking = tts.isSpeaking,
                                    statusText = brief.headline,
                                    bondingLevel = bondingLevel,
                                    bondingPercent = bondingPercent,
                                    onPlay = {
                                        chatViewModel.sendMessage(
                                            "มาเล่นเกมทายคำกับ${mascot.thaiName}กันหน่อย",
                                            childState.child
                                        )
                                    },
                                    onFood = { onNavigate(Screen.Nutrition.route) },
                                    onSleep = { onNavigate(Screen.SleepTracker.route) }
                                )
                                CompanionCtaRow(
                                    storyActive = chatState.mode == ChatMode.STORY,
                                    onStory = {
                                        chatViewModel.setMode(
                                            if (chatState.mode == ChatMode.STORY) ChatMode.NORMAL else ChatMode.STORY
                                        )
                                    },
                                    onCall = { onNavigate(Screen.VoiceCall.route) }
                                )
                            } else {
                                // Once the chat is running the full card crowds the screen. The
                                // strip keeps the pet, ความผูกพัน, and โทรคุย on screen in a
                                // fraction of the height.
                                CompanionStrip(
                                    mascot = mascot,
                                    emotion = emotion,
                                    isSpeaking = tts.isSpeaking,
                                    statusText = brief.headline,
                                    bondingLevel = bondingLevel,
                                    bondingPercent = bondingPercent,
                                    onCall = { onNavigate(Screen.VoiceCall.route) }
                                )
                            }
                        }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(top = 24.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showCompanion) {
                            // The companion opens with the brief rather than a generic hello — this
                            // is the "medicine in 30 minutes" line the parent came for.
                            item {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CompanionBubble(mascot = mascot, text = brief.detail)
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 44.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (chatState.mode == ChatMode.STORY) {
                                            SuggestionChip(Icons.Filled.PlayCircle, "อยากฟังต่อ!") {
                                                chatViewModel.sendMessage("อยากฟังต่อ!", childState.child)
                                            }
                                            SuggestionChip(Icons.Filled.ChangeCircle, "ขอเรื่องอื่น") {
                                                chatViewModel.sendMessage("ขอเรื่องอื่น", childState.child)
                                            }
                                        } else {
                                            SuggestionChip(Icons.Filled.EditNote, "บันทึกให้หน่อย") {
                                                chatViewModel.sendMessage("ช่วยบันทึกให้หน่อย", childState.child)
                                            }
                                            SuggestionChip(Icons.Filled.Article, "สรุปบันทึกวันนี้") {
                                                chatViewModel.sendMessage("สรุปบันทึกของวันนี้ให้หน่อย", childState.child)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        items(if (showCompanion) emptyList<UiChatMessage>() else chatState.messages) { message ->
                            ChatBubble(
                                message = message,
                                mascot = mascot,
                                onConfirmAction = {
                                    // "navigate" is UI-only (no repository write) — resolved and fired here,
                                    // alongside confirmAgentAction which still marks the card confirmed/failed.
                                    val action = message.agentAction
                                    if (action != null && normalizeActionType(action.type).contains("navigate")) {
                                        val screenKey = action.params["screen"] ?: action.params["route"] ?: ""
                                        resolveNavRoute(screenKey)?.let { onNavigate(it) }
                                    }
                                    childState.child?.id?.let { chatViewModel.confirmAgentAction(message.id, it) }
                                },
                                onCancelAction = { chatViewModel.cancelAgentAction(message.id) }
                            )
                        }
                        // Debug indicator: which AI model answered the latest reply
                        if (chatState.lastProvider.isNotBlank() && !chatState.isLoading) {
                            item {
                                Text(
                                    "ตอบโดย ${chatState.lastProvider}",
                                    fontSize = 10.sp,
                                    color = StitchInkFaint,
                                    modifier = Modifier.fillMaxWidth().padding(start = 44.dp)
                                )
                            }
                        }
                        if (chatState.isLoading) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MascotAvatar(mascot)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                                        color = Color.White,
                                        shadowElevation = 1.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            repeat(3) { i ->
                                                Box(
                                                    Modifier.size(6.dp).background(
                                                        StitchPrimary.copy(if (i == 0) 1f else 0.5f), CircleShape
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    pendingNeedInfo?.let { message ->
        val request = message.needInfo!!
        var answer by remember(message.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { chatViewModel.dismissNeedInfo(message.id) },
            icon = { Icon(Icons.Filled.HelpOutline, null, tint = StitchPrimary) },
            title = { Text(request.question) },
            text = {
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it },
                    label = { Text(request.fieldLabel.ifBlank { "คำตอบ" }) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { chatViewModel.answerNeedInfo(message.id, answer, childState.child) },
                    enabled = answer.isNotBlank()
                ) { Text("ส่ง", color = StitchPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { chatViewModel.dismissNeedInfo(message.id) }) { Text("ข้าม") } }
        )
    }

    sessionToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("ลบแชท") },
            text = { Text("ต้องการลบแชทนี้ใช่ไหม?") },
            confirmButton = {
                TextButton(onClick = { chatViewModel.deleteChat(id); sessionToDelete = null }) {
                    Text("ลบ", color = Error)
                }
            },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("ยกเลิก") } }
        )
    }

    // ความผูกพัน: feeding the child fed the companion too. Slides in over the conversation, then
    // leaves by itself — a meal is worth a moment's acknowledgement, not a dialog to dismiss.
    chatState.bondFeed?.let { bond ->
        LaunchedEffect(bond) {
            kotlinx.coroutines.delay(2600)
            chatViewModel.clearBondFeed()
        }
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.slideInVertically { -it } +
                    androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically { -it } +
                    androidx.compose.animation.fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = StitchPrimaryLight,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(top = 56.dp, start = 24.dp, end = 24.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        com.lullabyai.app.ui.mascot.Mascot(
                            art = mascot.rigArt(),
                            mood = com.lullabyai.app.ui.mascot.MascotMood.HAPPY,
                            modifier = Modifier.size(38.dp)
                        )
                        Column {
                            Text(
                                if (bond.leveledUp) "${mascot.thaiName}อิ่มแล้ว! ความผูกพันระดับ ${bond.level}"
                                else "${mascot.thaiName}อิ่มแล้ว!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StitchInk
                            )
                            Text(
                                "ความผูกพัน ${bond.xp} แต้ม",
                                fontSize = 11.sp,
                                color = StitchInkSoft
                            )
                        }
                    }
                }
            }
        }
    }

    sessionToRename?.let { (id, currentTitle) ->
        var newTitle by remember(id) { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            icon = { Icon(Icons.Filled.Edit, null, tint = StitchPrimary) },
            title = { Text("เปลี่ยนชื่อแชท") },
            text = {
                OutlinedTextField(
                    value = newTitle, onValueChange = { newTitle = it },
                    label = { Text("ชื่อแชท") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { chatViewModel.renameSession(id, newTitle); sessionToRename = null },
                    enabled = newTitle.isNotBlank()
                ) { Text("บันทึก", color = StitchPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { sessionToRename = null }) { Text("ยกเลิก") } }
        )
    }
}

/**
 * Stitch navigation header: คุยกับเด็ก switch on the left, companion name centred, history on
 * the right. The speaker toggle is not in the Stitch design but read-aloud already shipped —
 * dropping it would remove a working accessibility feature to match a mockup.
 */
@Composable
private fun CompanionTopBar(
    childMode: Boolean,
    onChildModeChange: (Boolean) -> Unit,
    title: String,
    voiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    onHistory: () -> Unit,
    onSwitchMascot: () -> Unit
) {
    Surface(color = Color.White) {
        // The app draws edge to edge; without this the header sits under the phone's clock and
        // battery. Material's TopAppBar applied this itself — a hand-built bar has to say so.
        Column(Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = childMode,
                        onCheckedChange = onChildModeChange,
                        modifier = Modifier.scale(0.7f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = StitchPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE5E7EB),
                            uncheckedBorderColor = Color(0xFFE5E7EB)
                        )
                    )
                    Text("คุยกับเด็ก", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StitchInkSoft, maxLines = 1)
                }
                // The companion's name is the switcher, and the caret is what says so — a
                // tappable title with no affordance is a feature nobody finds.
                Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSwitchMascot)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = StitchInk,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "เปลี่ยนเพื่อนคู่ใจ",
                            tint = StitchInkSoft,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                IconButton(onClick = onToggleVoice) {
                    Icon(
                        if (voiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        "อ่านคำตอบออกเสียง",
                        tint = if (voiceEnabled) StitchPrimary else StitchInkSoft
                    )
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Filled.History, "ประวัติการแชท", tint = StitchInkSoft)
                }
            }
            HorizontalDivider(color = StitchHairline)
        }
    }
}

/** Stitch history drawer: card list, close button, "เริ่มสนทนาใหม่" pinned to the bottom. */
@Composable
private fun HistoryDrawer(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(drawerContainerColor = StitchSurface, modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = Color.White) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ประวัติการแชท", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StitchInk)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(StitchHairline)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, "ปิด", tint = StitchInkSoft, modifier = Modifier.size(20.dp))
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    val selected = session.id == currentSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(
                                1.dp,
                                if (selected) StitchPrimary.copy(0.35f) else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onOpen(session.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            session.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) StitchPrimary else StitchInk,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.Edit, "เปลี่ยนชื่อแชท", tint = StitchPrimary,
                            modifier = Modifier.size(18.dp).clickable { onRename(session.id, session.title) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.Filled.DeleteOutline, "ลบแชท", tint = Error,
                            modifier = Modifier.size(18.dp).clickable { onDelete(session.id) }
                        )
                    }
                }
            }
            Surface(color = Color.White) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StitchPrimaryLight)
                        .clickable(onClick = onNewChat)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = StitchPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("เริ่มสนทนาใหม่", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StitchPrimary)
                }
            }
        }
    }
}

/** Stitch input row: rounded field with attach and mic inside, send as a separate circle. */
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    attachMenuOpen: Boolean,
    onAttachMenu: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    sendEnabled: Boolean
) {
    Surface(color = Color.White) {
        Column {
            HorizontalDivider(color = StitchHairline)
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(StitchSurface)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { onAttachMenu(true) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Add, "แนบไฟล์", tint = StitchInkFaint, modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { onAttachMenu(false) }) {
                            DropdownMenuItem(
                                text = { Text("อัพโหลดรูปภาพ", fontWeight = FontWeight.SemiBold) },
                                onClick = onPickImage,
                                leadingIcon = { Icon(Icons.Filled.Image, null, tint = StitchPrimary) }
                            )
                            DropdownMenuItem(
                                text = { Text("อัพโหลดเอกสาร", fontWeight = FontWeight.SemiBold) },
                                onClick = onPickDocument,
                                leadingIcon = { Icon(Icons.Filled.Description, null, tint = StitchPrimary) }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(placeholder, fontSize = 14.sp, color = StitchInkFaint, maxLines = 1)
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            textStyle = TextStyle(fontSize = 14.sp, color = StitchInkSoft),
                            cursorBrush = Brush.verticalGradient(listOf(StitchPrimary, StitchPrimary)),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(onClick = onMic, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Mic, "พูดเพื่อพิมพ์", tint = StitchInkFaint, modifier = Modifier.size(20.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(6.dp, CircleShape, spotColor = StitchPrimary.copy(0.4f))
                        .clip(CircleShape)
                        .background(if (sendEnabled) StitchPrimary else StitchPrimary.copy(0.4f))
                        .clickable(enabled = sendEnabled, onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Send, "ส่ง", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** The companion's 32dp round avatar beside each of its replies. */
@Composable
private fun MascotAvatar(mascot: MascotId) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(StitchPrimaryLight)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // The live pet rather than a still: it breathes, sways and blinks beside every reply, so
        // the companion stays alive once the conversation starts — not only on the welcome card
        // and the call screen.
        //
        // Zoomed and pushed down so the circle frames the face, not the whole body — at 32dp a
        // full-body render is an unreadable smudge. GREETING is kept because the blink
        // coordinates were measured on that pose.
        // Framed on the measured eye line rather than by eye: the rigged art centres each face at
        // roughly y=0.46, so a small downward nudge puts it in the middle of the circle. The old
        // 9dp was tuned for the older art and pushed the face low.
        com.lullabyai.app.ui.mascot.Mascot(
            art = mascot.rigArt(),
            mood = com.lullabyai.app.ui.mascot.MascotMood.IDLE,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Zoomed onto the head. At 32dp a full body is an unreadable smudge, so the circle
                // frames the face: 3.4x puts the head across the whole circle, and the nudge down
                // compensates for the face sitting slightly above the artwork's centre.
                scaleX = 3.4f
                scaleY = 3.4f
                translationY = 5.dp.toPx()
            }
        )
    }
}

/** A companion-voiced bubble with no stored message behind it (the proactive brief). */
@Composable
private fun CompanionBubble(mascot: MascotId, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MascotAvatar(mascot)
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = Color.White,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Text(
                text,
                fontSize = 14.sp,
                color = StitchInkSoft,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: UiChatMessage,
    mascot: MascotId,
    onConfirmAction: () -> Unit = {},
    onCancelAction: () -> Unit = {}
) {
    val isUser = message.role == "user"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) { MascotAvatar(mascot); Spacer(Modifier.width(12.dp)) }
            Surface(
                shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
                        else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = if (isUser) StitchPrimary else Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(0.82f)
            ) {
                Column(modifier = Modifier.padding(if (message.imageUri != null) 6.dp else 0.dp)) {
                    message.imageUri?.let { uri ->
                        AsyncImage(
                            model = uri, contentDescription = null,
                            modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(
                                horizontal = if (message.imageUri != null) 8.dp else 16.dp,
                                vertical = if (message.imageUri != null) 6.dp else 14.dp
                            ),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = if (isUser) Color.White else StitchInkSoft
                        )
                    }
                }
            }
            if (isUser) { Spacer(Modifier.width(8.dp)) }
        }
        // Agentic action card — the AI proposed logging/editing/deleting data; nothing runs until
        // the parent taps Confirm here (see ChatViewModel.confirmAgentAction).
        message.agentAction?.let { action ->
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 8.dp), horizontalArrangement = Arrangement.Start) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StitchPrimaryLight,
                    modifier = Modifier.fillMaxWidth(0.82f)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Bolt, null, tint = StitchPrimary, modifier = Modifier.size(16.dp))
                            Text(action.confirmText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StitchPrimary)
                        }
                        when (message.actionStatus) {
                            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onCancelAction, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                                    Text("ยกเลิก", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = onConfirmAction,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StitchPrimary)
                                ) {
                                    Text("ยืนยัน", fontSize = 12.sp, color = Color.White)
                                }
                            }
                            "confirmed" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                                Text("ดำเนินการแล้ว", fontSize = 12.sp, color = Color(0xFF22C55E))
                            }
                            "cancelled" -> Text("ยกเลิกแล้ว", fontSize = 12.sp, color = StitchInkFaint)
                            "failed" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = Error, modifier = Modifier.size(14.dp))
                                Text("ดำเนินการไม่สำเร็จ", fontSize = 12.sp, color = Error)
                            }
                        }
                    }
                }
            }
        }
    }
}

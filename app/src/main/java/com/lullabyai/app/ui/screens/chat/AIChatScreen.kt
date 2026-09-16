@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.lullabyai.app.data.repository.MascotBondRepository
import com.lullabyai.app.data.repository.UnlockAnnouncer
import com.lullabyai.app.data.settings.AppSettings
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.mascot.MascotWardrobe
import com.lullabyai.app.ui.mascot.UnlockBannerHost
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.util.RelationshipLabels
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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
    briefKind: BriefKind,
    mode: ChatMode
): CompanionEmotion = when {
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
    val isGuest = childState.child?.userId == "guest" || childState.child?.id == "guest_child_local"
    var showGuestRequirementDialog by remember { mutableStateOf(false) }
    val tts = com.lullabyai.app.util.rememberTtsSpeaker()
    var voiceEnabled by remember { mutableStateOf(false) }
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }

    // Which companion the parent picked during onboarding.
    val mascotKey by remember { SettingsManager(context).selectedMascot }.collectAsState(initial = null)
    val mascot = MascotId.fromKey(mascotKey)
    var wornGarments by remember { mutableStateOf<List<Int>>(emptyList()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mascot) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val bond = MascotBondRepository().get(mascot.assetPrefix).getOrNull() ?: return@launch
                    wornGarments = MascotWardrobe.garmentsFor(mascot.rigArt(), bond.equipped, bond.level)
                    UnlockAnnouncer.consider(SettingsManager(context), mascot.assetPrefix, bond.level)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Switching companion switches voice: each one answers in its own manner from the next message
    // onward. Set here rather than at each send, so every path through the screen picks it up.
    LaunchedEffect(mascot, isThai) { chatViewModel.setCompanion(mascot.assetPrefix, mascot.personaPrompt(isThai)) }

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
    val emotion = companionEmotion(brief.kind, chatState.mode)

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
    // Speech-to-text via Google's recognizer dialog, result fills the input field.
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) inputText = (inputText + (if (inputText.isBlank()) "" else " ") + spoken)
        }
    }
    fun startSpeech() {
        if (isGuest) {
            showGuestRequirementDialog = true
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isThai) "th-TH" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (isThai) "พูดข้อความของคุณ..." else "Speak your message...")
        }
        runCatching { speechLauncher.launch(intent) }
    }

    // Send the current text + optional image/document, then clear both.
    fun sendCurrent() {
        if (isGuest) {
            showGuestRequirementDialog = true
            return
        }
        val text = inputText.trim()
        val uri = selectedImageUri
        if (text.isBlank() && uri == null) return
        val bytes = uri?.let {
            if (selectedIsDocument) com.lullabyai.app.util.ImageUtils.readBytes(context, it)
            else com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, it)
        }
        val mime = if (selectedIsDocument) "application/pdf" else "image/jpeg"
        chatViewModel.sendMessage(text, childState.child, imageBytes = bytes, imageUri = uri?.toString(), imageMime = mime, isThai = isThai, family = childState.children)
        inputText = ""
        selectedImageUri = null
        selectedIsDocument = false
    }

    LaunchedEffect(isThai) { chatViewModel.loadSessions(isThai) }

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

    val isDark = com.lullabyai.app.ui.theme.LocalDarkTheme.current || appSettings.darkMode
    val chatColors = rememberChatColors(isDark = isDark)

    // Stitch puts the history drawer on the right. ModalNavigationDrawer only opens from the
    // layout-direction start edge, so the drawer is built RTL and the content flipped back.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalChatColors provides chatColors
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    HistoryDrawer(
                        sessions = chatState.sessions,
                        currentSessionId = chatState.currentSessionId,
                        onOpen = { scope.launch { drawerState.close() }; chatViewModel.openSession(it, isThai) },
                        onRename = { id, title -> sessionToRename = id to title },
                        onDelete = { sessionToDelete = it },
                        onNewChat = { scope.launch { drawerState.close() }; chatViewModel.newChat(isThai) },
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
                            title = if (isThai) mascot.thaiName else mascot.englishName,
                            onSwitchMascot = { onNavigate(Screen.MascotSwitch.route) },
                            onCloset = { onNavigate(Screen.MascotCloset.route) },
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
                                        color = chatColors.inkSoft,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            // Selected image/document preview (above the input) with a remove button.
                            selectedImageUri?.let { uri ->
                                Surface(color = chatColors.cardBg) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (selectedIsDocument) {
                                            Box(
                                                Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                                                    .background(chatColors.primaryLight),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.PictureAsPdf, null, tint = chatColors.primary)
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
                                            fontSize = 12.sp, color = chatColors.inkSoft, modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { selectedImageUri = null; selectedIsDocument = false }) {
                                            Icon(Icons.Filled.Close, "ลบไฟล์แนบ", tint = chatColors.inkFaint)
                                        }
                                    }
                                }
                            }
                            if (isGuest) {
                                Surface(
                                    color = if (chatColors.isDark) Color(0xFF231C33) else Color(0xFFF3E8FF),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (chatColors.isDark) Color(0xFF5B3E8A) else Color(0xFFD8B4FE))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Lock, contentDescription = null, tint = chatColors.primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (isThai) "บัญชีทดลอง (Test) — ต้องสมัครสมาชิกก่อนใช้งาน AI" else "Test Account — Sign up required for AI",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = chatColors.primary
                                            )
                                        }
                                        Text(
                                            if (isThai) "ระบบ AI ผู้ช่วยส่วนตัวเปิดให้ใช้งานสำหรับสมาชิกที่ลงทะเบียนเท่านั้น" else "AI companion features are available to registered members only.",
                                            fontSize = 12.sp,
                                            color = chatColors.inkSoft,
                                            textAlign = TextAlign.Center
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { onNavigate(Screen.SignUp.route) },
                                                shape = RoundedCornerShape(100.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = chatColors.primary),
                                                modifier = Modifier.weight(1f).height(38.dp)
                                            ) {
                                                Text(if (isThai) "สมัครสมาชิก (ฟรี)" else "Sign up (Free)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { onNavigate(Screen.Login.route) },
                                                shape = RoundedCornerShape(100.dp),
                                                modifier = Modifier.weight(1f).height(38.dp)
                                            ) {
                                                Text(if (isThai) "เข้าสู่ระบบ" else "Sign in", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = chatColors.primary)
                                            }
                                        }
                                    }
                                }
                            } else {
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
                            }
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
                    containerColor = chatColors.surface
                ) { padding ->
                    if (showGuestRequirementDialog) {
                        com.lullabyai.app.ui.components.RequireRegistrationDialog(
                            title = if (isThai) "ต้องสมัครสมาชิกก่อนใช้งาน AI" else "Registration Required for AI",
                            message = if (isThai) "ระบบ AI ผู้ช่วยส่วนตัวเปิดให้ใช้งานสำหรับสมาชิกที่ลงทะเบียน กรุณาสมัครสมาชิกหรือเข้าสู่ระบบเพื่อใช้งาน" else "AI companion features require an account. Please register or sign in to start chatting.",
                            isThai = isThai,
                            onDismiss = { showGuestRequirementDialog = false },
                            onSignUp = {
                                showGuestRequirementDialog = false
                                onNavigate(Screen.SignUp.route)
                            },
                            onSignIn = {
                                showGuestRequirementDialog = false
                                onNavigate(Screen.Login.route)
                            }
                        )
                    }
                    Column(Modifier.fillMaxSize().padding(padding)) {
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
                                    garments = wornGarments,
                                    onPlay = {
                                        if (isGuest) {
                                            showGuestRequirementDialog = true
                                            return@CompanionCard
                                        }
                                        chatViewModel.sendMessage(
                                            "มาเล่นเกมทายคำกับ${mascot.thaiName}กันหน่อย",
                                            childState.child,
                                            family = childState.children
                                        )
                                    },
                                    onFood = { onNavigate(Screen.Nutrition.route) },
                                    onSleep = { onNavigate(Screen.SleepTracker.route) }
                                )
                                CompanionCtaRow(
                                    storyActive = chatState.mode == ChatMode.STORY,
                                    onStory = {
                                        if (isGuest) {
                                            showGuestRequirementDialog = true
                                            return@CompanionCtaRow
                                        }
                                        chatViewModel.setMode(
                                            if (chatState.mode == ChatMode.STORY) ChatMode.NORMAL else ChatMode.STORY
                                        )
                                    },
                                    onCall = {
                                        if (isGuest) {
                                            showGuestRequirementDialog = true
                                            return@CompanionCtaRow
                                        }
                                        onNavigate(Screen.VoiceCall.route)
                                    }
                                )
                            } else {
                                CompanionStrip(
                                    mascot = mascot,
                                    emotion = emotion,
                                    isSpeaking = tts.isSpeaking,
                                    statusText = brief.headline,
                                    bondingLevel = bondingLevel,
                                    bondingPercent = bondingPercent,
                                    garments = wornGarments,
                                    onCall = {
                                        if (isGuest) {
                                            showGuestRequirementDialog = true
                                            return@CompanionStrip
                                        }
                                        onNavigate(Screen.VoiceCall.route)
                                    }
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
                            // Opening line: a short สวัสดี when nothing is due, else the reminder.
                            item {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Reminders keep the brief. A quiet open is just สวัสดี — with
                                    // the parent's name when we have one, not a long "nothing to worry
                                    // about" pitch that reads as a form.
                                    val opening = if (brief.kind == BriefKind.NONE) {
                                        RelationshipLabels.chatWelcome(
                                            chatState.parentRelationship, isThai, chatState.parentName
                                        )
                                    } else {
                                        brief.detail
                                    }
                                    CompanionBubble(mascot = mascot, text = opening)
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 44.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (chatState.mode == ChatMode.STORY) {
                                            SuggestionChip(Icons.Filled.PlayCircle, "อยากฟังต่อ!") {
                                                chatViewModel.sendMessage("อยากฟังต่อ!", childState.child, family = childState.children)
                                            }
                                            SuggestionChip(Icons.Filled.ChangeCircle, "ขอเรื่องอื่น") {
                                                chatViewModel.sendMessage("ขอเรื่องอื่น", childState.child, family = childState.children)
                                            }
                                        } else {
                                            SuggestionChip(Icons.Filled.EditNote, "บันทึกให้หน่อย") {
                                                chatViewModel.sendMessage("ช่วยบันทึกให้หน่อย", childState.child, family = childState.children)
                                            }
                                            SuggestionChip(Icons.Filled.Article, "สรุปบันทึกวันนี้") {
                                                chatViewModel.sendMessage("สรุปบันทึกของวันนี้ให้หน่อย", childState.child, family = childState.children)
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
                                isThai = isThai,
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
                                onCancelAction = { chatViewModel.cancelAgentAction(message.id) },
                                onConfirmCommand = { ticket -> chatViewModel.confirmCommand(message.id, ticket) },
                                onCancelCommand = { ticket -> chatViewModel.cancelCommand(message.id, ticket) }
                            )
                        }
                        // Parents see the product name. Debug builds add the model that actually
                        // answered, so a silent fallback to the cloud chain is still visible in testing.
                        if (chatState.lastProvider.isNotBlank() && !chatState.isLoading) {
                            item {
                                Text(
                                    (if (isThai) "ตอบโดย " else "Answered by ") +
                                        com.lullabyai.app.util.answerLabel(chatState.lastProvider) +
                                        if (com.lullabyai.app.BuildConfig.DEBUG) " · ${chatState.lastProvider}" else "",
                                    fontSize = 10.sp,
                                    color = chatColors.inkFaint,
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
                                        color = chatColors.aiBubble,
                                        shadowElevation = 1.dp
                                    ) {
                                        // Spark streams: the bubble fills as Typhoon writes. Dots until
                                        // the first words arrive (safety check, Gemma key points).
                                        val partial = chatState.streamingText
                                        if (partial != null) {
                                            Text(
                                                text = partial,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                                fontSize = 14.sp,
                                                lineHeight = 22.sp,
                                                color = chatColors.aiBubbleText
                                            )
                                        } else {
                                            val asked = chatState.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
                                            ThinkingIndicator(
                                                name = mascot.thaiName,
                                                complex = com.lullabyai.app.data.remote.NongRakMedicalRouter.isMedicalQuestion(asked) ||
                                                    asked.length > 180,
                                                isThai = isThai
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
            UnlockAnnouncer.consider(SettingsManager(context), mascot.assetPrefix, bond.level)
            val dressed = MascotBondRepository().get(mascot.assetPrefix).getOrNull()
            if (dressed != null) {
                wornGarments = MascotWardrobe.garmentsFor(mascot.rigArt(), dressed.equipped, dressed.level)
            }
            kotlinx.coroutines.delay(2600)
            chatViewModel.clearBondFeed()
        }
        val pendingUnlock by UnlockAnnouncer.current.collectAsState()
        if (pendingUnlock == null) {
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
                    color = chatColors.primaryLight,
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
                                color = chatColors.ink
                            )
                            Text(
                                "ความผูกพัน ${bond.xp} แต้ม",
                                fontSize = 11.sp,
                                color = chatColors.inkSoft
                            )
                        }
                    }
                }
            }
        }
        }
    }

    UnlockBannerHost(isThai)

    sessionToRename?.let { (id, currentTitle) ->
        var newTitle by remember(id) { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            icon = { Icon(Icons.Filled.Edit, null, tint = chatColors.primary) },
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
                ) { Text("บันทึก", color = chatColors.primary, fontWeight = FontWeight.Bold) }
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
    onSwitchMascot: () -> Unit,
    onCloset: () -> Unit
) {
    val chatColors = LocalChatColors.current
    Surface(color = chatColors.topBarBg) {
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
                            checkedTrackColor = chatColors.primary,
                            uncheckedThumbColor = if (chatColors.isDark) chatColors.inkSoft else Color.White,
                            uncheckedTrackColor = chatColors.hairline,
                            uncheckedBorderColor = chatColors.border
                        )
                    )
                    Text("คุยกับเด็ก", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chatColors.inkSoft, maxLines = 1)
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
                        color = chatColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "เปลี่ยนเพื่อนคู่ใจ",
                        tint = chatColors.inkSoft,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // The closet sits with the companion, not in Settings: this screen is where the
                // parent already thinks about who they are talking to.
                IconButton(onClick = onCloset) {
                    Icon(Icons.Filled.Checkroom, "ตู้เสื้อผ้าน้องรัก", tint = chatColors.inkSoft)
                }
                IconButton(onClick = onToggleVoice) {
                    Icon(
                        if (voiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        "อ่านคำตอบออกเสียง",
                        tint = if (voiceEnabled) chatColors.primary else chatColors.inkSoft
                    )
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Filled.History, "ประวัติการแชท", tint = chatColors.inkSoft)
                }
            }
            HorizontalDivider(color = chatColors.hairline)
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
    val chatColors = LocalChatColors.current
    ModalDrawerSheet(drawerContainerColor = chatColors.surface, modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = chatColors.topBarBg) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ประวัติการแชท", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = chatColors.ink)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(chatColors.hairline)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, "ปิด", tint = chatColors.inkSoft, modifier = Modifier.size(20.dp))
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
                            .background(chatColors.cardBg)
                            .border(
                                1.dp,
                                if (selected) chatColors.primary.copy(0.35f) else Color.Transparent,
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
                            color = if (selected) chatColors.primary else chatColors.ink,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.Edit, "เปลี่ยนชื่อแชท", tint = chatColors.primary,
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
            Surface(color = chatColors.bottomBarBg) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(chatColors.primaryLight)
                        .clickable(onClick = onNewChat)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = chatColors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("เริ่มสนทนาใหม่", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = chatColors.primary)
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
    val chatColors = LocalChatColors.current
    Surface(color = chatColors.bottomBarBg) {
        Column {
            HorizontalDivider(color = chatColors.hairline)
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
                        .background(chatColors.inputBg)
                        .border(1.dp, chatColors.inputBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { onAttachMenu(true) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Add, "แนบไฟล์", tint = chatColors.inkFaint, modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { onAttachMenu(false) }) {
                            DropdownMenuItem(
                                text = { Text("อัพโหลดรูปภาพ", fontWeight = FontWeight.SemiBold) },
                                onClick = onPickImage,
                                leadingIcon = { Icon(Icons.Filled.Image, null, tint = chatColors.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text("อัพโหลดเอกสาร", fontWeight = FontWeight.SemiBold) },
                                onClick = onPickDocument,
                                leadingIcon = { Icon(Icons.Filled.Description, null, tint = chatColors.primary) }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(placeholder, fontSize = 14.sp, color = chatColors.inkFaint, maxLines = 1)
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            textStyle = TextStyle(fontSize = 14.sp, color = chatColors.ink),
                            cursorBrush = Brush.verticalGradient(listOf(chatColors.primary, chatColors.primary)),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(onClick = onMic, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Mic, "พูดเพื่อพิมพ์", tint = chatColors.inkFaint, modifier = Modifier.size(20.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(6.dp, CircleShape, spotColor = chatColors.primary.copy(0.4f))
                        .clip(CircleShape)
                        .background(if (sendEnabled) chatColors.primary else chatColors.primary.copy(0.4f))
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
    val chatColors = LocalChatColors.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(chatColors.primaryLight)
            .border(2.dp, chatColors.border, CircleShape),
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
    val chatColors = LocalChatColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MascotAvatar(mascot)
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = chatColors.aiBubble,
            shadowElevation = if (chatColors.isDark) 0.dp else 1.dp,
            border = if (chatColors.isDark) androidx.compose.foundation.BorderStroke(1.dp, chatColors.border) else null,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Text(
                text,
                fontSize = 14.sp,
                color = chatColors.aiBubbleText,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

/**
 * The wait before the first words arrive. Spark checks the question first and, for medical or long
 * questions, has Gemma pick out key points before Typhoon starts writing — that can take a while,
 * so the line under the dots says what is happening instead of letting the bubble look frozen.
 */
@Composable
private fun ThinkingIndicator(name: String, complex: Boolean, isThai: Boolean) {
    val chatColors = LocalChatColors.current
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            seconds++
        }
    }
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "dots"
    )
    val line = when {
        seconds >= 25 -> if (isThai) "คำถามนี้ละเอียดหน่อย ขอเวลาอีกนิดนะคะ…" else "This one needs a little longer…"
        complex && seconds >= 8 -> if (isThai) "กำลังรวบรวมประเด็นสำคัญ…" else "Gathering the key points…"
        complex -> if (isThai) "${name}กำลังวิเคราะห์อย่างละเอียด…" else "Analysing carefully…"
        seconds >= 8 -> if (isThai) "กำลังเรียบเรียงคำตอบ…" else "Putting the answer together…"
        else -> if (isThai) "${name}กำลังคิด…" else "Thinking…"
    }
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                // Each dot rises and brightens in turn.
                val lift = 1f - kotlin.math.abs(phase - i - 0.5f).coerceAtMost(1f)
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { translationY = -lift * 6.dp.toPx() }
                        .background(chatColors.primary.copy(alpha = 0.4f + 0.6f * lift), CircleShape)
                )
            }
        }
        Text(line, fontSize = 12.sp, color = chatColors.inkSoft)
    }
}

/**
 * Above every medical answer, in place of warning text inside the reply. Driven by the message's
 * stored `medical` flag, so it is still there when the chat is reopened.
 */
@Composable
private fun MedicalNoteBanner(isThai: Boolean, modifier: Modifier = Modifier) {
    val chatColors = LocalChatColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = chatColors.primaryLight,
        border = if (chatColors.isDark) androidx.compose.foundation.BorderStroke(1.dp, chatColors.border) else null,
        modifier = modifier.fillMaxWidth(0.82f)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Filled.Info, null, tint = chatColors.primary, modifier = Modifier.size(16.dp))
            Text(
                if (isThai) "หมายเหตุ: คำแนะนำนี้เป็นข้อมูลทั่วไป ไม่ใช่การวินิจฉัยทางการแพทย์ " +
                    "หากอาการรุนแรงหรือไม่ดีขึ้น ควรพบแพทย์ทันที (ฉุกเฉินโทร 1669)"
                else "Note: this is general guidance, not a medical diagnosis. " +
                    "If symptoms are severe or not improving, see a doctor now (emergency: 1669).",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = chatColors.inkSoft
            )
        }
    }
}

/** Readable names for the columns a command card lists; unknown keys show as they are. */
private fun commandFieldLabel(key: String, isThai: Boolean): String {
    val th = mapOf(
        "start_time" to "เริ่ม", "end_time" to "สิ้นสุด", "sleep_type" to "ประเภทการนอน", "mood" to "อารมณ์",
        "milk_type" to "ชนิดนม", "volume_ml" to "ปริมาณ (มล.)", "side" to "ข้าง", "logged_at" to "เวลา",
        "meal_type" to "มื้อ", "item_name" to "อาหาร", "calories" to "แคลอรี", "log_date" to "วันที่",
        "waste_type" to "ประเภท", "amount" to "จำนวน", "consistency" to "ลักษณะ", "color" to "สี",
        "recorded_at" to "เวลา", "title" to "หัวข้อ", "description" to "รายละเอียด", "temperature" to "อุณหภูมิ",
        "weight_kg" to "น้ำหนัก (กก.)", "height_cm" to "ส่วนสูง (ซม.)", "vaccine_name" to "วัคซีน",
        "date_given" to "วันที่ฉีด", "medicine_name" to "ยา", "dosage" to "ขนาดยา", "name" to "ชื่อ",
        "severity" to "ความรุนแรง", "notes" to "หมายเหตุ", "note" to "หมายเหตุ", "starts_at" to "เริ่ม",
        "ends_at" to "สิ้นสุด", "spent_on" to "วันที่จ่าย", "category" to "หมวด", "time_of_day" to "เวลา",
        "item" to "รายการ", "budget" to "งบ", "month" to "เดือน", "body" to "ข้อความ", "instructions" to "คำแนะนำ",
        "tasks" to "สิ่งที่ต้องทำ", "expected_end" to "กลับมาดูแลเอง", "tooth_id" to "ซี่ฟัน", "status" to "สถานะ",
        "symptoms" to "อาการ", "feeling" to "ความรู้สึก", "day_status" to "สถานะวันนี้", "routine_date" to "วันที่",
        "morning" to "เช้า", "lunch" to "กลางวัน", "bedtime" to "ก่อนนอน", "flossing" to "ไหมขัดฟัน",
        "ingredient_name" to "วัตถุดิบ", "quantity" to "จำนวน", "done" to "ทำแล้ว", "on_date" to "วันที่",
        "pain_level" to "ระดับความเจ็บ", "erupted_at" to "วันที่ฟันขึ้น"
    )
    return if (isThai) th[key] ?: key else key.replace('_', ' ')
}

/** The named reason from ai-chat, in words a parent can act on. */
private fun commandFailureText(reason: String, isThai: Boolean): String = when {
    reason.startsWith("ticket_expired") ->
        if (isThai) "หมดเวลายืนยันแล้ว ลองพิมพ์สั่งใหม่อีกครั้ง" else "This expired. Please ask again."
    reason.startsWith("ticket_already_used") ->
        if (isThai) "รายการนี้บันทึกไปแล้ว" else "Already recorded."
    reason.startsWith("record_changed") ->
        if (isThai) "ข้อมูลนี้ถูกแก้ไปแล้วหลังจากเตรียมรายการ ลองสั่งใหม่อีกครั้ง" else "This changed since. Please ask again."
    reason.startsWith("record_not_found") || reason.startsWith("restore_not_found") ->
        if (isThai) "ไม่พบรายการนี้แล้ว" else "That item is gone."
    reason.startsWith("restore_expired") ->
        if (isThai) "เกิน 30 วันแล้ว กู้คืนไม่ได้" else "Older than 30 days; can't restore."
    "CHILD_OVER_TOTAL" in reason ->
        if (isThai) "งบของลูกรวมกันเกินงบรวมที่ตั้งไว้" else "Children's budgets would exceed the overall budget."
    "no caregiver is ticked" in reason ->
        if (isThai) "ยังไม่มีผู้ดูแลที่ติ๊ก \"ช่วยดูแล\" ในการตั้งค่าสิทธิ์" else "No caregiver is ticked in access settings."
    "42501" in reason || "row-level security" in reason || "not authorized" in reason ->
        if (isThai) "บทบาทของบัญชีนี้ไม่มีสิทธิ์ทำรายการนี้" else "Your role can't do this."
    else -> (if (isThai) "บันทึกไม่สำเร็จ: " else "Couldn't record: ") + reason.take(80)
}

@Composable
private fun ChatBubble(
    message: UiChatMessage,
    mascot: MascotId,
    isThai: Boolean = true,
    onConfirmAction: () -> Unit = {},
    onCancelAction: () -> Unit = {},
    onConfirmCommand: (ticket: String) -> Unit = {},
    onCancelCommand: (ticket: String) -> Unit = {}
) {
    val chatColors = LocalChatColors.current
    val isUser = message.role == "user"
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isUser && message.medical) {
            MedicalNoteBanner(isThai, Modifier.padding(start = 44.dp, bottom = 6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) { MascotAvatar(mascot); Spacer(Modifier.width(12.dp)) }
            Surface(
                shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
                        else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = if (isUser) chatColors.userBubble else chatColors.aiBubble,
                shadowElevation = if (chatColors.isDark) 0.dp else 1.dp,
                border = if (!isUser && chatColors.isDark) androidx.compose.foundation.BorderStroke(1.dp, chatColors.border) else null,
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
                            color = if (isUser) chatColors.userBubbleText else chatColors.aiBubbleText
                        )
                    }
                }
            }
            if (isUser) { Spacer(Modifier.width(8.dp)) }
        }
        // Command-center cards: signed proposals from ai-chat. Nothing is written until Confirm, and
        // the write runs with this person's own login (their role decides what is allowed).
        message.commands.forEach { command ->
            val status = message.commandStatus[command.ticket]
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 8.dp), horizontalArrangement = Arrangement.Start) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = chatColors.primaryLight,
                    border = if (chatColors.isDark) androidx.compose.foundation.BorderStroke(1.dp, chatColors.border) else null,
                    modifier = Modifier.fillMaxWidth(0.82f)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Bolt, null, tint = chatColors.primary, modifier = Modifier.size(16.dp))
                            Text(
                                command.label + if (command.child.isNotBlank()) " · ${command.child}" else "",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = chatColors.primary
                            )
                        }
                        Text(command.summary, fontSize = 12.sp, color = chatColors.inkSoft)
                        if (command.fields.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                command.fields.forEach { (key, value) ->
                                    val old = command.before[key]
                                    Text(
                                        "${commandFieldLabel(key, isThai)}: " + if (old != null) "${old.ifBlank { "–" }} → $value" else value,
                                        fontSize = 11.sp,
                                        color = if (old != null) chatColors.inkSoft else chatColors.inkFaint
                                    )
                                }
                            }
                        }
                        val isDelete = command.action == "delete"
                        when {
                            status == null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onCancelCommand(command.ticket) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, chatColors.border)
                                ) {
                                    Text(if (isThai) "ยกเลิก" else "Cancel", fontSize = 12.sp, color = chatColors.inkSoft)
                                }
                                Button(
                                    onClick = { onConfirmCommand(command.ticket) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isDelete) Error else chatColors.primary)
                                ) {
                                    Text(
                                        when (command.action) {
                                            "delete" -> if (isThai) "ยืนยันลบ" else "Delete"
                                            "send" -> if (isThai) "ยืนยันส่ง" else "Send"
                                            else -> if (isThai) "ยืนยัน" else "Confirm"
                                        },
                                        fontSize = 12.sp, color = Color.White
                                    )
                                }
                            }
                            status == "running" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = chatColors.primary
                                )
                                Text(if (isThai) "กำลังดำเนินการ…" else "Working…", fontSize = 12.sp, color = chatColors.inkSoft)
                            }
                            status == "done" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                                Text(
                                    when (command.action) {
                                        "edit" -> if (isThai) "แก้ไขแล้ว (กู้คืนได้ใน 30 วัน)" else "Updated (restorable for 30 days)"
                                        "delete" -> if (isThai) "ลบแล้ว (กู้คืนได้ใน 30 วัน)" else "Deleted (restorable for 30 days)"
                                        "restore" -> if (isThai) "กู้คืนแล้ว" else "Restored"
                                        "send" -> if (isThai) "ส่งแล้ว" else "Sent"
                                        else -> if (isThai) "บันทึกแล้ว" else "Recorded"
                                    },
                                    fontSize = 12.sp, color = Color(0xFF22C55E)
                                )
                            }
                            status == "cancelled" -> Text(if (isThai) "ยกเลิกแล้ว" else "Cancelled", fontSize = 12.sp, color = chatColors.inkFaint)
                            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = Error, modifier = Modifier.size(14.dp))
                                Text(commandFailureText(status.removePrefix("failed:"), isThai), fontSize = 12.sp, color = Error)
                            }
                        }
                    }
                }
            }
        }
        // Agentic action card — the AI proposed logging/editing/deleting data; nothing runs until
        // the parent taps Confirm here (see ChatViewModel.confirmAgentAction).
        message.agentAction?.let { action ->
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 8.dp), horizontalArrangement = Arrangement.Start) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = chatColors.primaryLight,
                    border = if (chatColors.isDark) androidx.compose.foundation.BorderStroke(1.dp, chatColors.border) else null,
                    modifier = Modifier.fillMaxWidth(0.82f)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Bolt, null, tint = chatColors.primary, modifier = Modifier.size(16.dp))
                            Text(action.confirmText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = chatColors.primary)
                        }
                        when (message.actionStatus) {
                            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onCancelAction,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, chatColors.border)
                                ) {
                                    Text("ยกเลิก", fontSize = 12.sp, color = chatColors.inkSoft)
                                }
                                Button(
                                    onClick = onConfirmAction,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = chatColors.primary)
                                ) {
                                    Text("ยืนยัน", fontSize = 12.sp, color = Color.White)
                                }
                            }
                            "confirmed" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                                Text("ดำเนินการแล้ว", fontSize = 12.sp, color = Color(0xFF22C55E))
                            }
                            "cancelled" -> Text("ยกเลิกแล้ว", fontSize = 12.sp, color = chatColors.inkFaint)
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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.UiChatMessage
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
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
        "disease" in k || "risk" in k || "ความเสี่ยง" in k -> Screen.DiseaseRisk.route
        "health" in k || "สุขภาพ" in k -> Screen.ChildHealth.route
        "setting" in k || "ตั้งค่า" in k -> Screen.Settings.route
        "stat" in k || "สถิติ" in k -> Screen.StatisticsInsights.route
        "pdf" in k || "export" in k -> Screen.PdfExport.route
        "child" in k || "ข้อมูลลูก" in k -> Screen.ChildSummary.route
        "home" in k || "dashboard" in k || "หน้าหลัก" in k -> Screen.Dashboard.route
        else -> null
    }
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
    val aiVoice = rememberAiVoice()
    var voiceEnabled by remember { mutableStateOf(false) }
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }

    // Image OR document (PDF) picker — "like in Gemini", per the request: the model can read an
    // attached file, not just photos. Images are downscaled to JPEG at send time; PDFs are sent
    // as raw bytes (see sendCurrent below) since downscaling only makes sense for images.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            selectedIsDocument = context.contentResolver.getType(uri) == "application/pdf"
        }
    }
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
            aiVoice.speak(latest.content)
        }
    }

    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ประวัติแชท", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { scope.launch { drawerState.close() }; chatViewModel.newChat() },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("แชทใหม่")
                            }
                            // Swiping or tapping the scrim also closes the drawer, but neither is
                            // obvious to someone unfamiliar with Material drawers — a plain, labeled
                            // close button is the discoverable option.
                            IconButton(onClick = { scope.launch { drawerState.close() } }) {
                                Icon(Icons.Filled.Close, contentDescription = "ปิด", modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(chatState.sessions) { session ->
                            val selected = session.id == chatState.currentSessionId
                            NavigationDrawerItem(
                                label = { Text(session.title, maxLines = 1) },
                                selected = selected,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    chatViewModel.openSession(session.id)
                                },
                                icon = { Icon(Icons.Filled.ChatBubbleOutline, null) },
                                badge = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(onClick = { sessionToRename = session.id to session.title }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Filled.Edit, "เปลี่ยนชื่อแชท", tint = Primary, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { sessionToDelete = session.id }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Filled.DeleteOutline, "ลบแชท", tint = Error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Primary.copy(0.12f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiAvatarView(isSpeaking = aiVoice.isSpeaking, modifier = Modifier.size(28.dp))
                            Text("คุยกับ AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                    },
                    actions = {
                        IconButton(onClick = {
                            voiceEnabled = !voiceEnabled
                            if (!voiceEnabled) aiVoice.stop()
                        }) {
                            Icon(
                                if (voiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                "อ่านคำตอบออกเสียง", tint = if (voiceEnabled) Primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { onNavigate(Screen.VoiceCall.route) }) {
                            Icon(Icons.Filled.Call, "โทรคุยกับ AI")
                        }
                        IconButton(onClick = { onNavigate(Screen.CameraScan.route) }) {
                            Icon(Icons.Filled.CameraAlt, "สแกนรอบตัวลูก")
                        }
                        IconButton(onClick = { chatViewModel.newChat() }) {
                            Icon(Icons.Filled.AddComment, "แชทใหม่")
                        }
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.History, "ประวัติแชท")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                Column {
                    // Selected image/document preview (above the input) with a remove button.
                    selectedImageUri?.let { uri ->
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (selectedIsDocument) {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PictureAsPdf, null, tint = Primary)
                                    }
                                } else {
                                    AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                }
                                Text(
                                    if (selectedIsDocument) "แนบเอกสารแล้ว" else "แนบรูปภาพแล้ว",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { selectedImageUri = null; selectedIsDocument = false }) {
                                    Icon(Icons.Filled.Close, "ลบไฟล์แนบ", tint = Outline)
                                }
                            }
                        }
                    }
                    // Message input — large field + large tap targets, easy to type/click on a phone.
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("พิมพ์ข้อความของคุณ...", color = Outline, style = MaterialTheme.typography.bodyLarge) },
                                textStyle = MaterialTheme.typography.bodyLarge,
                                shape = RoundedCornerShape(24.dp),
                                minLines = 2,
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Attach (image or PDF document — "like in Gemini")
                                IconButton(
                                    onClick = { imagePicker.launch(arrayOf("image/*", "application/pdf")) },
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(Icons.Filled.AttachFile, "แนบรูปภาพหรือเอกสาร", tint = Primary, modifier = Modifier.size(26.dp))
                                }
                                // Mic (speech → text)
                                IconButton(onClick = { startSpeech() }, modifier = Modifier.size(52.dp)) {
                                    Icon(Icons.Filled.Mic, "พูดเพื่อพิมพ์", tint = Primary, modifier = Modifier.size(26.dp))
                                }
                            }
                            IconButton(
                                onClick = { sendCurrent() },
                                modifier = Modifier.size(56.dp).clip(CircleShape).background(Primary),
                                enabled = (inputText.isNotBlank() || selectedImageUri != null) && !chatState.isLoading
                            ) {
                                Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    // App footer — "แชทบอท" tab highlighted
                    com.lullabyai.app.ui.components.AppBottomNav(
                        selected = com.lullabyai.app.ui.components.BottomTab.CHATBOT,
                        onHome = onNavHome,
                        onChatbot = {},
                        onChild = onNavChild,
                        onStats = onNavStats,
                        onSettings = onNavSettings
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(chatState.messages) { message ->
                    ChatBubble(
                        message = message,
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
                            style = MaterialTheme.typography.labelSmall,
                            color = Outline,
                            modifier = Modifier.padding(start = 40.dp)
                        )
                    }
                }
                if (chatState.isLoading) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AIAvatar()
                            Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(3) { i ->
                                        Box(modifier = Modifier.size(6.dp).background(Primary.copy(if (i == 0) 1f else 0.5f), CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // The AI's most recent question wins if several are pending (e.g. a slow reply from an older
    // turn) — showing more than one popup stacked would be confusing.
    val pendingNeedInfo = chatState.messages.lastOrNull { it.needInfo != null && !it.needInfoAnswered }
    pendingNeedInfo?.let { message ->
        val request = message.needInfo!!
        var answer by remember(message.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { chatViewModel.dismissNeedInfo(message.id) },
            icon = { Icon(Icons.Filled.HelpOutline, null, tint = Primary) },
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
                ) { Text("ส่ง", color = Primary, fontWeight = FontWeight.Bold) }
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

    sessionToRename?.let { (id, currentTitle) ->
        var newTitle by remember(id) { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            icon = { Icon(Icons.Filled.Edit, null, tint = Primary) },
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
                ) { Text("บันทึก", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { sessionToRename = null }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun ChatBubble(message: UiChatMessage, onConfirmAction: () -> Unit = {}, onCancelAction: () -> Unit = {}) {
    val isUser = message.role == "user"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) { AIAvatar(); Spacer(Modifier.width(8.dp)) }
            Surface(
                shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                        else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = if (isUser) Primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(0.78f)
            ) {
                Column(modifier = Modifier.padding(if (message.imageUri != null) 6.dp else 0.dp)) {
                    message.imageUri?.let { uri ->
                        AsyncImage(
                            model = uri, contentDescription = null,
                            modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(if (message.imageUri != null) 6.dp else 12.dp, if (message.imageUri != null) 4.dp else 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (isUser) { Spacer(Modifier.width(8.dp)) }
        }
        // Agentic action card — the AI proposed logging/editing/deleting data; nothing runs until
        // the parent taps Confirm here (see ChatViewModel.confirmAgentAction).
        message.agentAction?.let { action ->
            Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp), horizontalArrangement = Arrangement.Start) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Primary.copy(0.08f),
                    modifier = Modifier.fillMaxWidth(0.78f)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Bolt, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text(action.confirmText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Primary)
                        }
                        when (message.actionStatus) {
                            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onCancelAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("ยกเลิก", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(onClick = onConfirmAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                    Text("ยืนยัน", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                            "confirmed" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                Text("ดำเนินการแล้ว", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            }
                            "cancelled" -> Text("ยกเลิกแล้ว", style = MaterialTheme.typography.labelSmall, color = Outline)
                            "failed" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = Error, modifier = Modifier.size(14.dp))
                                Text("ดำเนินการไม่สำเร็จ", style = MaterialTheme.typography.labelSmall, color = Error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AIAvatar() {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(Primary.copy(0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
    }
}

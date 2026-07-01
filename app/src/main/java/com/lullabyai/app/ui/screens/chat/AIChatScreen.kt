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
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

@Composable
fun AIChatScreen(
    chatViewModel: ChatViewModel,
    childViewModel: ChildViewModel,
    onBack: () -> Unit,
    onNavHome: () -> Unit = {},
    onNavChild: () -> Unit = {},
    onNavSettings: () -> Unit = {},
    onNavStats: () -> Unit = {}
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
    val context = LocalContext.current

    // Image picker → hold the URI; it's downscaled to JPEG bytes at send time.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selectedImageUri = uri
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

    // Send the current text + optional image, then clear both.
    fun sendCurrent() {
        val text = inputText.trim()
        val uri = selectedImageUri
        if (text.isBlank() && uri == null) return
        val bytes = uri?.let { com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, it) }
        chatViewModel.sendMessage(text, childState.child, imageBytes = bytes, imageUri = uri?.toString(), imageMime = "image/jpeg")
        inputText = ""
        selectedImageUri = null
    }

    LaunchedEffect(Unit) { chatViewModel.loadSessions() }

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
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ประวัติแชท", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FilledTonalButton(
                            onClick = { scope.launch { drawerState.close() }; chatViewModel.newChat() },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("แชทใหม่")
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
                    title = { Text("คุยกับ AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                    },
                    actions = {
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
                    // Selected-image preview (above the input) with a remove button.
                    selectedImageUri?.let { uri ->
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                Text("แนบรูปภาพแล้ว", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { selectedImageUri = null }) {
                                    Icon(Icons.Filled.Close, "ลบรูป", tint = Outline)
                                }
                            }
                        }
                    }
                    // Message input
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                                OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("พิมพ์ข้อความของคุณ...", color = Outline) },
                                shape = RoundedCornerShape(100.dp),
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            // Mic (speech → text)
                            IconButton(onClick = { startSpeech() }) {
                                Icon(Icons.Filled.Mic, "พูดเพื่อพิมพ์", tint = Primary)
                            }
                            IconButton(
                                onClick = { sendCurrent() },
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Primary),
                                enabled = (inputText.isNotBlank() || selectedImageUri != null) && !chatState.isLoading
                            ) {
                                Icon(Icons.Filled.Send, null, tint = Color.White)
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
                items(chatState.messages) { message -> ChatBubble(message = message) }
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
private fun ChatBubble(message: UiChatMessage) {
    val isUser = message.role == "user"
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

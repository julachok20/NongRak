@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
package com.lullabyai.app.ui.screens.access

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.CareMessage
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.CareChatViewModel

/**
 * Parent ↔ caregiver conversation about one child.
 *
 * Nothing here is private: the AI reads every message the carer sends, files what it understands
 * as a care log the parent can see, and asks when a message is too vague to record. The policy
 * dialog says exactly that and blocks the screen until it is accepted — an after-the-fact
 * disclosure would not be consent.
 */
@Composable
fun CareChatScreen(
    viewModel: CareChatViewModel,
    navController: NavController,
    peerId: String,
    /** Context carried in from an activity entry; seeds the draft once, then behaves normally. */
    quote: String = "",
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val signed by viewModel.imageUrls.collectAsState()
    val context = LocalContext.current
    // Reads the picked image's bytes here rather than handing a content URI to the repository —
    // the permission grant on that URI does not outlive this composable.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        viewModel.sendImage(bytes, ext)
    }
    // Held rather than acted on immediately: unsend keeps the text readable, and the sender has
    // to be told that before it happens, not after.
    var pendingUnsend by remember { mutableStateOf<CareMessage?>(null) }

    LaunchedEffect(peerId) { viewModel.load(peerId) }

    // Attached once per arrival, after load() has resolved peerName — the quote is attributed to
    // the peer, and an empty name would render an anonymous quote block. Keyed on the quote so
    // returning to a chat you opened normally does not re-attach an old subject line.
    LaunchedEffect(quote, state.peerName) {
        if (quote.isNotBlank() && state.peerName.isNotBlank()) viewModel.quoteActivity(quote)
    }

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = state.peerName.ifBlank { if (isThai) "แชท" else "Chat" },
                onBack = onBack,
                navController = navController
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Standing reminder, not only the one-time dialog — people forget what they agreed to.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Primary.copy(0.08f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.SmartToy, null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(
                    if (isThai) "แชทนี้ไม่เป็นส่วนตัว — NongRak AI อ่านและบันทึกข้อมูลการดูแลอัตโนมัติ"
                    else "This chat is not private — NongRak AI reads it and records care automatically",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.messages.isEmpty() && !state.loading) {
                    item {
                        Text(
                            if (isThai) "ยังไม่มีข้อความ — เริ่มพูดคุยได้เลย"
                            else "No messages yet — say hello",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                }
                items(state.messages, key = { it.id }) { m ->
                    MessageBubble(
                        m = m,
                        isThai = isThai,
                        imageUrl = m.imageUrl?.let { signed[it] },
                        revealed = m.id in state.revealed,
                        onToggleReveal = { viewModel.toggleReveal(m.id) },
                        onReply = { viewModel.startReply(m) },
                        onCopy = { clipboard.setText(AnnotatedString(m.body)) },
                        onUnsend = { pendingUnsend = m }
                    )
                }
            }

            // The AI's own follow-up. Only the carer ever sees these, and they stay out of the
            // main AI chat so a care question never mixes into the parent's assistant.
            state.aiRecorded?.let { kind ->
                InfoBanner(
                    icon = Icons.Filled.CheckCircle,
                    tint = HealthGood,
                    text = if (isThai) "AI บันทึก \"$kind\" ให้แล้ว ผู้ปกครองเห็นในประวัติกิจกรรม"
                    else "AI recorded \"$kind\" — visible to the parents in the activity history",
                    onDismiss = viewModel::dismissAiRecorded
                )
            }
            state.aiQuestion?.let { q ->
                InfoBanner(
                    icon = Icons.Filled.HelpOutline,
                    tint = Primary,
                    text = q,
                    onDismiss = viewModel::dismissAiQuestion
                )
            }

            state.error?.let {
                Text(
                    it,
                    color = Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding()) {

                state.replyingTo?.let { target ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .background(Primary.copy(0.08f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(32.dp).background(Primary))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (isThai) "ตอบกลับ " else "Replying to ") +
                                    target.senderName.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Text(
                                target.body,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = viewModel::cancelReply, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, null, tint = Outline, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { pickImage.launch("image/*") },
                        enabled = !state.sending
                    ) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = if (isThai) "แนบรูปภาพ" else "Attach image",
                            tint = Primary
                        )
                    }
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = viewModel::setDraft,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        maxLines = 4,
                        placeholder = { Text(if (isThai) "พิมพ์ข้อความ…" else "Type a message…") }
                    )
                    FilledIconButton(
                        onClick = viewModel::send,
                        enabled = state.draft.isNotBlank() && !state.sending,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary)
                    ) {
                        if (state.sending) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White
                            )
                        } else {
                            Icon(Icons.Filled.Send, null, tint = Color.White)
                        }
                    }
                }
                }
            }
        }
    }

    // Says plainly what unsend does here, because it is not what the word normally means.
    pendingUnsend?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUnsend = null },
            icon = { Icon(Icons.Filled.Undo, null, tint = Error) },
            title = {
                Text(
                    if (isThai) "ยกเลิกการส่งข้อความนี้?" else "Unsend this message?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isThai)
                        "ข้อความจะถูกทำเครื่องหมายว่าถูกยกเลิก แต่อีกฝ่ายยังกดดูข้อความเดิมได้ " +
                            "เพราะบันทึกการดูแลเด็กต้องตรวจสอบย้อนหลังได้"
                    else
                        "The message is marked as withdrawn, but the other person can still open " +
                            "and read the original — a childcare record has to stay auditable."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unsend(target.id)
                    pendingUnsend = null
                }) {
                    Text(
                        if (isThai) "ยกเลิกการส่ง" else "Unsend",
                        color = Error, fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnsend = null }) {
                    Text(if (isThai) "ไม่" else "Keep")
                }
            }
        )
    }

    // Blocks the screen until accepted. Dismissing goes back rather than granting access by
    // default — silence is not agreement.
    if (state.needsConsent) {
        ChatPolicyDialog(
            isThai = isThai,
            onAccept = viewModel::acceptPolicy,
            onDecline = onBack
        )
    }
}

@Composable
private fun ChatPolicyDialog(isThai: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDecline,
        icon = { Icon(Icons.Filled.PrivacyTip, null, tint = Primary) },
        title = {
            Text(if (isThai) "ก่อนเริ่มแชท" else "Before you start", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isThai) "แชทระหว่างผู้ปกครองและผู้ดูแลนี้ไม่เป็นส่วนตัว โปรดอ่านก่อนตกลง:"
                    else "This parent–caregiver chat is not private. Please read before agreeing:"
                )
                listOf(
                    if (isThai) "NongRak AI อ่านข้อความทุกข้อความในห้องนี้"
                    else "NongRak AI reads every message in this room",
                    if (isThai) "AI จะบันทึกข้อมูลการดูแลเด็กที่พบลงในประวัติกิจกรรมโดยอัตโนมัติ"
                    else "It records any childcare details it finds into the activity history",
                    if (isThai) "เจ้าของและผู้ปกครองร่วมเห็นบันทึกเหล่านั้นได้"
                    else "The owner and co-owners can see those records",
                    if (isThai) "อย่าส่งข้อมูลส่วนตัวที่ไม่เกี่ยวกับการดูแลเด็ก"
                    else "Do not send personal information unrelated to the child's care"
                ).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = Primary)
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = Primary)
                    )
                    Text(
                        if (isThai) "ฉันเข้าใจและยอมรับ" else "I understand and agree",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = agreed) {
                Text(if (isThai) "ตกลง" else "Agree", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(if (isThai) "ไม่ตกลง" else "Decline") }
        }
    )
}

@Composable
private fun InfoBanner(icon: ImageVector, tint: Color, text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(tint.copy(0.10f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Close, null, tint = Outline, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * One tick sent, two ticks delivered, two blue ticks read — the convention people already know,
 * so it needs no legend. Drawn as two overlapping checks rather than a single glyph because
 * Material has no done_all equivalent in the bundled icon set.
 */
@Composable
private fun DeliveryTicks(status: com.lullabyai.app.data.model.DeliveryStatus) {
    val tint = when (status) {
        com.lullabyai.app.data.model.DeliveryStatus.READ -> Color(0xFF53BDEB)
        else -> Color.White.copy(0.75f)
    }
    Box(Modifier.width(if (status == com.lullabyai.app.data.model.DeliveryStatus.SENT) 12.dp else 17.dp)) {
        Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(12.dp))
        if (status != com.lullabyai.app.data.model.DeliveryStatus.SENT) {
            Icon(
                Icons.Filled.Check, null, tint = tint,
                modifier = Modifier.size(12.dp).offset(x = 5.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    m: CareMessage,
    isThai: Boolean,
    imageUrl: String?,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onUnsend: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    // A withdrawn message loses its colour: it should not read as part of the conversation.
    val tint = when {
        m.isUnsent -> SurfaceContainerLow
        m.mine -> Primary
        else -> SurfaceContainerLow
    }
    val onTint = if (m.mine && !m.isUnsent) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Column(
                Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        tint,
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (m.mine) 18.dp else 4.dp,
                            bottomEnd = if (m.mine) 4.dp else 18.dp
                        )
                    )
                    .combinedClickable(
                        onClick = { if (m.isUnsent) onToggleReveal() },
                        onLongClick = { menuOpen = true }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // What this message was answering, so a reply still makes sense later.
                m.replyToBody?.let { quoted ->
                    Row(
                        Modifier
                            .padding(bottom = 6.dp)
                            .background(
                                (if (m.mine && !m.isUnsent) Color.White else Primary).copy(0.14f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(if (m.mine && !m.isUnsent) Color.White else Primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                m.replyToSender.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = onTint.copy(0.9f)
                            )
                            Text(
                                quoted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = onTint.copy(0.75f)
                            )
                        }
                    }
                }

                if (m.isUnsent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DoNotDisturbOn, null,
                            tint = Outline, modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isThai) "ข้อความนี้ถูกยกเลิกการส่ง" else "This message was unsent",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (revealed) (if (isThai) "ซ่อนข้อความเดิม" else "Hide original")
                        else (if (isThai) "แตะเพื่อดูข้อความเดิม" else "Tap to see the original"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary
                    )
                    if (revealed) {
                        Text(
                            m.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .background(Outline.copy(0.12f), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        )
                    }
                } else {
                    // A picture with no caption should not leave an empty line above it.
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = if (isThai) "รูปภาพในแชท" else "Chat image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (m.body.isNotBlank()) Spacer(Modifier.height(6.dp))
                    } else if (m.imageUrl != null) {
                        // Path known, signed URL not back yet.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Outline.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        if (m.body.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
                    if (m.body.isNotBlank()) {
                        Text(m.body, color = onTint, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        TimeUtils.localTime(m.createdAt).orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (m.mine && !m.isUnsent) Color.White.copy(0.7f) else Outline
                    )
                    // Only my own messages carry a receipt: showing one on a message I received
                    // would be telling me something I already know.
                    if (m.mine && !m.isUnsent) {
                        Spacer(Modifier.width(4.dp))
                        DeliveryTicks(m.status)
                    }
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!m.isUnsent) {
                    DropdownMenuItem(
                        text = { Text(if (isThai) "ตอบกลับ" else "Reply") },
                        leadingIcon = { Icon(Icons.Filled.Reply, null) },
                        onClick = { menuOpen = false; onReply() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isThai) "คัดลอก" else "Copy") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { menuOpen = false; onCopy() }
                    )
                    // Only your own words are yours to withdraw.
                    if (m.mine) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isThai) "ยกเลิกการส่ง" else "Unsend",
                                    color = Error
                                )
                            },
                            leadingIcon = { Icon(Icons.Filled.Undo, null, tint = Error) },
                            onClick = { menuOpen = false; onUnsend() }
                        )
                    }
                } else {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (revealed) (if (isThai) "ซ่อนข้อความเดิม" else "Hide original")
                                else (if (isThai) "ดูข้อความเดิม" else "Show original")
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Visibility, null) },
                        onClick = { menuOpen = false; onToggleReveal() }
                    )
                }
            }
        }
    }
}

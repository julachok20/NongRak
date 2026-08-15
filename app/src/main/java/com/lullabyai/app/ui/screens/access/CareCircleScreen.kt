@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.CareConversation
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.AccessRoleLabels
import com.lullabyai.app.viewmodel.CareCircleViewModel

/**
 * Design: "Care Circle — แชทกับผู้ดูแล".
 *
 * Everyone around this child in one list, so a conversation is chosen rather than assumed. The
 * green dots come from a Realtime presence channel: they exist only while a socket does, so they
 * cannot go stale after a crash.
 */
@Composable
fun CareCircleScreen(
    viewModel: CareCircleViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    var showNewChat by remember { mutableStateOf(false) }
    // Only people already granted access can appear here — the picker is a filter over the
    // access list, never a way to reach someone outside it.
    val startable = state.conversations.filter { !it.hasHistory }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "แชทกับผู้ดูแล" else "Care Circle",
                onBack = onBack,
                navController = navController
            )
        },
        floatingActionButton = {
            if (startable.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showNewChat = true },
                    containerColor = Primary,
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Filled.ChatBubbleOutline,
                        contentDescription = if (isThai) "เริ่มแชทใหม่" else "New chat"
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    placeholder = {
                        Text(if (isThai) "ค้นหาผู้ดูแล หรือ ข้อความ…" else "Search people or messages…")
                    }
                )
            }

            if (state.onlinePeers.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            if (isThai) "ผู้ดูแลที่ออนไลน์" else "Online now",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            state.onlinePeers.forEach { c ->
                                Column(
                                    Modifier.width(68.dp).clickable { onOpenChat(c.peerId) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        AccessAvatar(c.peerAvatar, c.peerName)
                                        Box(
                                            Modifier
                                                .size(13.dp)
                                                .background(Color.White, CircleShape)
                                                .padding(2.dp)
                                                .background(HealthGood, CircleShape)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        c.peerName,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            }

            state.error?.let { msg ->
                item { Text(msg, color = Error, style = MaterialTheme.typography.bodySmall) }
            }

            items(state.visible, key = { it.peerId }) { c ->
                ConversationRow(
                    c = c,
                    isThai = isThai,
                    online = c.peerId in state.online,
                    onClick = { onOpenChat(c.peerId) }
                )
            }

            if (!state.loading && state.visible.isEmpty() && state.error == null) {
                item {
                    Text(
                        if (isThai) "ยังไม่มีผู้ดูแลคนอื่นสำหรับเด็กคนนี้"
                        else "Nobody else has access to this child yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            people = startable,
            isThai = isThai,
            onPick = { showNewChat = false; onOpenChat(it) },
            onDismiss = { showNewChat = false }
        )
    }
}

/**
 * "เลือกผู้ดูแลเพื่อสนทนา" — everyone with access you have not written to yet. Deliberately not a
 * search over all users: the access list IS the address book, so there is no way to start a
 * conversation with someone who has no relationship to this child.
 */
@Composable
private fun NewChatSheet(
    people: List<CareConversation>,
    isThai: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (isThai) "เลือกผู้ดูแลเพื่อสนทนา" else "Pick someone to message",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            people.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(p.peerId) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccessAvatar(p.peerAvatar, p.peerName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.peerName, fontWeight = FontWeight.Bold)
                        Text(
                            AccessRoleLabels.title(p.peerRole, isThai),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    c: CareConversation,
    isThai: Boolean,
    online: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // An unread conversation carries a coloured spine, so the eye finds it before reading.
        if (c.unread > 0) {
            Box(Modifier.width(3.dp).height(40.dp).background(Primary, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Box(contentAlignment = Alignment.BottomEnd) {
            AccessAvatar(c.peerAvatar, c.peerName)
            if (online) {
                Box(
                    Modifier
                        .size(13.dp)
                        .background(Color.White, CircleShape)
                        .padding(2.dp)
                        .background(HealthGood, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.peerName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "(${AccessRoleLabels.title(c.peerRole, isThai)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                when {
                    c.lastHasImage && c.lastBody.isNullOrBlank() ->
                        if (isThai) "ส่งรูปภาพ" else "Sent a photo"
                    c.lastBody.isNullOrBlank() ->
                        if (isThai) "ยังไม่มีข้อความ" else "No messages yet"
                    // Your own last line reads oddly without a marker.
                    c.lastFromMe -> (if (isThai) "คุณ: " else "You: ") + c.lastBody
                    else -> c.lastBody.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (c.unread > 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                TimeUtils.localTime(c.lastAt) ?: TimeUtils.localDate(c.lastAt).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = if (c.unread > 0) Primary else Outline
            )
            if (c.unread > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .background(Error, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${c.unread}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

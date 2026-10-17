package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.RecordDiff
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.data.model.RecordEdit
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.viewmodel.AccessRoleLabels
import com.lullabyai.app.viewmodel.ActivityHistoryUiState
import com.lullabyai.app.viewmodel.ActivityHistoryViewModel
import com.lullabyai.app.viewmodel.ActivityKindLabels
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId

private val AhPrimary = Color(0xFF173DB8)
private val AhSoft = Color(0xFFE8EDFD)
private val AhGreen = Color(0xFF15803D)
private val AhGreenSoft = Color(0xFFDCFCE7)
private val AhRed = Color(0xFFB91C1C)
private val AhAuthorColors = listOf(Color(0xFF173DB8), Color(0xFF0E7C66), Color(0xFF9333EA), Color(0xFFB45309), Color(0xFFBE185D))

/**
 * ประวัติกิจกรรมและการแก้ไขข้อมูลลูก (Child Log & Edit Activity History), built to the Stitch frame.
 *
 * Every row is a real record from list_child_activity_feed. "แก้ไขข้อมูล" and its before → after lines
 * come from record_edits, "คืนค่าข้อมูลเดิม" calls revert_record_edit (parents only, enforced by the
 * database), and "คุณแม่อ่านแล้ว" comes from record_reads.
 */
@Composable
fun ActivityHistoryScreen(
    viewModel: ActivityHistoryViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onChat: ((String) -> Unit)? = null,
    /** peerId + what the reply is about, so the carer sees which update is being questioned. */
    onReplyToEntry: ((String, String) -> Unit)? = null,
    onOpenHub: (() -> Unit)? = null,
    onOpenRecord: (String) -> Unit = {}
) {
    val thai = LocalStrings.current.isThai
    val state by viewModel.uiState.collectAsState()
    var photoOpen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    val caregiver = state.members.firstOrNull {
        it.isCaregiver && !AccessRoleLabels.isFullAccess(it.role) && it.userId != state.myUserId
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LullabyTopBar(
                title = if (thai) "ประวัติกิจกรรม" else "Activity history",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HeaderCard(thai, state) }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Close, null) }
                    },
                    placeholder = {
                        Text(if (thai) "ค้นหากิจกรรม, บันทึกย่อ, หรือชื่อผู้แก้ไข…" else "Search activity, notes or who wrote it…", fontSize = 13.sp)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityKindLabels.CATEGORIES.forEach { (key, names) ->
                        CategoryChip(
                            label = if (thai) names.first else names.second,
                            icon = categoryIcon(key),
                            selected = state.category == key
                        ) { viewModel.setCategory(key) }
                    }
                }
            }

            if (state.authors.isNotEmpty()) item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(if (thai) "ผู้แก้ไข:" else "By:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PersonChip(if (thai) "ทุกคน" else "Everyone", null, state.authorId == null) { viewModel.setAuthor(null) }
                    state.authors.forEachIndexed { i, (id, name) ->
                        PersonChip(name, AhAuthorColors[i % AhAuthorColors.size], state.authorId == id) { viewModel.setAuthor(id) }
                    }
                }
            }

            state.notice?.let { n -> item { NoticeBanner(n, AhGreen, AhGreenSoft, viewModel::dismissNotice) } }
            state.error?.takeIf { it != "no_child" }?.let { e ->
                item { NoticeBanner(e, AhRed, Color(0xFFFEE2E2), viewModel::dismissNotice) }
            }

            if (state.loading && state.entries.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AhPrimary)
                }
            }
            if (!state.loading && state.visible.isEmpty()) item {
                Text(
                    if (thai) "ไม่พบกิจกรรมที่ตรงกับตัวกรอง" else "Nothing matches these filters",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            state.days.forEach { (day, rows) ->
                item(key = "day_$day") { DayHeader(thai, day, rows.size) }
                items(rows, key = { it.sourceId }) { e ->
                    val author = e.authorId
                    EntryCard(
                        thai = thai,
                        entry = e,
                        state = state,
                        onRevert = { edit -> viewModel.revert(edit, thai) },
                        onReply = if (state.canChat && onReplyToEntry != null && author != null && author != state.myUserId) {
                            { onReplyToEntry(author, ActivityKindLabels.title(e.kind, thai)) }
                        } else null,
                        onOpenPhoto = { photoOpen = it },
                        onOpenRecord = { onOpenRecord(e.kind) }
                    )
                }
            }

            val unfiltered = state.category == ActivityHistoryUiState.ALL && state.authorId == null && state.query.isBlank()
            if (state.deletions.isNotEmpty() && unfiltered) {
                item(key = "day_deleted") { DayHeader(thai, "deleted", state.deletions.size) }
                items(state.deletions, key = { "del_${it.id}" }) { d ->
                    DeletedCard(thai, d, state, onRestore = { viewModel.revert(d, thai) })
                }
            }

            if (state.canLoadMore) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AhSoft)
                        .clickable { viewModel.loadMore() }.padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ExpandMore, null, tint = AhPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (thai) "โหลดประวัติกิจกรรมย้อนหลังเพิ่มเติม" else "Load older history",
                        color = AhPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                    )
                }
            }

            if (state.canChat && caregiver != null && onChat != null) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AhPrimary).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Forum, null, tint = Color.White) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (thai) "มีข้อสงสัยเกี่ยวกับการบันทึก?" else "Questions about an entry?",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                        Text(
                            if (thai) "ส่งข้อความหาผู้ดูแล หรือถาม${caregiver.displayName}ทันที" else "Message ${caregiver.displayName} now",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onChat(caregiver.userId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AhPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (thai) "คุยกับพี่เลี้ยง" else "Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    photoOpen?.let { url ->
        AlertDialog(
            onDismissRequest = { photoOpen = null },
            confirmButton = { TextButton(onClick = { photoOpen = null }) { Text(if (thai) "ปิด" else "Close") } },
            text = { AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) }
        )
    }
}

// ----------------------------------------------------------------------------------------------

@Composable
private fun HeaderCard(thai: Boolean, state: ActivityHistoryUiState) {
    val today = LocalDate.now().toString()
    val todayAuthors = state.entries
        .filter { com.lullabyai.app.util.TimeUtils.localDate(it.occurredAt) == today }
        .mapNotNull { it.authorName }.distinct().take(3)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(AhSoft), contentAlignment = Alignment.Center) {
                    if (state.childAvatarUrl != null) {
                        AsyncImage(state.childAvatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else Text("👶", fontSize = 28.sp)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp).clip(CircleShape).background(AhGreen),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${if (thai) "น้อง " else ""}${state.childName}",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ageCompact(state.childBirthdate, thai)?.let {
                        Text(
                            it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AhPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(AhSoft).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                state.loadedAt?.let { at ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sync, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            (if (thai) "ซิงค์อัตโนมัติ: " else "Auto-synced: ") + ago(at, thai),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AhSoft.copy(alpha = 0.6f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AhPrimary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.HistoryEdu, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${state.todayCount} ${if (thai) "กิจกรรมวันนี้" else "today"}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (thai) "บันทึก & แก้ไขล่าสุด" else "Latest entries & edits", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                todayAuthors.forEachIndexed { i, name ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(AhAuthorColors[i % AhAuthorColors.size])
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(name.take(3), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    thai: Boolean,
    entry: ChildActivityEntry,
    state: ActivityHistoryUiState,
    onRevert: (RecordEdit) -> Unit,
    onReply: (() -> Unit)?,
    onOpenPhoto: (String) -> Unit,
    onOpenRecord: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val edits = state.edits[entry.sourceId].orEmpty()
    val latest = edits.firstOrNull()
    val changes = remember(latest?.id, thai) { latest?.let { RecordDiff.changes(it, thai) }.orEmpty() }
    val author = entry.authorId?.let { state.membersById[it] }
    val media = state.media[entry.sourceId]
    val seen = state.seenBy[entry.sourceId].orEmpty()
    val byOther = entry.authorId != null && entry.authorId != state.myUserId

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(author, entry.authorName)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        entry.authorName ?: (if (thai) "ผู้ดูแล" else "Caregiver"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    author?.let { a ->
                        val role = if (a.isCaregiver && !AccessRoleLabels.isFullAccess(a.role)) "nanny" else a.role
                        Text(
                            AccessRoleLabels.title(role, thai),
                            fontSize = 10.sp, color = AhPrimary, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(AhSoft).padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(clockDay(entry.occurredAt, thai), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val (actionIcon, actionText) = when {
                latest != null && RecordDiff.isClearedToNone(changes) ->
                    Icons.Filled.RemoveCircle to (if (thai) "คืนข้อมูลนี้เป็นไม่มี" else "Reverted this information to none")
                latest != null && RecordDiff.isTimeOnly(changes) -> Icons.Filled.Update to (if (thai) "แก้ไขเวลา" else "Time edited")
                latest != null -> Icons.Filled.EditNote to (if (thai) "แก้ไขข้อมูล" else "Edited")
                else -> Icons.Filled.AddCircle to (if (thai) "บันทึกใหม่" else "New")
            }
            Row(
                Modifier.clip(RoundedCornerShape(100.dp)).background(AhSoft)
                    .clickable(enabled = onOpenRecord != null, onClick = { onOpenRecord?.invoke() })
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(actionIcon, null, tint = AhPrimary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AhPrimary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AhSoft), contentAlignment = Alignment.Center) {
                Icon(kindIcon(entry.kind), null, tint = AhPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(ActivityKindLabels.title(entry.kind, thai), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        val detail = entry.detail
        if (changes.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AhSoft.copy(alpha = 0.5f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                changes.forEach { c -> ChangeRow(c) }
                if (entry.kind == "sleep") {
                    RecordDiff.sleepDuration(latest?.newRow, thai)?.let { d ->
                        Row {
                            Text(
                                if (thai) "เวลานอนรวมรอบนี้" else "Total this sleep",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                            )
                            Text(d, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AhPrimary)
                        }
                    }
                }
            }
        } else if (!detail.isNullOrBlank()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AhSoft.copy(alpha = 0.5f)).padding(12.dp)) {
                Text(detail, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AhPrimary)
            }
        }

        entry.note?.takeIf { it.isNotBlank() }?.let {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.StickyNote2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("\"$it\"", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (media != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                media.photoPath?.let { CarePhotoThumb(it, size = 72.dp, onOpen = onOpenPhoto) }
                media.voicePath?.let { CareVoiceChip(it, media.voiceSeconds, thai) }
            }
        }

        if (expanded && edits.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (thai) "ประวัติการแก้ไข (${edits.size})" else "Edit history (${edits.size})",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                edits.forEach { e ->
                    val who = e.actorId?.let { state.membersById[it]?.displayName } ?: (if (thai) "ระบบ" else "system")
                    val lines = RecordDiff.changes(e, thai)
                    Text(
                        "• ${clockDay(e.createdAt, thai)} — $who: " +
                            lines.joinToString("; ") { "${it.label} ${it.before} → ${it.after}" }
                                .ifBlank { if (thai) "ไม่มีการเปลี่ยนแปลงที่แสดงได้" else "no visible change" },
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                byOther && seen.isNotEmpty() -> {
                    Icon(Icons.Filled.DoneAll, null, tint = AhGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${seen.first().first}${if (thai) "อ่านแล้ว" else " read"} (${clock(seen.first().second, thai)})",
                        fontSize = 11.sp, color = AhGreen, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                edits.isNotEmpty() -> {
                    TextAction(
                        Icons.Filled.Visibility,
                        if (expanded) (if (thai) "ซ่อนรายละเอียด" else "Hide details") else (if (thai) "ดูรายละเอียด" else "Details"),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ) { expanded = !expanded }
                    Spacer(Modifier.weight(1f))
                }
                else -> Spacer(Modifier.weight(1f))
            }
            if (latest != null && state.isParent && !RecordDiff.isClearedToNone(changes)) {
                if (state.reverting == latest.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AhRed)
                else TextAction(Icons.Filled.History, if (thai) "คืนค่าข้อมูลเดิม" else "Restore", AhRed) { onRevert(latest) }
            } else if (onReply != null) {
                TextAction(Icons.Filled.ModeComment, if (thai) "ตอบกลับ" else "Reply", AhPrimary, onReply)
            }
        }
    }
}

@Composable
private fun DeletedCard(thai: Boolean, d: RecordEdit, state: ActivityHistoryUiState, onRestore: () -> Unit) {
    val who = d.actorId?.let { state.membersById[it]?.displayName } ?: (if (thai) "ไม่ทราบ" else "unknown")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF1F2)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.DeleteOutline, null, tint = AhRed)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (thai) "ลบแล้ว: " else "Deleted: ") + ActivityKindLabels.title(ActivityKindLabels.kindOfTable(d.tableName), thai),
                fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
            Text(
                "${if (thai) "โดย" else "by"} $who • ${clockDay(d.createdAt, thai)}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.isParent) {
            if (state.reverting == d.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AhRed)
            else TextAction(Icons.Filled.Restore, if (thai) "กู้คืน" else "Restore", AhRed, onRestore)
        }
    }
}

@Composable
private fun ChangeRow(c: RecordDiff.Change) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(c.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(c.before, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
        Icon(Icons.Filled.ArrowForward, null, tint = AhPrimary, modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
        Text(c.after, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        c.delta?.let {
            Spacer(Modifier.width(6.dp))
            Text(
                it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AhGreen,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AhGreenSoft).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AuthorAvatar(member: ChildAccessMember?, name: String?) {
    val url by produceState<String?>(null, member?.avatarUrl) { value = StorageHelper.signedAvatarUrl(member?.avatarUrl) }
    Box {
        Box(Modifier.size(40.dp).clip(CircleShape).background(AhSoft), contentAlignment = Alignment.Center) {
            if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(name?.take(1).orEmpty(), fontWeight = FontWeight.Bold, color = AhPrimary)
        }
        if (member?.role == "owner") {
            Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(AhPrimary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(thai: Boolean, day: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val (title, sub) = dayTitle(day, thai)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        sub?.let { Text("  •  $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.weight(1f))
        Text(
            "$count ${if (thai) "รายการ" else "items"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CategoryChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(100.dp))
            .background(if (selected) AhPrimary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) AhPrimary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Color.White else AhPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PersonChip(label: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(100.dp))
            .background(if (selected) AhSoft else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) AhPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) AhPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1
        )
    }
}

@Composable
private fun TextAction(icon: ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun NoticeBanner(text: String, fg: Color, bg: Color, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onDismiss).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(16.dp))
    }
}

// ----------------------------------------------------------------------------------------------

private fun categoryIcon(key: String): ImageVector = when (key) {
    "nutrition" -> Icons.Filled.LocalDrink
    "growth" -> Icons.Filled.Insights
    "sleep" -> Icons.Filled.Bedtime
    "medical" -> Icons.Filled.Medication
    "waste" -> Icons.Filled.BabyChangingStation
    "care" -> Icons.Filled.Diversity3
    else -> Icons.Filled.Apps
}

private fun kindIcon(kind: String): ImageVector = when {
    kind == "growth" -> Icons.Filled.Scale
    kind == "milk" -> Icons.Filled.WaterDrop
    kind == "food" -> Icons.Filled.Restaurant
    kind == "sleep" -> Icons.Filled.Bedtime
    kind == "waste" -> Icons.Filled.BabyChangingStation
    kind == "vaccine" -> Icons.Filled.Vaccines
    kind == "symptom" -> Icons.Filled.MonitorHeart
    kind == "medicine" -> Icons.Filled.Medication
    kind.startsWith("care_") -> Icons.Filled.Diversity3
    else -> Icons.Filled.EditNote
}

private val TH_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

/** "วันนี้ • 18 มี.ค. 2568" — Thai dates carry the Buddhist year. */
private fun dayTitle(day: String, thai: Boolean): Pair<String, String?> {
    if (day == "deleted") return (if (thai) "รายการที่ถูกลบ" else "Deleted entries") to null
    val d = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day to null
    val today = LocalDate.now()
    val full = if (thai) "${d.dayOfMonth} ${TH_MONTHS[d.monthValue - 1]} ${d.year + 543}" else d.toString()
    return when (d) {
        today -> (if (thai) "วันนี้" else "Today") to full
        today.minusDays(1) -> (if (thai) "เมื่อวานนี้" else "Yesterday") to full
        else -> full to null
    }
}

private fun local(iso: String) =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()

private fun clock(iso: String, thai: Boolean): String =
    local(iso)?.let { "%02d:%02d".format(it.hour, it.minute) + if (thai) " น." else "" } ?: ""

/** "10:15 น." today, "เมื่อวานนี้ 15:30 น." yesterday, else the date and time. */
private fun clockDay(iso: String, thai: Boolean): String {
    val t = local(iso) ?: return ""
    val hm = clock(iso, thai)
    val today = LocalDate.now()
    return when (t.toLocalDate()) {
        today -> hm
        today.minusDays(1) -> (if (thai) "เมื่อวานนี้ " else "Yesterday ") + hm
        else -> "${t.dayOfMonth} ${if (thai) TH_MONTHS[t.monthValue - 1] else t.month.name.take(3)} $hm"
    }
}

private fun ago(iso: String, thai: Boolean): String {
    val mins = runCatching { Duration.between(Instant.parse(iso), Instant.now()).toMinutes() }.getOrDefault(0L).coerceAtLeast(0L)
    return when {
        mins < 1 -> if (thai) "เมื่อสักครู่" else "just now"
        mins < 60 -> if (thai) "$mins นาทีที่แล้ว" else "$mins min ago"
        else -> if (thai) "${mins / 60} ชม.ที่แล้ว" else "${mins / 60} h ago"
    }
}

/** "7 ด. 1 สป." — the age pill beside the name. */
private fun ageCompact(birthdate: String?, thai: Boolean): String? {
    val p = runCatching { Period.between(LocalDate.parse(birthdate), LocalDate.now()) }.getOrNull() ?: return null
    val months = p.years * 12 + p.months
    val weeks = p.days / 7
    return if (thai) "$months ด. $weeks สป." else "${months}m ${weeks}w"
}

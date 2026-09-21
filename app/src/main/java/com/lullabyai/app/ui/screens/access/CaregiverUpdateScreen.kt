@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.lullabyai.app.ui.screens.access

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.CareLog
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.util.VoiceNoteRecorder
import com.lullabyai.app.viewmodel.CareKinds
import com.lullabyai.app.viewmodel.CaregiverUpdateViewModel
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId

private val QrPrimary = Color(0xFF173DB8)
private val QrPrimarySoft = Color(0xFFE8EDFD)
private val QrGreen = Color(0xFF15803D)
private val QrGreenSoft = Color(0xFFDCFCE7)
private val QrRed = Color(0xFFB91C1C)

/**
 * บันทึกกิจกรรมด่วนสำหรับผู้ดูแล (Caregiver Quick Record), built to the Stitch frame.
 *
 * Everything on it is real: the shift line comes from the open handover, "คุณแม่อ่านแล้ว" from
 * record_reads, the photo and voice note are uploaded to the private care-media bucket, and the
 * urgent alert goes through line-notify-parents, which decides on its own who may be told.
 */
@Composable
fun CaregiverUpdateScreen(
    viewModel: CaregiverUpdateViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onChat: ((String) -> Unit)? = null
) {
    val thai = LocalStrings.current.isThai
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val recorder = remember { VoiceNoteRecorder(context) }

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
            viewModel.setPhoto(out.toByteArray())
        }
    }

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess { viewModel.setRecording(true) }
            .onFailure { viewModel.reportError((if (thai) "เปิดไมโครโฟนไม่ได้: " else "Microphone unavailable: ") + it.message) }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else viewModel.reportError(if (thai) "ต้องอนุญาตไมโครโฟนเพื่อบันทึกเสียง" else "Microphone permission is needed to record")
    }

    fun toggleMic() {
        if (state.recording) {
            val note = recorder.stop()
            if (note == null) viewModel.setRecording(false) else viewModel.setVoice(note.bytes, note.seconds)
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LullabyTopBar(
                title = if (thai) "บันทึกกิจกรรมด่วน" else "Quick record",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            SubmitBar(
                thai = thai,
                count = state.pendingCount,
                enabled = state.canSubmit,
                saving = state.saving,
                onSubmit = { viewModel.submit(thai) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Caregiver Quick Record",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("${if (thai) "น้อง " else ""}${state.childName} (${ageShort(state.childBirthdate, thai)})", QrPrimary, QrPrimarySoft)
                if (state.myName.isNotBlank()) {
                    Pill("${state.myName} ${if (thai) "กำลังดูแล" else "on duty"}", QrGreen, QrGreenSoft)
                }
            }

            ChildCard(
                thai = thai,
                name = state.childName,
                birthdate = state.childBirthdate,
                avatarUrl = state.childAvatarUrl,
                latestMood = state.latestMood,
                myName = state.myName,
                shiftStart = state.shiftStart,
                shiftEnd = state.shiftEnd,
                unread = state.totalUnread,
                onChat = state.parents.firstOrNull()?.let { p -> onChat?.let { cb -> { cb(p.userId) } } }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(QrPrimary))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (thai) "บันทึกสถานะและกิจกรรมสด" else "Live status and activity",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (thai) "แตะเลือกอารมณ์และสิ่งที่น้องกำลังทำอยู่ ณ ตอนนี้ แบบยืดหยุ่นตามจริง"
                        else "Tap how they feel and what they're doing right now",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Pill(if (thai) "เรียลไทม์" else "Live", QrPrimary, QrPrimarySoft)
            }

            state.savedMessage?.let { msg ->
                Banner(Icons.Filled.CheckCircle, msg, QrGreen, QrGreenSoft) { viewModel.dismissSaved() }
            }
            state.error?.let { err ->
                Banner(Icons.Filled.ErrorOutline, err, QrRed, Color(0xFFFEE2E2)) {}
            }

            Section(
                icon = Icons.Filled.SentimentSatisfied,
                title = if (thai) "อารมณ์ตอนนี้น้องเป็นอย่างไร?" else "How are they feeling?",
                subtitle = if (thai) "แตะเลือกอารมณ์ล่าสุดที่พี่เลี้ยงสังเกตเห็น" else "Pick what you see right now",
                trailing = if (thai) "เลือกได้ทันที" else "One tap"
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareKinds.MOODS.forEach { m ->
                        val label = "${m.emoji} ${if (thai) m.th else m.en}" +
                            if (m.key == CareKinds.MOOD_UNWELL) (if (thai) " (แจ้งเตือนคุณแม่เป็นพิเศษ)" else " (alerts the parent)") else ""
                        SelectRow(label, selected = state.mood == m.key, danger = m.key == CareKinds.MOOD_UNWELL) {
                            viewModel.setMood(m.key)
                        }
                    }
                }
            }

            Section(
                icon = Icons.Filled.Interests,
                title = if (thai) "ตอนนี้น้องกำลังทำอะไรอยู่?" else "What are they doing?",
                subtitle = if (thai) "เลือกกิจกรรมสดที่กำลังเกิดขึ้น หรือเพิ่มเองได้" else "Pick what's happening, or add your own",
                trailing = if (thai) "แตะเลือกได้หลายข้อ" else "Pick several"
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareKinds.ALL.forEach { k ->
                        KindChip("${k.emoji}  ${if (thai) k.th else k.en}", k.kind in state.selectedKinds) {
                            viewModel.toggleKind(k.kind)
                        }
                    }
                }
                if ("other" in state.selectedKinds) {
                    OutlinedTextField(
                        value = state.otherLabel,
                        onValueChange = viewModel::setOtherLabel,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        placeholder = { Text(if (thai) "ระบุกิจกรรม เช่น ไปสวนสาธารณะ" else "What was it? e.g. park walk", fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Section(
                icon = Icons.Filled.EditNote,
                title = if (thai) "โน้ตบันทึกสิ่งที่น้องทำล่าสุด (ยืดหยุ่นตามจริง)" else "Note what just happened",
                subtitle = null,
                trailing = null
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(QrPrimarySoft.copy(alpha = 0.6f)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (thai) "บันทึกสิ่งที่น้องทำล่าสุด เช่น น้องนั่งเล่นคนเดียวได้ดี ทานผลไม้หมดเกลี้ยง"
                                else "e.g. played on their own nicely, finished all the fruit",
                                fontSize = 13.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        ),
                        minLines = 2
                    )
                    IconButton(
                        onClick = { viewModel.submit(thai) },
                        enabled = state.canSubmit,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(if (state.canSubmit) QrPrimary else QrPrimary.copy(alpha = 0.3f))
                    ) { Icon(Icons.Filled.Send, if (thai) "ส่ง" else "Send", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }

            Section(
                icon = Icons.Filled.AddAPhoto,
                title = if (thai) "แนบรูปน่ารัก & บันทึกเสียงถึงคุณแม่" else "Photo and voice note for the parent",
                subtitle = null,
                trailing = if (state.photo != null) (if (thai) "1 ภาพพร้อมส่ง" else "1 photo ready") else null
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.photo != null) {
                        val bmp = remember(state.photo) {
                            state.photo?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                        }
                        Box(Modifier.weight(1f).height(128.dp).clip(RoundedCornerShape(16.dp))) {
                            bmp?.let {
                                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            IconButton(
                                onClick = { viewModel.setPhoto(null) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(Color(0x99000000))
                            ) { Icon(Icons.Filled.Close, if (thai) "ลบรูป" else "Remove", tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Column(
                        Modifier.weight(1f).height(128.dp).clip(RoundedCornerShape(16.dp))
                            .background(QrPrimarySoft.copy(alpha = 0.6f)).clickable { camera.launch(null) }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = QrPrimary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.photo == null) (if (thai) "ถ่ายรูปส่งคุณแม่" else "Take a photo") else (if (thai) "ถ่ายใหม่" else "Retake"),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = QrPrimary
                        )
                        Text(if (thai) "คุณแม่ชอบดูรูปตอนบ่าย" else "Parents love an afternoon photo", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(QrPrimarySoft.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Chat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            state.recording -> if (thai) "กำลังบันทึกเสียง… แตะไมค์อีกครั้งเพื่อหยุด (สูงสุด 2 นาที)" else "Recording… tap the mic to stop (2 min max)"
                            state.voice != null -> (if (thai) "ข้อความเสียงพร้อมส่ง " else "Voice note ready ") + "${state.voiceSeconds}s"
                            else -> if (thai) "กดไมค์เพื่อพูดข้อความถึงคุณแม่ (เช่น น้องยิ้มเก่ง ดื่มนมหมด)" else "Tap the mic to say something to the parent"
                        },
                        fontSize = 12.sp, modifier = Modifier.weight(1f),
                        color = if (state.recording) QrRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.voice != null && !state.recording) {
                        IconButton(onClick = { viewModel.setVoice(null, 0) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, if (thai) "ลบเสียง" else "Delete", tint = QrRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = { toggleMic() },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(if (state.recording) QrRed else QrPrimary.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            if (state.recording) Icons.Filled.Stop else Icons.Filled.Mic,
                            if (thai) "บันทึกเสียง" else "Record",
                            tint = if (state.recording) Color.White else QrPrimary, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (state.urgent) Color(0xFFFFF1F2) else QrPrimarySoft.copy(alpha = 0.6f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ForwardToInbox, null, tint = if (state.urgent) QrRed else QrPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (thai) "แจ้งเตือนด่วนไปยังคุณแม่ทันที" else "Alert the parent now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (thai) "ส่งข้อความ LINE ถึงคุณแม่เมื่อกดบันทึก (สูงสุด 5 ครั้ง/วัน)" else "Sends a LINE message when you save (5 a day)",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.urgent, onCheckedChange = viewModel::setUrgent)
            }

            TodayList(thai = thai, logs = state.today, seenBy = state.seenBy)
        }
    }
}

// ----------------------------------------------------------------------------------------------

@Composable
private fun ChildCard(
    thai: Boolean,
    name: String,
    birthdate: String?,
    avatarUrl: String?,
    latestMood: CareLog?,
    myName: String,
    shiftStart: Int?,
    shiftEnd: Int?,
    unread: Int,
    onChat: (() -> Unit)?
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(QrPrimarySoft), contentAlignment = Alignment.Center) {
                if (avatarUrl != null) AsyncImage(avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("👶", fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${if (thai) "น้อง " else ""}$name", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Pill(ageLong(birthdate, thai), QrPrimary, QrPrimarySoft)
                }
                latestMood?.let { m ->
                    Text(
                        "${CareKinds.moodEmoji(m.mood)} ${CareKinds.moodLabel(m.mood, thai)} • " +
                            (if (thai) "อัปเดต " else "updated ") + minutesAgo(m.loggedAt, thai),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onChat != null) {
                BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                    IconButton(onClick = onChat, modifier = Modifier.size(44.dp).clip(CircleShape).background(QrPrimarySoft)) {
                        Icon(Icons.Filled.NotificationsActive, if (thai) "คุยกับคุณแม่" else "Message the parent", tint = QrPrimary)
                    }
                }
            }
        }
        if (shiftStart != null || myName.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(QrPrimarySoft.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(QrGreen))
                Spacer(Modifier.width(8.dp))
                Text(
                    buildString {
                        append(if (thai) "กะเวร: " else "Shift: ")
                        append(myName.ifBlank { if (thai) "ผู้ดูแล" else "caregiver" })
                        if (shiftStart != null) {
                            append(" • ").append(hhmm(shiftStart)).append(" - ").append(shiftEnd?.let { hhmm(it) } ?: "…")
                            if (thai) append(" น.")
                        }
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.CloudDone, null, tint = QrPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (thai) "ซิงค์คุณแม่" else "Synced", fontSize = 11.sp, color = QrPrimary)
            }
        }
    }
}

@Composable
private fun TodayList(thai: Boolean, logs: List<CareLog>, seenBy: Map<String, List<String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(QrGreen))
            Spacer(Modifier.width(8.dp))
            Text(
                (if (thai) "บันทึกแล้ววันนี้ " else "Logged today ") + "(${logs.size} ${if (thai) "รายการ" else "entries"})",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            if (logs.isNotEmpty()) Pill(if (thai) "ส่งแล้วทั้งหมด" else "All sent", QrGreen, QrGreenSoft)
        }
        if (logs.isEmpty()) {
            Text(
                if (thai) "ยังไม่มีบันทึกวันนี้ เลือกอารมณ์หรือกิจกรรมด้านบนแล้วกดบันทึก" else "Nothing logged yet today",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                logs.forEachIndexed { i, log ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    TodayRow(thai, log, seenBy[log.id].orEmpty())
                }
            }
        }
    }
}

@Composable
private fun TodayRow(thai: Boolean, log: CareLog, seenBy: List<String>) {
    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(QrPrimarySoft), contentAlignment = Alignment.Center) {
            Text(CareKinds.emoji(log.kind), fontSize = 18.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    CareKinds.label(log.kind, thai) + (log.mood?.let { " • ${CareKinds.moodLabel(it, thai)}" } ?: ""),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(clock(log.loggedAt, thai), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            log.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            log.abnormal?.takeIf { it.isNotBlank() }?.let {
                Text("⚠ $it", fontSize = 12.sp, color = QrRed, fontWeight = FontWeight.SemiBold)
            }
            if (log.photoPath != null || log.voicePath != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    log.photoPath?.let { CarePhotoThumb(it, size = 56.dp) }
                    log.voicePath?.let { CareVoiceChip(it, log.voiceSeconds, thai) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                log.authorName?.takeIf { it.isNotBlank() }?.let { Pill(it, QrPrimary, QrPrimarySoft) }
                Icon(Icons.Filled.DoneAll, null, tint = if (seenBy.isNotEmpty()) QrGreen else MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                Text(
                    if (seenBy.isNotEmpty()) (if (thai) "${seenBy.first()}อ่านแล้ว" else "Seen by ${seenBy.first()}")
                    else (if (thai) "ซิงค์ข้อมูลแล้ว" else "Synced"),
                    fontSize = 11.sp, color = if (seenBy.isNotEmpty()) QrGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubmitBar(thai: Boolean, count: Int, enabled: Boolean, saving: Boolean, onSubmit: () -> Unit) {
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (thai) "บันทึกที่เลือก" else "Selected", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$count ${if (thai) "รายการ" else "entries"}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QrPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QrPrimary)
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (thai) "บันทึกกิจกรรมทั้งหมด" else "Save all", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, subtitle: String?, trailing: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(QrPrimarySoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = QrPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                subtitle?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.let { Text(it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = QrPrimary) }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SelectRow(label: String, selected: Boolean, danger: Boolean, onClick: () -> Unit) {
    val accent = if (danger) QrRed else QrPrimary
    Row(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else QrPrimarySoft.copy(alpha = 0.5f))
            .border(BorderStroke(1.dp, if (selected) accent else Color.Transparent), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
            tint = if (selected) accent else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) QrPrimary else QrPrimarySoft.copy(alpha = 0.6f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun Banner(icon: ImageVector, text: String, fg: Color, bg: Color, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onDismiss).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ----------------------------------------------------------------------------------------------

private fun hhmm(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun clock(iso: String, thai: Boolean): String = runCatching {
    val t = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
    "%02d:%02d".format(t.hour, t.minute) + if (thai) " น." else ""
}.getOrDefault("")

private fun minutesAgo(iso: String, thai: Boolean): String = runCatching {
    val mins = Duration.between(OffsetDateTime.parse(iso).toInstant(), java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    when {
        mins < 1 -> if (thai) "เมื่อสักครู่" else "just now"
        mins < 60 -> if (thai) "$mins นาทีที่แล้ว" else "$mins min ago"
        else -> if (thai) "${mins / 60} ชม.ที่แล้ว" else "${mins / 60} h ago"
    }
}.getOrDefault("")

private fun agePeriod(birthdate: String?): Period? =
    runCatching { Period.between(LocalDate.parse(birthdate), LocalDate.now()) }.getOrNull()

/** "7 เดือน" — the pill beside the name at the top. */
private fun ageShort(birthdate: String?, thai: Boolean): String {
    val p = agePeriod(birthdate) ?: return ""
    val months = p.years * 12 + p.months
    return if (thai) "$months เดือน" else "$months mo"
}

/** "7 เดือน 12 วัน" — the chip on the child card. */
private fun ageLong(birthdate: String?, thai: Boolean): String {
    val p = agePeriod(birthdate) ?: return ""
    val months = p.years * 12 + p.months
    return if (thai) "$months เดือน ${p.days} วัน" else "${months}m ${p.days}d"
}

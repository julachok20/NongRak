@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AudioReplayCard
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.VideoReplayCard
import com.lullabyai.app.ui.components.supportedFormatHint
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.ImageUtils
import com.lullabyai.app.viewmodel.DiaryViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Stitch: Add Memory (เพิ่มความทรงจำ). */
@Composable
fun AddMemoryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var mediaType by remember { mutableStateOf("photo") }
    var description by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var memoryDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var milestoneTag by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var pendingMime by remember { mutableStateOf<String?>(null) }
    // Kept alongside the bytes purely so the replay cards can hand a playable source to
    // MediaPlayer/VideoView — uploads still go through pendingBytes.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    fun clearAttachment() {
        pendingBytes = null
        pendingName = null
        pendingMime = null
        pendingUri = null
    }

    // True once the parent has typed or attached something worth warning about before discarding.
    val hasUnsavedWork = pendingBytes != null || description.isNotBlank() ||
        title.isNotBlank() || milestoneTag != null

    fun ingestUri(uri: Uri, asImage: Boolean) {
        val bytes = if (asImage) ImageUtils.downscaleToJpeg(context, uri)
        else ImageUtils.readBytes(context, uri)
        if (bytes == null) {
            formError = if (isThai) "อ่านไฟล์ไม่สำเร็จ" else "Could not read file"
            return
        }
        if (bytes.size > 10 * 1024 * 1024) {
            formError = if (isThai) "ไฟล์ต้องไม่เกิน 10MB" else "File must be ≤ 10MB"
            return
        }
        pendingBytes = bytes
        pendingName = uri.lastPathSegment ?: if (asImage) "memory.jpg" else "memory.bin"
        pendingMime = context.contentResolver.getType(uri) ?: if (asImage) "image/jpeg" else null
        pendingUri = uri
        formError = null
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            mediaType = "photo"
            ingestUri(it, asImage = true)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            mediaType = "video"
            ingestUri(it, asImage = false)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            mediaType = "audio"
            ingestUri(it, asImage = false)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(uri) ?: ""
            when {
                mime.startsWith("image/") -> { mediaType = "photo"; ingestUri(uri, asImage = true) }
                mime.startsWith("video/") -> { mediaType = "video"; ingestUri(uri, asImage = false) }
                mime.startsWith("audio/") -> { mediaType = "audio"; ingestUri(uri, asImage = false) }
                else -> {
                    // PDF / other attach — store as note with attachment path via photo bucket mime
                    mediaType = if (pendingBytes == null && description.isBlank()) "note" else mediaType
                    ingestUri(uri, asImage = false)
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            icon = { Icon(Icons.Filled.WarningAmber, null, tint = Error) },
            title = { Text(if (isThai) "ยกเลิกการบันทึก?" else "Discard this memory?") },
            text = {
                Text(
                    if (isThai) "สิ่งที่คุณกรอกและไฟล์ที่แนบไว้จะไม่ถูกบันทึก"
                    else "What you've written and the attached file won't be saved."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    clearAttachment()
                    onBack()
                }) { Text(if (isThai) "ยกเลิกและออก" else "Discard", color = Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(if (isThai) "กรอกต่อ" else "Keep editing")
                }
            }
        )
    }

    if (showDatePicker) {
        val initial = remember {
            runCatching {
                java.time.LocalDate.parse(memoryDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse { System.currentTimeMillis() }
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        memoryDate = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") }
            }
        ) { DatePicker(state = dateState) }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เพิ่มความทรงจำ" else "Add Memory",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (isThai) "เลือกสื่อความประทับใจ" else "Choose media",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MediaTypeCard(
                    Icons.Filled.PhotoCamera,
                    if (isThai) "รูปภาพ" else "Photo",
                    selected = mediaType == "photo",
                    Modifier.weight(1f)
                ) {
                    mediaType = "photo"
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                MediaTypeCard(
                    Icons.Filled.Videocam,
                    if (isThai) "วิดีโอ" else "Video",
                    selected = mediaType == "video",
                    Modifier.weight(1f)
                ) {
                    mediaType = "video"
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
                MediaTypeCard(
                    Icons.Filled.Mic,
                    if (isThai) "บันทึกเสียง" else "Audio",
                    selected = mediaType == "audio",
                    Modifier.weight(1f)
                ) {
                    mediaType = "audio"
                    audioPicker.launch("audio/*")
                }
            }

            // Which formats the currently-selected type actually accepts. Shown even before a
            // pick so the parent knows what to look for in the picker.
            Text(
                supportedFormatHint(mediaType, isThai),
                style = MaterialTheme.typography.labelSmall,
                color = Outline
            )

            // Replay the picked clip before committing it. Photos already preview via the
            // filename row, so only audio/video get a player.
            pendingUri?.let { uri ->
                when (mediaType) {
                    "audio" -> AudioReplayCard(uri = uri, fileName = pendingName, isThai = isThai)
                    "video" -> VideoReplayCard(uri = uri, isThai = isThai)
                    else -> Unit
                }
            }

            Text(
                if (isThai) "แนบเอกสาร (Attach Files)" else "Attach files",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Card(
                onClick = { filePicker.launch("*/*") },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, OutlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CloudUpload, null, tint = Primary, modifier = Modifier.size(48.dp))
                    Text(
                        if (pendingName != null)
                            (if (isThai) "เลือกแล้ว: " else "Selected: ") + pendingName
                        else if (isThai) "แตะเพื่ออัปโหลดไฟล์ (PDF, JPG, PNG)"
                        else "Tap to upload (PDF, JPG, PNG)",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(supportedFormatHint("file", isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
                    // Explicit Cancel for a picked-but-not-yet-saved attachment — user asked for
                    // a real button to back out of the attach step rather than just re-picking.
                    if (pendingName != null) {
                        TextButton(onClick = { clearAttachment() }) {
                            Icon(Icons.Filled.Close, null, tint = Error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isThai) "ยกเลิกไฟล์นี้" else "Cancel attachment", color = Error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isThai) "หัวข้อ (ไม่บังคับ)" else "Title (optional)") },
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = formatMemoryDate(memoryDate, isThai),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                label = { Text(if (isThai) "วันที่บันทึก" else "Memory date") },
                leadingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = Primary) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.EditCalendar, null)
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text(if (isThai) "เล่าเรื่องราวความประทับใจ..." else "Tell the story...") },
                placeholder = {
                    Text(
                        if (isThai) "วันนี้ลูกน้อยทำอะไรที่ทำให้คุณยิ้มได้บ้าง?"
                        else "What made you smile today?"
                    )
                },
                shape = RoundedCornerShape(14.dp)
            )

            Text(
                if (isThai) "แท็กเหตุการณ์สำคัญ (Milestone)" else "Milestone tags",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            val tags = listOf(
                "first_step" to (if (isThai) "ก้าวแรก" else "First steps"),
                "first_word" to (if (isThai) "คำแรก" else "First words"),
                "first_smile" to (if (isThai) "ยิ้มแรก" else "First smile"),
                "first_tooth" to (if (isThai) "ฟันซี่แรก" else "First tooth")
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // wrap via Flow-like rows
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.take(2).forEach { (key, label) ->
                        FilterChip(
                            selected = milestoneTag == key,
                            onClick = { milestoneTag = if (milestoneTag == key) null else key },
                            label = { Text(label) },
                            leadingIcon = if (milestoneTag == key) {
                                { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.drop(2).forEach { (key, label) ->
                        FilterChip(
                            selected = milestoneTag == key,
                            onClick = { milestoneTag = if (milestoneTag == key) null else key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Primary.copy(0.35f), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isThai) "ทุกเสี้ยววินาทีคือสิ่งล้ำค่า เราช่วยรักษาไว้ให้คุณ"
                    else "Every moment is precious — we help you keep it",
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    textAlign = TextAlign.Center
                )
            }

            formError?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    if (pendingBytes == null && description.isBlank()) {
                        formError = if (isThai) "กรุณาเพิ่มรูป/ไฟล์ หรือเขียนเรื่องราว"
                        else "Add a photo/file or write a story"
                        return@Button
                    }
                    val type = when {
                        pendingBytes == null && description.isNotBlank() -> "note"
                        else -> mediaType
                    }
                    vm.addMemory(
                        title = title.ifBlank { null },
                        description = description.ifBlank { null },
                        memoryDate = memoryDate,
                        mediaType = type,
                        mediaBytes = pendingBytes,
                        fileName = pendingName,
                        mimeType = pendingMime,
                        milestoneTag = milestoneTag,
                        isMilestone = milestoneTag != null
                    ) { ok ->
                        if (ok) onBack()
                        else formError = if (isThai) "บันทึกไม่สำเร็จ" else "Save failed"
                    }
                },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกความทรงจำ" else "Save memory", fontWeight = FontWeight.Bold)
                }
            }

            // Screen-level escape hatch. Confirms only when there is something to lose, so an
            // accidental entry into this screen backs out in one tap.
            OutlinedButton(
                onClick = { if (hasUnsavedWork) showDiscardConfirm = true else onBack() },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, OutlineVariant)
            ) {
                Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "ยกเลิก" else "Cancel", color = OnSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MediaTypeCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFDBE1FF) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null,
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = CircleShape, color = Primary.copy(0.1f)) {
                Icon(icon, null, tint = Primary, modifier = Modifier.padding(10.dp))
            }
            Text(label, color = Primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

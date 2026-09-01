@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.AudioReplayCard
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.VideoReplayCard
import com.lullabyai.app.ui.components.supportedFormatHint
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.ImageUtils
import com.lullabyai.app.viewmodel.DiaryViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Stitch 49.Diary: Add Memory (เพิ่มความทรงจำ)
 * Upgraded with media selector cards, attachment dropzone, milestone chips, social sharing, and validation.
 */
@Composable
fun AddMemoryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var mediaType by remember { mutableStateOf("photo") }
    var description by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var memoryDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var milestoneTag by remember { mutableStateOf<String?>(null) }
    var customTagInput by remember { mutableStateOf("") }
    var showCustomTagDialog by remember { mutableStateOf(false) }

    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var pendingMime by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showVideoStudio by remember { mutableStateOf(false) }

    // Video is the only type with a choice to make: record it now, or pick one already on the
    // phone. Photo and audio go straight to their picker, and neither ever reaches the studio.
    var showVideoSourceSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    fun clearAttachment() {
        pendingBytes = null
        pendingName = null
        pendingMime = null
        pendingUri = null
    }

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
            // Names the size that arrived, not just the rule. "ไฟล์ต้องไม่เกิน 10MB" on a 34MB
            // clip leaves the parent guessing whether they missed by a little or a lot.
            val actualMb = bytes.size / (1024 * 1024)
            formError = if (isThai) "ไฟล์ใหญ่เกินไป (${actualMb}MB) — เก็บได้ไม่เกิน 10MB"
            else "File is too large (${actualMb}MB) — the limit is 10MB"
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
            // Straight on into the studio. The parent picked "อัปโหลดคลิป และแก้ไข" - editing is
            // the point of the choice, not an extra step they have to find afterwards.
            if (pendingUri != null) showVideoStudio = true
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
                else -> { mediaType = "document"; ingestUri(uri, asImage = false) }
            }
        }
    }


    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เพิ่มความทรงจำ" else "Add Memory",
                onBack = { if (hasUnsavedWork) showDiscardConfirm = true else onBack() },
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Media Selection Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isThai) "เลือกสื่อความประทับใจ" else "Select Media Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MediaTypeChoiceCard(
                        icon = Icons.Filled.PhotoCamera,
                        label = if (isThai) "รูปภาพ" else "Photo",
                        selected = mediaType == "photo",
                        modifier = Modifier.weight(1f)
                    ) {
                        mediaType = "photo"
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }

                    MediaTypeChoiceCard(
                        icon = Icons.Filled.Videocam,
                        label = if (isThai) "วิดีโอ" else "Video",
                        selected = mediaType == "video",
                        modifier = Modifier.weight(1f)
                    ) {
                        mediaType = "video"
                        showVideoSourceSheet = true
                    }

                    MediaTypeChoiceCard(
                        icon = Icons.Filled.Mic,
                        label = if (isThai) "บันทึกเสียง" else "Audio",
                        selected = mediaType == "audio",
                        modifier = Modifier.weight(1f)
                    ) {
                        mediaType = "audio"
                        audioPicker.launch("audio/*")
                    }
                }
            }

            // Attachment / Dropzone Card
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isThai) "แนบเอกสาร / ไฟล์ (Attach Files)" else "Attach Files",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                if (pendingBytes != null) {
                    // Preview Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.3f)),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        when (mediaType) {
                                            "video" -> Icons.Filled.Videocam
                                            "audio" -> Icons.Filled.Mic
                                            "document" -> Icons.Filled.Description
                                            else -> Icons.Filled.Image
                                        },
                                        null,
                                        tint = Primary
                                    )
                                    Text(
                                        pendingName ?: "attached_file",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { clearAttachment() }) {
                                    Icon(Icons.Filled.Close, null, tint = Error)
                                }
                            }

                            if (mediaType == "photo" && pendingUri != null) {
                                AsyncImage(
                                    model = pendingUri,
                                    contentDescription = "Uploaded Photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (mediaType == "video" && pendingUri != null) {
                                VideoReplayCard(
                                    uri = pendingUri!!,
                                    isThai = isThai,
                                    modifier = Modifier.fillMaxWidth(),
                                    onEditStudio = { showVideoStudio = true }
                                )
                            } else if (mediaType == "audio" && pendingUri != null) {
                                AudioReplayCard(
                                    uri = pendingUri!!,
                                    fileName = pendingName,
                                    isThai = isThai,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        onClick = { filePicker.launch("*/*") },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.25f)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CloudUpload, null, tint = Primary, modifier = Modifier.size(44.dp))
                            Text(
                                if (isThai) "แตะเพื่ออัปโหลดไฟล์ (PDF, JPG, PNG, MP4)" else "Tap to upload file (PDF, JPG, PNG, MP4)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            Text(
                                if (isThai) "ขนาดสูงสุด 10MB" else "Max size 10MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = Outline
                            )
                        }
                    }
                }
            }

            // Date Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isThai) "วันที่บันทึก (Memory Date)" else "Memory Date",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant
                )
                OutlinedTextField(
                    value = formatMemoryDate(memoryDate, isThai),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarToday, null, tint = Primary)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = OutlineVariant
                    )
                )
            }

            // Story Description
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isThai) "เล่าเรื่องราวความประทับใจ..." else "Story & Description",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(if (isThai) "วันนี้ลูกน้อยทำอะไรที่ทำให้คุณยิ้มได้บ้าง?" else "What made you smile today?")
                    },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = OutlineVariant
                    )
                )
            }

            // Milestone Chips Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                    Text(
                        text = if (isThai) "แท็กเหตุการณ์สำคัญ (Milestone)" else "Milestone Tag",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant
                    )
                }

                val standardTags = listOf(
                    "first_step" to (if (isThai) "ก้าวแรก" else "First Step"),
                    "first_word" to (if (isThai) "คำแรก" else "First Word"),
                    "first_smile" to (if (isThai) "ยิ้มแรก" else "First Smile"),
                    "first_tooth" to (if (isThai) "ฟันซี่แรก" else "First Tooth")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    standardTags.forEach { (tagKey, tagLabel) ->
                        val isSelected = milestoneTag == tagKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { milestoneTag = if (isSelected) null else tagKey },
                            label = { Text(tagLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(100.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SecondaryContainer,
                                selectedLabelColor = Primary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = OnSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Primary else OutlineVariant
                            )
                        )
                    }
                }

                // Custom Tag Button
                OutlinedButton(
                    onClick = { showCustomTagDialog = true },
                    shape = RoundedCornerShape(100.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp), tint = Primary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (milestoneTag != null && standardTags.none { it.first == milestoneTag }) milestoneTag!!
                        else (if (isThai) "เพิ่มแท็กอื่นๆ" else "Add Custom Tag"),
                        color = Primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Atmosphere Quote
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Primary.copy(alpha = 0.35f), modifier = Modifier.size(36.dp))
                Text(
                    text = if (isThai) "ทุกเสี้ยววินาทีคือสิ่งล้ำค่า เราช่วยรักษาไว้ให้คุณ" else "Every moment is precious — we help keep it safe",
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    textAlign = TextAlign.Center
                )
            }

            // Export to Social Section (TikTok, Instagram, YouTube Shorts with NongRak License)
            SocialExportSection(
                title = title.ifBlank { null },
                description = description.ifBlank { null },
                date = memoryDate,
                mediaUri = pendingUri,
                mimeType = pendingMime,
                isThai = isThai
            )


            formError?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            // Save Memory Button
            Button(
                onClick = {
                    if (pendingBytes == null && description.isBlank()) {
                        formError = if (isThai) "กรุณาเพิ่มรูป/ไฟล์ หรือเขียนเรื่องราว" else "Add a photo/file or write a story"
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
                        if (ok) {
                            Toast.makeText(
                                context,
                                if (isThai) "บันทึกความทรงจำเรียบร้อยแล้ว ✨" else "Memory saved ✨",
                                Toast.LENGTH_SHORT
                            ).show()
                            onBack()
                        } else {
                            formError = if (isThai) "บันทึกไม่สำเร็จ" else "Save failed"
                        }
                    }
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = Primary),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "บันทึกความทรงจำ" else "Save Memory",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Cancel Button
            OutlinedButton(
                onClick = { if (hasUnsavedWork) showDiscardConfirm = true else onBack() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant)
            ) {
                Text(if (isThai) "ยกเลิก" else "Cancel", color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Custom Tag Dialog
    if (showCustomTagDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTagDialog = false },
            title = { Text(if (isThai) "เพิ่มแท็กเหตุการณ์สำคัญ" else "Add Milestone Tag") },
            text = {
                OutlinedTextField(
                    value = customTagInput,
                    onValueChange = { customTagInput = it },
                    placeholder = { Text(if (isThai) "เช่น คลานครั้งแรก, ว่ายน้ำ..." else "e.g. First swim...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customTagInput.isNotBlank()) {
                            milestoneTag = customTagInput.trim()
                        }
                        showCustomTagDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(if (isThai) "บันทึกแท็ก" else "Save Tag")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTagDialog = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    // Discard Confirmation Dialog
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(if (isThai) "ยกเลิกการบันทึก?" else "Discard changes?") },
            text = {
                Text(
                    if (isThai) "คุณมีข้อมูลที่ยังไม่ได้บันทึก หากออกตอนนี้ข้อมูลจะสูญหาย"
                    else "You have unsaved changes. Are you sure you want to discard them?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) {
                    Text(if (isThai) "ละทิ้งข้อมูล" else "Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(if (isThai) "แก้ไขต่อ" else "Keep editing")
                }
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        memoryDate = localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) {
                    Text(if (isThai) "เลือก" else "Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Record now, or pick a clip already on the phone.
    if (showVideoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showVideoSourceSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (isThai) "เพิ่มวิดีโอความทรงจำ" else "Add a video memory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                VideoSourceRow(
                    icon = Icons.Filled.Videocam,
                    title = if (isThai) "ถ่ายตอนนี้" else "Record now",
                    subtitle = if (isThai) "เปิดกล้อง แล้วแก้ไขต่อในสตูดิโอ"
                    else "Open the camera, then edit in the studio",
                    onClick = {
                        showVideoSourceSheet = false
                        showCamera = true
                    }
                )
                VideoSourceRow(
                    icon = Icons.Filled.VideoLibrary,
                    title = if (isThai) "อัปโหลดคลิป" else "Upload a clip",
                    subtitle = if (isThai) "เลือกจากคลังวิดีโอ แล้วแก้ไขต่อ"
                    else "Choose from your gallery, then edit",
                    onClick = {
                        showVideoSourceSheet = false
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
            }
        }
    }

    if (showCamera) {
        NongRakVideoCaptureScreen(
            isThai = isThai,
            onDismiss = { showCamera = false },
            onCaptured = { uri ->
                showCamera = false
                mediaType = "video"
                ingestUri(uri, asImage = false)
                if (pendingUri != null) showVideoStudio = true
            },
            onPickInstead = {
                showCamera = false
                videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        )
    }

    // Video Studio & Magic FX Dialog
    if (showVideoStudio && pendingUri != null) {

        NongRakVideoStudioDialog(
            videoUri = pendingUri!!,
            title = title.ifBlank { null },
            description = description.ifBlank { null },
            memoryDate = memoryDate,
            isThai = isThai,
            // Lets the caption say "ก้าวแรกของน้องกีก้า" rather than "ก้าวแรกของลูก" - the whole
            // point of calling it personalisation.
            childName = state.child?.name,
            onDismiss = { showVideoStudio = false },
            onReRecord = {
                // Back to the source sheet rather than straight to the camera: "บันทึกใหม่" after
                // an upload should not silently become "record", which is a different decision.
                showVideoStudio = false
                clearAttachment()
                showVideoSourceSheet = true
            },
            onApply = { rendered, edit ->
                // The rendered clip replaces the source, so the memory that gets saved IS the
                // edited video. Previously this only raised a toast and the original was stored.
                pendingUri = rendered
                Toast.makeText(
                    context,
                    if (isThai) "ใส่ '${edit.effect.titleTh}' ลงในวิดีโอแล้ว ✨"
                    else "'${edit.effect.titleEn}' is now part of the video ✨",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}


/** One choice in the video source sheet. */
@Composable
private fun VideoSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(21.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant)
        }
    }
}

@Composable
private fun MediaTypeChoiceCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFDBE1FF) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(if (selected) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) Primary else Primary.copy(0.1f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        null,
                        tint = if (selected) Color.White else Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                label,
                color = Primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}


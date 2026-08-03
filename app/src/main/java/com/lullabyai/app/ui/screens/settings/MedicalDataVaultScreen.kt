@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.MedicalDocument
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChildViewModel

/** Stitch Document Vault categories (chips). */
enum class VaultDocCategory(val key: String, val labelTh: String, val labelEn: String) {
    ALL("all", "ทั้งหมด", "All"),
    IDENTITY("identity", "สูติบัตร (Identity)", "Birth Cert (Identity)"),
    CERTS("certs", "ใบรับรองแพทย์ (Certs)", "Medical Certs"),
    LAB("lab", "ผลตรวจ (Lab)", "Lab Results"),
    OTHER("other", "อื่นๆ (Other)", "Other");

    companion object {
        fun fromKey(key: String): VaultDocCategory =
            entries.firstOrNull { it.key == key } ?: OTHER

        val uploadChoices = listOf(IDENTITY, CERTS, LAB, OTHER)
    }
}

/**
 * Stitch Document Vault — categorized file hub over `medical_documents` / `medical-docs` storage.
 * Upload / browse / download / delete / view. AI-retention UI lives in [AiDataRetentionScreen].
 */
@Composable
fun MedicalDataVaultScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit,
    selectedTab: BottomTab = BottomTab.SETTINGS
) {
    DocumentVaultBody(
        childViewModel = childViewModel,
        navController = navController,
        onBack = onBack,
        selectedTab = selectedTab,
        titleTh = "คลังเอกสาร (Vault)",
        titleEn = "Document Vault"
    )
}

@Composable
internal fun DocumentVaultBody(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit,
    selectedTab: BottomTab,
    titleTh: String,
    titleEn: String
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val canDelete = state.isChildOwner
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VaultDocCategory.ALL) }
    var sortByDate by remember { mutableStateOf(true) }
    var fabOpen by remember { mutableStateOf(false) }
    var pendingUploadCategory by remember { mutableStateOf(VaultDocCategory.OTHER) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var pendingPickerMime by remember { mutableStateOf("*/*") }
    var toDelete by remember { mutableStateOf<MedicalDocument?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.child?.id) { childViewModel.loadMedicalDocuments() }

    fun readAndUpload(uri: Uri, category: VaultDocCategory) {
        runCatching {
            val cr = context.contentResolver
            var fname = "document"
            cr.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) fname = c.getString(idx) ?: fname
            }
            val mime = cr.getType(uri)
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                Toast.makeText(
                    context,
                    if (isThai) "อ่านไฟล์ไม่สำเร็จ" else "Couldn't read file",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            if (bytes.size > 10 * 1024 * 1024) {
                Toast.makeText(
                    context,
                    if (isThai) "ไฟล์ใหญ่เกิน 10MB" else "File exceeds 10MB",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            childViewModel.uploadMedicalDocument(fname, mime, bytes, category.key)
        }.onFailure {
            Toast.makeText(
                context,
                if (isThai) "อัปโหลดไม่สำเร็จ" else "Upload failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readAndUpload(it, pendingUploadCategory) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { readAndUpload(it, pendingUploadCategory) }
    }

    fun launchPendingPicker() {
        when (pendingPickerMime) {
            "image/*" -> photoPicker.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
            else -> filePicker.launch("*/*")
        }
    }

    val filtered = remember(state.medicalDocuments, query, filter, sortByDate) {
        state.medicalDocuments
            .filter { filter == VaultDocCategory.ALL || it.category == filter.key }
            .filter { query.isBlank() || it.fileName.contains(query, ignoreCase = true) }
            .let { list ->
                if (sortByDate) list.sortedByDescending { it.createdAt.orEmpty() }
                else list.sortedBy { it.fileName.lowercase() }
            }
    }
    // Important (identity / verified) featured only on "All"; category filters show a flat group.
    val important = if (filter == VaultDocCategory.ALL) {
        filtered.filter { it.category == "identity" || it.verified }
    } else emptyList()
    val grouped = filtered
        .filterNot { it in important }
        .groupBy { VaultDocCategory.fromKey(it.category) }
        .toSortedMap(compareBy { it.ordinal })

    fun viewDoc(doc: MedicalDocument) {
        busyId = doc.id
        childViewModel.openMedicalDocument(doc.filePath) { url ->
            busyId = null
            if (url == null) {
                Toast.makeText(
                    context,
                    if (isThai) "เปิดไฟล์ไม่สำเร็จ" else "Couldn't open file",
                    Toast.LENGTH_SHORT
                ).show()
                return@openMedicalDocument
            }
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }.onFailure {
                Toast.makeText(
                    context,
                    if (isThai) "ไม่มีแอปเปิดไฟล์นี้" else "No app can open this file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun downloadDoc(doc: MedicalDocument) {
        busyId = doc.id
        childViewModel.openMedicalDocument(doc.filePath) { url ->
            busyId = null
            if (url == null) {
                Toast.makeText(
                    context,
                    if (isThai) "ดาวน์โหลดไม่สำเร็จ" else "Download failed",
                    Toast.LENGTH_SHORT
                ).show()
                return@openMedicalDocument
            }
            runCatching {
                val dm = context.getSystemService(DownloadManager::class.java)
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle(doc.fileName)
                    .setDescription(if (isThai) "คลังเอกสาร NongRak" else "NongRak Document Vault")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "NongRak/${doc.fileName}"
                    )
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                dm.enqueue(req)
                Toast.makeText(
                    context,
                    if (isThai) "กำลังดาวน์โหลด…" else "Downloading…",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    if (isThai) "ดาวน์โหลดไม่สำเร็จ" else "Download failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) titleTh else titleEn,
                onBack = onBack,
                navController = navController
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Explicit Cancel for an in-flight upload — user asked for a real button rather
                // than leaving the FAB spinner with no visible way to abort it.
                if (state.docUploading) {
                    VaultFabAction(
                        label = if (isThai) "ยกเลิกอัปโหลด" else "Cancel upload",
                        icon = Icons.Filled.Close
                    ) { childViewModel.cancelMedicalDocumentUpload() }
                }
                AnimatedVisibility(
                    visible = fabOpen,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VaultFabAction(
                            label = if (isThai) "อุปกรณ์" else "Device",
                            icon = Icons.Filled.UploadFile
                        ) {
                            fabOpen = false
                            pendingPickerMime = "*/*"
                            showCategoryPicker = true
                        }
                        VaultFabAction(
                            label = "Google Drive",
                            icon = Icons.Filled.CloudUpload
                        ) {
                            fabOpen = false
                            Toast.makeText(
                                context,
                                if (isThai) "Google Drive ยังไม่พร้อม — ใช้อัปโหลดจากอุปกรณ์แทน"
                                else "Google Drive coming soon — use Device upload",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        VaultFabAction(
                            label = if (isThai) "รูปภาพ" else "Photo",
                            icon = Icons.Filled.AddAPhoto
                        ) {
                            fabOpen = false
                            pendingPickerMime = "image/*"
                            showCategoryPicker = true
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { fabOpen = !fabOpen },
                    containerColor = Primary,
                    contentColor = Color.White
                ) {
                    if (state.docUploading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            if (fabOpen) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = if (isThai) "อัปโหลด" else "Upload"
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomNav(
                selected = selectedTab,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.MyDoctorsHub.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (isThai) "ค้นหาเอกสาร..." else "Search documents...",
                                color = Outline
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    IconButton(
                        onClick = { sortByDate = !sortByDate },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            if (sortByDate) Icons.Filled.CalendarToday else Icons.Filled.SortByAlpha,
                            contentDescription = if (isThai) "เรียงลำดับ" else "Sort",
                            tint = OnSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VaultDocCategory.entries.forEach { cat ->
                        val selected = filter == cat
                        FilterChip(
                            selected = selected,
                            onClick = { filter = cat },
                            label = {
                                Text(
                                    if (isThai) cat.labelTh else cat.labelEn,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color.Transparent,
                                selectedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.FolderOff,
                                null,
                                tint = Outline.copy(0.35f),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isThai) "ยังไม่มีเอกสาร" else "No documents yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isThai) "อัปโหลดเอกสารเพื่อจัดเก็บไว้ในคลังอย่างปลอดภัย"
                            else "Upload documents to store them safely in the vault",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Outline,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                if (important.isNotEmpty()) {
                    item {
                        Text(
                            if (isThai) "เอกสารสำคัญ (Important)" else "Important",
                            style = MaterialTheme.typography.titleMedium,
                            color = Secondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    items(important, key = { "imp-${it.id}" }) { doc ->
                        VaultDocCard(
                            doc = doc,
                            isThai = isThai,
                            highlight = true,
                            busy = busyId == doc.id,
                            onDownload = { downloadDoc(doc) },
                            onView = { viewDoc(doc) },
                            onDelete = { toDelete = doc },
                            showDelete = canDelete && !doc.verified
                        )
                    }
                }

                grouped.forEach { (cat, docs) ->
                    if (docs.isEmpty()) return@forEach
                    item(key = "hdr-${cat.key}") {
                        Text(
                            if (isThai) cat.labelTh else cat.labelEn,
                            style = MaterialTheme.typography.labelMedium,
                            color = Outline,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    items(docs, key = { it.id }) { doc ->
                        VaultDocCard(
                            doc = doc,
                            isThai = isThai,
                            highlight = false,
                            busy = busyId == doc.id,
                            onDownload = { downloadDoc(doc) },
                            onView = { viewDoc(doc) },
                            onDelete = { toDelete = doc },
                            showDelete = canDelete
                        )
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = {
                Text(
                    if (isThai) "เลือกหมวดเอกสาร" else "Choose category",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VaultDocCategory.uploadChoices.forEach { cat ->
                        val selected = pendingUploadCategory == cat
                        TextButton(onClick = { pendingUploadCategory = cat }) {
                            Icon(
                                if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                null,
                                tint = if (selected) Primary else Outline
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isThai) cat.labelTh else cat.labelEn)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCategoryPicker = false
                        launchPendingPicker()
                    }
                ) {
                    Text(if (isThai) "เลือกไฟล์" else "Pick file", fontWeight = FontWeight.Bold, color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryPicker = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = {
                Text(
                    if (isThai) "ลบเอกสาร?" else "Delete document?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isThai) "ลบ \"${target.fileName}\" อย่างถาวรใช่หรือไม่?"
                    else "Permanently delete \"${target.fileName}\"?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        childViewModel.deleteMedicalDocument(target.id, target.filePath)
                        toDelete = null
                    }
                ) {
                    Text(if (isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun VaultFabAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.4f))
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = SurfaceContainerHighest,
            contentColor = Primary
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun VaultDocCard(
    doc: MedicalDocument,
    isThai: Boolean,
    highlight: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean
) {
    val isPdf = doc.mimeType?.contains("pdf", ignoreCase = true) == true
    val isImage = doc.mimeType?.startsWith("image/") == true
    val (icon, tint, bg) = when {
        doc.category == "identity" || doc.verified -> Triple(Icons.Filled.Badge, Color.White, PrimaryContainer)
        doc.category == "lab" && isImage -> Triple(Icons.Filled.Image, Secondary, SecondaryContainer.copy(0.45f))
        doc.category == "lab" -> Triple(Icons.Filled.Science, Secondary, SecondaryContainer.copy(0.45f))
        isPdf -> Triple(Icons.Filled.Description, Tertiary, TertiaryContainer.copy(0.25f))
        isImage -> Triple(Icons.Filled.Image, Tertiary, TertiaryContainer.copy(0.25f))
        else -> Triple(Icons.Filled.Description, Tertiary, TertiaryContainer.copy(0.25f))
    }
    val meta = buildString {
        if (doc.verified) {
            append(if (isThai) "ยืนยันแล้ว" else "Verified")
            doc.fileSizeBytes?.let { append(" • "); append(formatFileSize(it)) }
        } else {
            append(TimeUtils.localDate(doc.createdAt).orEmpty().ifBlank { "—" })
            doc.fileSizeBytes?.let { append(" • "); append(formatFileSize(it)) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        border = if (highlight) androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.25f)) else null
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Primary)
                } else {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    doc.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (doc.verified) HealthGood else Outline
                )
            }
            IconButton(onClick = onDownload, enabled = !busy) {
                Icon(Icons.Filled.Download, if (isThai) "ดาวน์โหลด" else "Download", tint = Primary)
            }
            if (highlight || doc.verified) {
                IconButton(onClick = onView, enabled = !busy) {
                    Icon(Icons.Filled.Visibility, if (isThai) "ดู" else "View", tint = Primary)
                }
            }
            if (showDelete) {
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Filled.DeleteOutline, if (isThai) "ลบ" else "Delete", tint = OnSurfaceVariant)
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

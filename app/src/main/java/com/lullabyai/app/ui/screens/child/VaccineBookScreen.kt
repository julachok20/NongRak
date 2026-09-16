@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.lullabyai.app.data.VaccineAgeBuckets
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun VaccineBookScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val childState by childViewModel.uiState.collectAsState()
    val vaccines = childState.vaccines
    val canDelete = childState.isChildOwner

    // The seven fixed age buckets the user asked for (#7). Crucially these are a CONSTANT list, not
    // derived from the records: previously a bucket only existed if some record already sat in it,
    // so a parent could not tell "no vaccines this year" from "this year isn't shown" — exactly the
    // problem reported ("I don't know if they had any vaccine of the year").
    val buckets = com.lullabyai.app.data.VaccineAgeBuckets.BUCKETS

    // Prefer the catalogued recommended age; fall back to the row's own age_group label so custom
    // and legacy rows still land somewhere instead of vanishing.
    fun bucketLabelOf(v: VaccineRecord): String {
        com.lullabyai.app.data.VaccineAgeBuckets.ageMonthsOf(v.vaccineName)?.let {
            return com.lullabyai.app.data.VaccineAgeBuckets.bucketForMonths(it).label
        }
        return buckets.firstOrNull { it.label == v.ageGroup }?.label ?: buckets.last().label
    }

    var pageIndex by remember { mutableIntStateOf(0) }
    val currentBucket = buckets[pageIndex.coerceIn(0, buckets.lastIndex)]
    val currentGroup = if (s.isThai) currentBucket.label else currentBucket.labelEn
    val groupVaccines = vaccines
        .filter { bucketLabelOf(it) == currentBucket.label }
        .sortedBy {
            com.lullabyai.app.data.VaccineAgeBuckets.ageMonthsOf(it.vaccineName)
                ?: it.ageOrder ?: Int.MAX_VALUE
        }
    val hasIncompleteAgeData = vaccines.any { it.ageGroup == null }

    // Every dose this child has already had, across ALL age bands — computed once here rather than
    // re-scanned per row. A course (DTP 1-3 + the 18m booster) spans several buckets, so the doses
    // visible in the current band can never answer "is the whole set finished?" (#9).
    val completedDoses = remember(vaccines) {
        vaccines.filter { it.status == "completed" }.map { it.vaccineName }.toSet()
    }

    val total = vaccines.size
    val completed = vaccines.count { it.status == "completed" }
    val pct = if (total > 0) completed * 100 / total else 0

    var showAdd by remember { mutableStateOf(false) }
    var vaccineToDelete by remember { mutableStateOf<VaccineRecord?>(null) }
    var vaccineToEdit by remember { mutableStateOf<VaccineRecord?>(null) }
    var expandedSkipId by remember { mutableStateOf<String?>(null) }   // which row's skip-reason menu is open
    var vaccineInsight by remember { mutableStateOf<String?>(null) }
    // #10: which dose the parent is attaching an ใบรับรองวัคซีน to. Set by the row's upload button,
    // consumed by the picker below, cleared once the upload finishes.
    var certTarget by remember { mutableStateOf<VaccineRecord?>(null) }
    var certUploading by remember { mutableStateOf(false) }
    var certError by remember { mutableStateOf<String?>(null) }
    var viewingDocument by remember { mutableStateOf<com.lullabyai.app.ui.components.DocumentViewSource?>(null) }

    fun viewCertificate(record: VaccineRecord) {
        val path = record.certificateUrl ?: return
        childViewModel.openVaccineCertificate(path) { url ->
            if (url.isNullOrBlank()) {
                certError = if (s.isThai) "เปิดใบรับรองไม่สำเร็จ" else "Could not open certificate"
                return@openVaccineCertificate
            }
            viewingDocument = com.lullabyai.app.ui.components.DocumentViewSource.Remote(
                url = url,
                displayTitle = if (s.isThai) "ใบรับรองวัคซีน - ${record.vaccineName}" else "Vaccine Certificate - ${record.vaccineName}",
                mime = "application/pdf"
            )
        }
    }

    val certPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        val target = certTarget
        certTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: ""
        // Photos get downscaled like every other image upload; PDFs go through untouched.
        val bytes = if (mime.startsWith("image/")) com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri)
        else com.lullabyai.app.util.ImageUtils.readBytes(context, uri)
        if (bytes == null) {
            certError = if (s.isThai) "อ่านไฟล์ไม่สำเร็จ" else "Could not read file"
            return@rememberLauncherForActivityResult
        }
        if (bytes.size > 10 * 1024 * 1024) {
            certError = if (s.isThai) "ไฟล์ต้องไม่เกิน 10MB" else "File must be ≤ 10MB"
            return@rememberLauncherForActivityResult
        }
        certError = null
        certUploading = true
        childViewModel.uploadVaccineCertificate(
            target,
            bytes,
            if (mime.startsWith("image/")) "jpg" else "pdf"
        ) { ok ->
            certUploading = false
            if (!ok) certError = if (s.isThai) "อัปโหลดไม่สำเร็จ" else "Upload failed"
        }
    }
    LaunchedEffect(certTarget) { if (certTarget != null) certPicker.launch("*/*") }

    // Seed the full Thai 0–3y schedule if this child has no vaccines yet
    LaunchedEffect(childState.child?.id) {
        if (childState.child != null) childViewModel.ensureVaccineSchedule()
    }

    // AI Health Insight (generated once per visit)
    LaunchedEffect(childState.child?.id) {
        val c = childState.child
        if (c != null && vaccineInsight == null) {
            val prompt = if (s.isThai)
                "ให้คำแนะนำสั้นๆ 2-3 ประโยคเกี่ยวกับวัคซีนสำหรับเด็กที่ได้รับวัคซีนไปแล้ว $pct% ของช่วงวัย 0-3 ปี เน้นความสำคัญของการฉีดตรงตามกำหนด"
            else
                "Give a short 2-3 sentence insight about vaccines for a child who has completed $pct% of their 0-3 year vaccine schedule. Emphasize staying on schedule."
            vaccineInsight = ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 800)
        }
    }

    ChildScreenScaffold(title = if (s.isThai) "สมุดวัคซีน" else "Vaccine Book", currentTab = "vaccine", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Title + age chip
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (s.isThai) "สมุดวัคซีน" else "Vaccine Book", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (s.isThai) "ติดตามความคืบหน้าการฉีดวัคซีนของลูกน้อย" else "Track your child's vaccination progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (currentGroup.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                            Text(currentGroup, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Overall progress — text + ring + on-schedule badge (per design: neutral card with a
            // border, not the alarming red tint the previous version used). Badge derived from real
            // vaccine data (any overdue dose flips it), not invented copy.
            item {
                val hasOverdue = vaccines.any { it.status == "overdue" }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.5f)),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (s.isThai) "ความคืบหน้าโดยรวม" else "Overall progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    if (s.isThai) "${childState.child?.name ?: "ลูกน้อย"} ได้รับวัคซีนแล้ว $pct% ของช่วงวัย 0-3 ปี"
                                    else "${childState.child?.name ?: "Your child"} has completed $pct% of the 0-3y vaccine schedule",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                                CircularProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                Text("$pct%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = if (hasOverdue) Error.copy(0.1f) else MaterialTheme.colorScheme.primary.copy(0.1f)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(if (hasOverdue) Icons.Filled.WarningAmber else Icons.Filled.AutoAwesome, null, tint = if (hasOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    if (hasOverdue) (if (s.isThai) "มีรายการเลยกำหนด" else "Behind schedule")
                                    else (if (s.isThai) "เป็นไปตามกำหนด" else "On schedule"),
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (hasOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Legacy rows with no age_group land silently under "อื่นๆ" (Other) — flag it so a
            // parent doesn't mistake an import/data gap for the child simply having no vaccines due.
            if (hasIncompleteAgeData) {
                item {
                    // Dark: pastel box becomes an orange wash with lighter orange text.
                    val warnDark = com.lullabyai.app.ui.theme.LocalDarkTheme.current
                    val warnInk = if (warnDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                    Surface(shape = RoundedCornerShape(12.dp), color = if (warnDark) Color(0x29E65100) else Color(0xFFFFF3E0)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.WarningAmber, null, tint = warnInk, modifier = Modifier.size(18.dp))
                            Text(
                                if (s.isThai) "บางรายการวัคซีนไม่มีข้อมูลช่วงอายุ — อาจต้องแก้ไข" else "Some vaccines are missing age-range data — may need updating",
                                style = MaterialTheme.typography.labelMedium, color = warnInk
                            )
                        }
                    }
                }
            }

            // #10 entry to the สมุดวัคซีน scanner — reads pages with AI and pre-ticks the doses.
            item {
                OutlinedButton(
                    onClick = { navController.navigate(VACCINE_BOOK_SCAN_ROUTE) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.isThai) "อ่านจากสมุดวัคซีน (ถ่ายรูป)" else "Scan vaccine book",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                    )
                }
            }

            // Age-band selector — a dropdown over the seven fixed buckets (#7), so every band is
            // always listed whether or not it currently holds any dose.
            item {
                Text(if (s.isThai) "เลือกช่วงอายุ" else "Select age range", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                "${pageIndex + 1}. $currentGroup",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            // Doses recorded in this band, so the parent sees coverage before opening.
                            Text(
                                if (s.isThai) "${groupVaccines.size} รายการ" else "${groupVaccines.size} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = Outline
                            )
                            Icon(Icons.Filled.ArrowDropDown, null, tint = Outline)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        buckets.forEachIndexed { i, b ->
                            val count = vaccines.count { bucketLabelOf(it) == b.label }
                            val done = vaccines.count { bucketLabelOf(it) == b.label && it.status == "completed" }
                            DropdownMenuItem(
                                onClick = { pageIndex = i; expanded = false },
                                text = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            "${i + 1}. " + (if (s.isThai) b.label else b.labelEn),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (i == pageIndex) FontWeight.Bold else FontWeight.Normal,
                                            color = if (i == pageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            if (count == 0) (if (s.isThai) "ยังไม่มี" else "none") else "$done/$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (count == 0) Outline else if (done == count) HealthGood else HealthAttention
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Section header
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        (if (s.isThai) "ตารางวัคซีนช่วง " else "Vaccine schedule — ") + currentGroup,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // Stitch shows an upcoming-count chip beside this header ("3 รายการถัดไป").
                    val upcoming = groupVaccines.count { it.status != "completed" && it.status != "skipped" }
                    if (upcoming > 0) {
                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.primary.copy(0.10f)) {
                            Text(
                                if (s.isThai) "$upcoming รายการถัดไป" else "$upcoming upcoming",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Explicit empty state — answers "did they have any vaccine of this year?" with a real
            // answer instead of the band simply not existing (#7).
            if (groupVaccines.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.EventAvailable, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(
                            if (s.isThai) "ยังไม่มีวัคซีนในช่วง \"$currentGroup\""
                            else "No vaccines recorded for \"$currentGroup\"",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (s.isThai) "ตามตารางไทยอาจไม่มีวัคซีนในช่วงนี้ หรือยังไม่ได้บันทึก"
                            else "The Thai schedule may have none for this band, or it isn't recorded yet.",
                            style = MaterialTheme.typography.labelSmall, color = Outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Vaccine cards for current age group
            items(groupVaccines) { v ->
                VaccineRow(
                    vaccine = v,
                    course = VaccineAgeBuckets.courseStatus(v.vaccineName, completedDoses),
                    skipExpanded = expandedSkipId == v.id,
                    onToggle = { childViewModel.toggleVaccine(v); expandedSkipId = null },
                    onEdit = { vaccineToEdit = v },
                    onOpenSkip = { expandedSkipId = if (expandedSkipId == v.id) null else v.id },
                    onConfirmSkip = { reason ->
                        childViewModel.skipVaccine(v, reason)
                        expandedSkipId = null
                    },
                    onUploadCertificate = { certTarget = v },
                    onViewCertificate = { viewCertificate(v) },
                    onAddToCalendar = {
                        v.scheduledDate?.let { date ->
                            val ok = com.lullabyai.app.util.CalendarHelper.addVaccineEvent(context, v.vaccineName, childState.child?.name ?: "", date, v.place)
                            if (!ok) {
                                android.widget.Toast.makeText(
                                    context,
                                    if (s.isThai) "ไม่พบแอปปฏิทินในเครื่อง" else "No calendar app found on this device",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            // Add custom vaccine
            item {
                OutlinedButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.isThai) "เพิ่มวัคซีนเพิ่มเติม / นัดหมาย" else "Add vaccine / appointment")
                }
            }

            // AI Health Insight (per design)
            item {
                AIInsightCard(text = vaccineInsight ?: "", isLoading = vaccineInsight == null)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    com.lullabyai.app.ui.components.InAppDocumentViewerDialog(
        source = viewingDocument,
        onDismiss = { viewingDocument = null },
        isThai = s.isThai
    )

    if (showAdd) {
        AddVaccineDialog(
            defaultGroup = currentGroup.ifEmpty { "แรกเกิด" },
            onDismiss = { showAdd = false },
            onAdd = { name, group, date, desc, place ->
                childViewModel.addVaccine(name, group, date.ifBlank { null }, desc.ifBlank { null }, place.ifBlank { null })
                showAdd = false
            }
        )
    }

    // Edit / reschedule dialog
    vaccineToEdit?.let { target ->
        EditVaccineDialog(
            vaccine = target,
            canDelete = canDelete,
            onDismiss = { vaccineToEdit = null },
            onSave = { name, date, desc, place ->
                childViewModel.updateVaccineSchedule(target, name, date.ifBlank { null }, desc.ifBlank { null }, place.ifBlank { null })
                vaccineToEdit = null
            },
            onDelete = { vaccineToDelete = target; vaccineToEdit = null }
        )
    }

    // Delete confirmation — "Are you sure?"
    vaccineToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { vaccineToDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (s.isThai) "ลบรายการวัคซีน?" else "Delete vaccine?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "คุณแน่ใจหรือไม่ว่าต้องการลบ \"${target.vaccineName}\" ออกจากสมุดวัคซีน? การลบนี้ไม่สามารถย้อนกลับได้" else "Remove \"${target.vaccineName}\" from the vaccine book? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    childViewModel.deleteVaccine(target)
                    vaccineToDelete = null
                }) { Text(if (s.isThai) "ลบ" else "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { vaccineToDelete = null }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") }
            }
        )
    }
}

// Standard Thai NIP age groups, in order — the user picks one so the dose lands in the right category.
private val VACCINE_AGE_GROUPS = listOf(
    "แรกเกิด", "1 เดือน", "2 เดือน", "4 เดือน", "6 เดือน", "9 เดือน",
    "1 ปี", "1 ปี 6 เดือน", "2 ปี", "2 ปี 6 เดือน", "3 ปี", "อื่นๆ"
)

@Composable
private fun AddVaccineDialog(defaultGroup: String, onDismiss: () -> Unit, onAdd: (String, String, String, String, String) -> Unit) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(if (defaultGroup in VACCINE_AGE_GROUPS) defaultGroup else "แรกเกิด") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.isThai) "เพิ่มวัคซีน" else "Add vaccine", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (s.isThai) "ชื่อวัคซีน" else "Vaccine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Age-group picker — places the vaccine in the correct category (sorted by age).
                Text(if (s.isThai) "ช่วงอายุ" else "Age group", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VACCINE_AGE_GROUPS.forEach { g ->
                        val sel = g == group
                        Surface(onClick = { group = g }, shape = RoundedCornerShape(100.dp), color = if (sel) Primary else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(g, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text(if (s.isThai) "วันที่นัด (YYYY-MM-DD)" else "Appointment date (YYYY-MM-DD)") }, placeholder = { Text(if (s.isThai) "เช่น 2026-08-01" else "e.g. 2026-08-01") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text(if (s.isThai) "สถานพยาบาล / สถานที่ฉีด" else "Hospital / place") }, placeholder = { Text(if (s.isThai) "เช่น รพ.ศิริราช, คลินิกใกล้บ้าน" else "e.g. Siriraj Hospital") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.LocalHospital, null, tint = Outline) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(if (s.isThai) "คำอธิบาย (ถ้ามี)" else "Description (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim(), group, date.trim(), desc.trim(), place.trim()) }) { Text(if (s.isThai) "เพิ่ม" else "Add", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
    )
}

@Composable
private fun EditVaccineDialog(
    vaccine: VaccineRecord,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(vaccine.vaccineName) }
    var date by remember { mutableStateOf(vaccine.scheduledDate ?: "") }
    var desc by remember { mutableStateOf(vaccine.description ?: "") }
    var place by remember { mutableStateOf(vaccine.place ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EditCalendar, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (s.isThai) "แก้ไขกำหนดการวัคซีน" else "Edit vaccine schedule", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (s.isThai) "ชื่อวัคซีน" else "Vaccine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text(if (s.isThai) "วันที่นัด (YYYY-MM-DD)" else "Appointment date (YYYY-MM-DD)") }, placeholder = { Text(if (s.isThai) "เช่น 2026-08-01" else "e.g. 2026-08-01") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text(if (s.isThai) "สถานพยาบาล / สถานที่ฉีด" else "Hospital / place") }, placeholder = { Text(if (s.isThai) "เช่น รพ.ศิริราช, คลินิกใกล้บ้าน" else "e.g. Siriraj Hospital") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.LocalHospital, null, tint = Outline) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(if (s.isThai) "คำอธิบาย (ถ้ามี)" else "Description (optional)") }, modifier = Modifier.fillMaxWidth())
                Text((if (s.isThai) "ช่วงอายุ: " else "Age: ") + (vaccine.ageGroup ?: "-"), style = MaterialTheme.typography.labelSmall, color = Outline)
                if (canDelete) {
                    TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (s.isThai) "ลบรายการนี้ออกจากสมุด" else "Remove from book", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), date.trim(), desc.trim(), place.trim()) }) { Text(if (s.isThai) "บันทึก" else "Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
    )
}

// Reasons a parent might skip a dose — mirrors the พัฒนาการ skip flow.
private fun vaccineSkipReasons(isThai: Boolean) =
    if (isThai) listOf("ลูกป่วย/ไม่สบาย", "แพ้วัคซีน", "เลื่อนนัด", "ผู้ปกครองปฏิเสธ", "ได้รับที่อื่นแล้ว", "อื่นๆ")
    else listOf("Child was ill", "Vaccine allergy", "Rescheduled", "Parent declined", "Given elsewhere", "Other")

@Composable
private fun VaccineRow(
    vaccine: VaccineRecord,
    course: VaccineAgeBuckets.CourseStatus,
    skipExpanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenSkip: () -> Unit,
    onConfirmSkip: (String) -> Unit,
    onAddToCalendar: () -> Unit,
    onUploadCertificate: () -> Unit = {},
    onViewCertificate: () -> Unit = {}
) {
    val s = LocalStrings.current
    val (badgeColor, badgeText) = when (vaccine.status) {
        "completed" -> Color(0xFF4CAF50) to (if (s.isThai) "เสร็จสิ้น" else "Done")
        "appointment" -> MaterialTheme.colorScheme.primary to (if (s.isThai) "แนะนำให้ฉีด" else "Recommended")
        "overdue" -> MaterialTheme.colorScheme.error to (if (s.isThai) "เลยกำหนด" else "Overdue")
        "upcoming" -> Color(0xFFF57C00) to (if (s.isThai) "เร็วๆ นี้" else "Upcoming")
        "skipped" -> Color(0xFF9E9E9E) to (if (s.isThai) "ข้ามแล้ว" else "Skipped")
        else -> MaterialTheme.colorScheme.outline to (if (s.isThai) "รอกำหนด" else "Scheduled")
    }
    val done = vaccine.status == "completed"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top: icon + info + status badge. Badge is top-aligned in its own slot so it can never
            // overlap the action buttons (which now live on a separate row below).
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Vaccines, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(vaccine.vaccineName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    // The dose's real recommended age, always shown — the seven buckets are coarser
                    // than the NIP schedule, so this keeps the true age visible (e.g. MMR-1 is a
                    // 9-month dose even though it sits in the "1 ปี" band).
                    com.lullabyai.app.data.VaccineAgeBuckets.ageMonthsOf(vaccine.vaccineName)?.let { m ->
                        Text(
                            (if (s.isThai) "กำหนดฉีดเมื่อ " else "Due at ") +
                                com.lullabyai.app.data.VaccineAgeBuckets.ageLabel(m, s.isThai),
                            style = MaterialTheme.typography.labelSmall, color = Outline
                        )
                    }
                    vaccine.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val date = if (done) vaccine.dateGiven else vaccine.scheduledDate
                    date?.takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.CalendarToday, it) }
                    vaccine.place?.takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.LocalHospital, it) }
                    // #10 provenance + certificate state.
                    if (vaccine.source == "moph_book") {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(0.10f)) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Text(
                                    if (s.isThai) "จากสมุดวัคซีนกระทรวงสาธารณสุข" else "From ministry vaccine book",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (!vaccine.certificateUrl.isNullOrBlank()) {
                        TextButton(
                            onClick = onViewCertificate,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (s.isThai) "ดูใบรับรอง" else "View certificate",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // #10: completed dose with no certificate yet gets a small upload affordance.
                    if (done && vaccine.certificateUrl.isNullOrBlank()) {
                        TextButton(
                            onClick = onUploadCertificate,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.UploadFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (s.isThai) "อัปโหลดใบรับรองวัคซีน" else "Upload certificate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (vaccine.status == "skipped" && !vaccine.notes.isNullOrBlank()) {
                        Text((if (s.isThai) "ข้าม: " else "Skipped: ") + vaccine.notes, style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57C00))
                    }
                    // Multi-dose course state (#7, fixed by #9). Only shown once the dose is
                    // actually done, since that's when the next visit is due. Driven by the whole
                    // series against the child's real record, so a finished course says
                    // "ครบชุดแล้ว" instead of nagging for a dose already given.
                    if (done) {
                        when (course) {
                            is VaccineAgeBuckets.CourseStatus.NotASeries -> Unit
                            is VaccineAgeBuckets.CourseStatus.Complete -> CourseBadge(
                                // Same green as the "เสร็จสิ้น" badge on this row, deliberately.
                                color = Color(0xFF4CAF50),
                                icon = Icons.Filled.CheckCircle,
                                text = if (s.isThai) "ครบชุดแล้ว" else "Course complete"
                            )
                            is VaccineAgeBuckets.CourseStatus.Outstanding -> CourseBadge(
                                color = Color(0xFFF57C00),
                                icon = Icons.Filled.EventRepeat,
                                // monthsAway == 0 means the outstanding dose isn't later than this
                                // one (out-of-order record) — name it without a bogus "0 months".
                                text = if (course.monthsAway > 0) {
                                    if (s.isThai) "ยังไม่ครบชุด • เข็มถัดไปอีกประมาณ ${course.monthsAway} เดือน (${course.dose})"
                                    else "Course not finished • next dose in about ${course.monthsAway} months (${course.dose})"
                                } else {
                                    if (s.isThai) "ยังไม่ครบชุด • ยังขาด ${course.dose}"
                                    else "Course not finished • still missing ${course.dose}"
                                }
                            )
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.12f)) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                }
            }

            // Separate, clearly-labeled row (not a bare icon) — only for doses that still have a
            // date worth putting on a calendar.
            if (!done && vaccine.status != "skipped" && !vaccine.scheduledDate.isNullOrBlank()) {
                OutlinedButton(onClick = onAddToCalendar, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    Icon(Icons.Filled.EventAvailable, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (s.isThai) "เพิ่มลงปฏิทิน" else "Add to calendar", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Action buttons — own row, evenly spaced, so they never collide with the text/badge.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    Icon(Icons.Filled.EditCalendar, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (s.isThai) "แก้ไข" else "Edit", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick = onOpenSkip, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    Icon(Icons.Filled.Block, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (s.isThai) "ข้าม" else "Skip", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onToggle, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (done) Color(0xFF4CAF50) else Primary)
                ) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp), tint = Color.White); Spacer(Modifier.width(4.dp))
                    Text(if (done) (if (s.isThai) "ฉีดแล้ว" else "Done") else (if (s.isThai) "ฉีด" else "Mark"), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }

            // Inline skip-reason menu (mirrors the พัฒนาการ skip flow).
            if (skipExpanded) {
                var selectedReason by remember { mutableStateOf(vaccine.notes ?: "") }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (s.isThai) "ระบุเหตุผลที่ข้าม:" else "Reason for skipping:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    vaccineSkipReasons(s.isThai).forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { selectedReason = reason }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = { onConfirmSkip(selectedReason) }, enabled = selectedReason.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
                        Text(if (s.isThai) "ยืนยันการข้าม" else "Confirm skip", color = Color(0xFFF57C00), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** The one pill used for both course states — same shape, colour is the only difference. */
@Composable
private fun CourseBadge(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.10f)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Outline, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Outline)
    }
}

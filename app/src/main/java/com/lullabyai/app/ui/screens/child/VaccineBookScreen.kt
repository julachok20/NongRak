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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    val childState by childViewModel.uiState.collectAsState()
    val vaccines = childState.vaccines

    // Distinct age groups in schedule order. age_group/age_order may be null on legacy rows —
    // coalesce so those rows still appear (under "อื่นๆ") instead of breaking grouping.
    val fallbackGroup = if (s.isThai) "อื่นๆ" else "Other"
    val groups = remember(vaccines, fallbackGroup) {
        vaccines.sortedBy { it.ageOrder ?: Int.MAX_VALUE }
            .map { it.ageGroup ?: fallbackGroup }.distinct()
    }
    var pageIndex by remember(groups) { mutableStateOf(0) }
    val currentGroup = groups.getOrNull(pageIndex) ?: ""
    val groupVaccines = vaccines
        .filter { (it.ageGroup ?: fallbackGroup) == currentGroup }
        .sortedBy { it.ageOrder ?: Int.MAX_VALUE }
    val hasIncompleteAgeData = vaccines.any { it.ageGroup == null }

    val total = vaccines.size
    val completed = vaccines.count { it.status == "completed" }
    val pct = if (total > 0) completed * 100 / total else 0

    var showAdd by remember { mutableStateOf(false) }
    var vaccineToDelete by remember { mutableStateOf<VaccineRecord?>(null) }
    var vaccineToEdit by remember { mutableStateOf<VaccineRecord?>(null) }
    var expandedSkipId by remember { mutableStateOf<String?>(null) }   // which row's skip-reason menu is open
    var vaccineInsight by remember { mutableStateOf<String?>(null) }

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
                            Text(currentGroup, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Overall progress — text + ring + certificate button (per design)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Error.copy(0.06f))
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
                                CircularProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp, color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                Text("$pct%", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }
}
                }
            }

            // Legacy rows with no age_group land silently under "อื่นๆ" (Other) — flag it so a
            // parent doesn't mistake an import/data gap for the child simply having no vaccines due.
            if (hasIncompleteAgeData) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3E0)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                            Text(
                                if (s.isThai) "บางรายการวัคซีนไม่มีข้อมูลช่วงอายุ — อาจต้องแก้ไข" else "Some vaccines are missing age-range data — may need updating",
                                style = MaterialTheme.typography.labelMedium, color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            // Age-band chips (per design) — replaces prev/next
            item {
                Text(if (s.isThai) "เลือกช่วงอายุ" else "Select age range", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEachIndexed { i, g ->
                        val sel = i == pageIndex
                        Surface(onClick = { pageIndex = i }, shape = RoundedCornerShape(100.dp), color = if (sel) Primary else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(g, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section header
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text((if (s.isThai) "ตารางวัคซีนช่วง " else "Vaccine schedule — ") + currentGroup, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Vaccine cards for current age group
            items(groupVaccines) { v ->
                VaccineRow(
                    vaccine = v,
                    skipExpanded = expandedSkipId == v.id,
                    onToggle = { childViewModel.toggleVaccine(v); expandedSkipId = null },
                    onEdit = { vaccineToEdit = v },
                    onOpenSkip = { expandedSkipId = if (expandedSkipId == v.id) null else v.id },
                    onConfirmSkip = { reason ->
                        childViewModel.skipVaccine(v, reason)
                        expandedSkipId = null
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
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Color(0xFFBA1A1A)) },
            title = { Text(if (s.isThai) "ลบรายการวัคซีน?" else "Delete vaccine?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "คุณแน่ใจหรือไม่ว่าต้องการลบ \"${target.vaccineName}\" ออกจากสมุดวัคซีน? การลบนี้ไม่สามารถย้อนกลับได้" else "Remove \"${target.vaccineName}\" from the vaccine book? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    childViewModel.deleteVaccine(target)
                    vaccineToDelete = null
                }) { Text(if (s.isThai) "ลบ" else "Delete", color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold) }
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
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim(), group, date.trim(), desc.trim(), place.trim()) }) { Text(if (s.isThai) "เพิ่ม" else "Add", color = Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
    )
}

@Composable
private fun EditVaccineDialog(
    vaccine: VaccineRecord,
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
        icon = { Icon(Icons.Filled.EditCalendar, null, tint = Primary) },
        title = { Text(if (s.isThai) "แก้ไขกำหนดการวัคซีน" else "Edit vaccine schedule", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (s.isThai) "ชื่อวัคซีน" else "Vaccine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text(if (s.isThai) "วันที่นัด (YYYY-MM-DD)" else "Appointment date (YYYY-MM-DD)") }, placeholder = { Text(if (s.isThai) "เช่น 2026-08-01" else "e.g. 2026-08-01") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text(if (s.isThai) "สถานพยาบาล / สถานที่ฉีด" else "Hospital / place") }, placeholder = { Text(if (s.isThai) "เช่น รพ.ศิริราช, คลินิกใกล้บ้าน" else "e.g. Siriraj Hospital") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.LocalHospital, null, tint = Outline) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(if (s.isThai) "คำอธิบาย (ถ้ามี)" else "Description (optional)") }, modifier = Modifier.fillMaxWidth())
                Text((if (s.isThai) "ช่วงอายุ: " else "Age: ") + (vaccine.ageGroup ?: "-"), style = MaterialTheme.typography.labelSmall, color = Outline)
                TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = Color(0xFFBA1A1A), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (s.isThai) "ลบรายการนี้ออกจากสมุด" else "Remove from book", color = Color(0xFFBA1A1A), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), date.trim(), desc.trim(), place.trim()) }) { Text(if (s.isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold) }
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
    skipExpanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenSkip: () -> Unit,
    onConfirmSkip: (String) -> Unit
) {
    val s = LocalStrings.current
    val (badgeColor, badgeText) = when (vaccine.status) {
        "completed" -> Color(0xFF4CAF50) to (if (s.isThai) "เสร็จสิ้น" else "Done")
        "appointment" -> Primary to (if (s.isThai) "แนะนำให้ฉีด" else "Recommended")
        "overdue" -> Color(0xFFBA1A1A) to (if (s.isThai) "เลยกำหนด" else "Overdue")
        "upcoming" -> Color(0xFFF57C00) to (if (s.isThai) "เร็วๆ นี้" else "Upcoming")
        "skipped" -> Color(0xFF9E9E9E) to (if (s.isThai) "ข้ามแล้ว" else "Skipped")
        else -> Outline to (if (s.isThai) "รอกำหนด" else "Scheduled")
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
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Vaccines, null, tint = Primary, modifier = Modifier.size(22.dp)) }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(vaccine.vaccineName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    vaccine.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val date = if (done) vaccine.dateGiven else vaccine.scheduledDate
                    date?.takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.CalendarToday, it) }
                    vaccine.place?.takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.LocalHospital, it) }
                    if (vaccine.status == "skipped" && !vaccine.notes.isNullOrBlank()) {
                        Text((if (s.isThai) "ข้าม: " else "Skipped: ") + vaccine.notes, style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57C00))
                    }
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.12f)) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
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
                    Text(if (s.isThai) "ระบุเหตุผลที่ข้าม:" else "Reason for skipping:", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                    vaccineSkipReasons(s.isThai).forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { selectedReason = reason }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason }, colors = RadioButtonDefaults.colors(selectedColor = Primary))
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

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Outline, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Outline)
    }
}

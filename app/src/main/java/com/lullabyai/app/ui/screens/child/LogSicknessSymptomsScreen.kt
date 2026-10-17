@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.SectionCard
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.HealthRecordRules
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val SYMPTOM_KEYS = listOf("fever", "cough", "runny_nose", "rash", "lethargic", "vomiting", "diarrhea")
private fun symptomLabel(key: String, isThai: Boolean) = when (key) {
    "fever" -> if (isThai) "ตัวร้อน" else "Fever"
    "cough" -> if (isThai) "ไอ" else "Cough"
    "runny_nose" -> if (isThai) "มีน้ำมูก" else "Runny nose"
    "rash" -> if (isThai) "ผื่น" else "Rash"
    "lethargic" -> if (isThai) "ซึม" else "Lethargic"
    "vomiting" -> if (isThai) "อาเจียน" else "Vomiting"
    "diarrhea" -> if (isThai) "ท้องเสีย" else "Diarrhea"
    // user-typed symptom keys ("sore_throat") — prettify instead of mislabelling as diarrhea
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
private val SEVERITIES = listOf(
    "none" to ("ไม่มี" to "None"),
    "mild" to ("เล็กน้อย" to "Mild"),
    "moderate" to ("ปานกลาง" to "Moderate"),
    "severe" to ("รุนแรง" to "Severe")
)
/** Per-symptom pain level. `symptoms[key]` stays a severity string (no schema change);
 *  the row's `pain_level` is the max across symptoms, as documented on SymptomLog.painLevel. */
private val SEVERITY_PAIN = mapOf("none" to 0, "mild" to 3, "moderate" to 6, "severe" to 9)
private val FEELINGS = listOf(
    "cold" to ("หนาว" to "Cold"),
    "normal" to ("ปกติ" to "Normal"),
    "hot" to ("ตัวร้อน" to "Hot")
)
private val DAY_STATUSES = listOf(
    "started" to ("เริ่มมีอาการ" to "Just started"),
    "active" to ("กำลังมีอาการ" to "Ongoing"),
    "recovered" to ("หายแล้ว" to "Recovered")
)

/** "บันทึกอาการวันนี้" — Stitch daily_symptom_record: multi-day strip, feelings, pain slider,
 *  photo, editable current-day log (F3 10-min lock from last symptom write). */
@Composable
fun LogSicknessSymptomsScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val record = state.healthRecords.firstOrNull { it.id == state.openHealthRecordId }

    LaunchedEffect(record?.id) { record?.let { childViewModel.loadSymptomLogs(it.id) } }

    val startDate = remember(record?.id, record?.recordedAt, record?.createdAt) {
        val iso = record?.recordedAt ?: record?.createdAt
        com.lullabyai.app.util.TimeUtils.localDate(iso)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
    }
    val today = remember { LocalDate.now() }
    val dayOptions = remember(startDate, today) {
        val end = if (today.isBefore(startDate)) startDate else today
        val days = ChronoDaysBetween(startDate, end).coerceAtMost(14)
        (0..days).map { startDate.plusDays(it.toLong()) }
    }

    var selectedDate by remember(record?.id) { mutableStateOf(today.coerceAtLeast(startDate)) }

    // Many submissions per day: a day holds a LIST of entries. editingId == null means the form is
    // a brand-new entry (INSERT); non-null edits that specific row (UPDATE, subject to the 10-min lock).
    val dayLogs = remember(state.symptomLogs, selectedDate) {
        state.symptomLogs.filter { it.logDate == selectedDate.toString() }
            .sortedBy { it.createdAt ?: "" }
    }
    var editingId by remember(record?.id, selectedDate) { mutableStateOf<String?>(null) }
    // bumped after a save so the form clears even when editingId is already null
    var formNonce by remember(record?.id) { mutableIntStateOf(0) }
    val existingLog = dayLogs.firstOrNull { it.id == editingId }
    val formKeys = arrayOf<Any?>(editingId, selectedDate, formNonce)

    var severities by remember(*formKeys) { mutableStateOf(existingLog?.symptoms ?: emptyMap()) }
    // stored temperature is canonical Celsius; show it back in the unit it was entered in
    var tempInC by remember(*formKeys) { mutableStateOf(existingLog?.temperatureUnit != "F") }
    var tempText by remember(*formKeys) {
        mutableStateOf(
            existingLog?.temperature?.let { c ->
                val shown = if (existingLog.temperatureUnit == "F") c * 9 / 5 + 32 else c
                String.format(Locale.US, "%.1f", shown)
            } ?: ""
        )
    }
    // Feeling is the main way to record temperature; the number field is opt-in.
    var useFeeling by remember(*formKeys) { mutableStateOf(existingLog?.let { it.temperature == null } ?: true) }
    var feeling by remember(*formKeys) { mutableStateOf(existingLog?.feeling) }
    var dayStatus by remember(*formKeys) { mutableStateOf(existingLog?.dayStatus ?: "active") }
    var notes by remember(*formKeys) { mutableStateOf(existingLog?.notes ?: "") }
    var photoPath by remember(*formKeys) { mutableStateOf(existingLog?.photoUrl) }
    var customSymptom by remember { mutableStateOf("") }

    // Every symptom carries its own level; the row's pain_level is the worst of them.
    val symptomKeys = remember(severities) { SYMPTOM_KEYS + severities.keys.filter { it !in SYMPTOM_KEYS } }
    val derivedPain = remember(severities) {
        severities.values.mapNotNull { SEVERITY_PAIN[it] }.maxOrNull() ?: 0
    }

    val enteredTemp = tempText.toDoubleOrNull()
    val tempC = if (useFeeling || enteredTemp == null) null
                else if (tempInC) enteredTemp else (enteredTemp - 32) * 5 / 9
    val tempOutOfRange = tempC != null && (tempC < 35.0 || tempC > 43.0)

    val dayNumber = ChronoDaysBetween(startDate, selectedDate) + 1
    // F3: only an *existing* entry locks after 10 min. A new entry is always writable, which is
    // what lets a parent keep recording later the same day.
    val editBlocked = existingLog?.let { HealthRecordRules.isSymptomLogEditLocked(it) } == true

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            if (editBlocked) return@let
            val bytes = runCatching { context.contentResolver.openInputStream(it)?.use { st -> st.readBytes() } }.getOrNull()
            if (bytes != null) childViewModel.uploadSymptomLogPhoto(bytes) { path -> photoPath = path }
        }
    }

    LaunchedEffect(existingLog?.photoUrl) {
        existingLog?.photoUrl?.let {
            // signed URL already refreshed on save; keep local preview path
        }
    }

    // One save path for both the top-bar action and the bottom button. onDone is ALWAYS named —
    // saveSymptomLog takes temperatureUnit before onDone, so a positional lambda is a trap.
    fun submit() {
        val rec = record ?: return
        childViewModel.saveSymptomLog(
            healthRecordId = rec.id,
            logDate = selectedDate.toString(),
            symptoms = severities.filterValues { it != "none" },
            temperature = tempC,
            feeling = feeling,
            dayStatus = dayStatus,
            notes = notes.ifBlank { null },
            painLevel = derivedPain,
            photoUrl = photoPath,
            existingId = editingId,
            temperatureUnit = if (tempInC) "C" else "F",
            onDone = {
                if (dayStatus == "recovered") {
                    childViewModel.toggleHealthRecordStatus(rec.id)
                    onBack()
                } else {
                    // stay on screen with a blank form so the parent can log again today
                    editingId = null
                    formNonce++
                }
            }
        )
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "บันทึกอาการป่วย" else "Log Symptoms",
                onBack = onBack,
                actions = {
                    TextButton(
                        enabled = record != null && !state.symptomLogSaving && !editBlocked && !tempOutOfRange,
                        onClick = { submit() }
                    ) {
                        if (state.symptomLogSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        else Text(
                            if (existingLog != null) (if (s.isThai) "อัปเดต" else "Update")
                            else (if (s.isThai) "บันทึก" else "Save"),
                            color = if (editBlocked) Outline else Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            record?.let {
                Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // No open illness record means there is nothing to attach a daily symptom to, so
            // saveSymptomLog returns early and both Save buttons are disabled. Saying so is the
            // point: a greyed button with no reason is the same as a broken screen to a parent at
            // 2am. This screen is the DAY-2 follow-up; the first day is AddHealthRecord.
            if (record == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (s.isThai) "ยังไม่มีอาการที่กำลังติดตามอยู่ค่ะ"
                            else "No illness is being tracked yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (s.isThai)
                                "หน้านี้ใช้บันทึกอาการของแต่ละวันหลังเริ่มติดตามแล้ว ถ้าลูกเพิ่งไม่สบาย เริ่มที่บันทึกสุขภาพก่อนนะคะ"
                            else
                                "This page records each day of an illness already being tracked. If your child has just become unwell, start with a health record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Outline
                        )
                        Button(
                            onClick = { navController.navigate(Screen.AddHealthRecord.route) },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(
                                if (s.isThai) "บันทึกสุขภาพ / ไข้" else "Record a fever or symptoms",
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Multi-day strip (Stitch)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.isThai) "วันที่บันทึก" else "Log date", style = MaterialTheme.typography.labelMedium, color = Outline)
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                        Text(
                            if (s.isThai) "วันที่ $dayNumber ของการติดตาม" else "Day $dayNumber of recovery",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayOptions.forEach { d ->
                        val selected = d == selectedDate
                        val hasLog = state.symptomLogs.any { it.logDate == d.toString() }
                        Surface(
                            onClick = { selectedDate = d },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Primary else MaterialTheme.colorScheme.surface,
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = if (selected) 2.dp else 0.dp
                        ) {
                            Column(
                                Modifier.width(56.dp).padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, if (s.isThai) Locale("th") else Locale.ENGLISH),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) androidx.compose.ui.graphics.Color.White else Outline
                                )
                                Text(
                                    d.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasLog && !selected) {
                                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(100.dp)).background(Primary))
                                }
                            }
                        }
                    }
                }
            }

            // Entries already recorded for this day — tap to edit, or start a fresh one.
            if (dayLogs.isNotEmpty()) {
                SectionCard(
                    title = if (s.isThai) "บันทึกของวันนี้ (${dayLogs.size} ครั้ง)"
                    else "Entries this day (${dayLogs.size})",
                    icon = Icons.Filled.History
                ) {
                    dayLogs.forEachIndexed { i, log ->
                        val sel = log.id == editingId
                        val locked = HealthRecordRules.isSymptomLogEditLocked(log)
                        val time = com.lullabyai.app.util.TimeUtils.localTime(log.updatedAt ?: log.createdAt)
                        Surface(
                            onClick = { editingId = if (sel) null else log.id; formNonce++ },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) Primary.copy(0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    time ?: "#${i + 1}",
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    color = if (sel) Primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    buildString {
                                        val t = log.temperature
                                        if (t != null) {
                                            if (log.temperatureUnit == "F") append(String.format(Locale.US, "%.1f°F", t * 9 / 5 + 32))
                                            else append(String.format(Locale.US, "%.1f°C", t))
                                        } else log.feeling?.let { f ->
                                            append(FEELINGS.firstOrNull { it.first == f }
                                                ?.let { if (s.isThai) it.second.first else it.second.second } ?: f)
                                        }
                                        if (log.symptoms.isNotEmpty()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(log.symptoms.keys.joinToString(", ") { symptomLabel(it, s.isThai) })
                                        }
                                    }.ifBlank { if (s.isThai) "ไม่มีรายละเอียด" else "No details" },
                                    style = MaterialTheme.typography.bodySmall, color = Outline,
                                    modifier = Modifier.weight(1f), maxLines = 1
                                )
                                if (locked) Icon(Icons.Filled.Lock, null, tint = Outline, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { editingId = null; formNonce++ }) {
                        Icon(Icons.Filled.AddCircle, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (s.isThai) "บันทึกเพิ่มอีกครั้ง" else "Add another entry",
                            color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (editBlocked) {
                Surface(shape = RoundedCornerShape(12.dp), color = Outline.copy(0.12f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (s.isThai) "ล็อกแล้ว — แก้ไขอาการได้ภายใน 10 นาทีหลังอัปเดตล่าสุด"
                        else "Locked — symptoms editable for 10 minutes after the last update",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall, color = Outline, fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (existingLog != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = Primary.copy(0.08f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (s.isThai) "กำลังแก้ไขบันทึกของวันที่นี้" else "Editing this day's existing log",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall, color = Primary, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Temperature / feeling toggle
            SectionCard(if (s.isThai) "อุณหภูมิร่างกาย" else "Body temperature", Icons.Filled.Thermostat) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), RoundedCornerShape(12.dp)).padding(4.dp)
                ) {
                    // Feeling first — it is the primary way to record temperature.
                    listOf(true to (if (s.isThai) "ความรู้สึก" else "Feeling"), false to (if (s.isThai) "ตัวเลข" else "Number")).forEach { (feelMode, label) ->
                        val sel = useFeeling == feelMode
                        Surface(
                            onClick = { if (!editBlocked) useFeeling = feelMode },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            color = if (sel) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(label, fontWeight = FontWeight.Bold, color = if (sel) Primary else Outline, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (!useFeeling) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tempText, onValueChange = { if (!editBlocked) tempText = it },
                            enabled = !editBlocked, modifier = Modifier.weight(1f), singleLine = true,
                            placeholder = { Text(if (tempInC) "36.5" else "97.7", color = Outline) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                        )
                        // C / F unit selector — value is converted to canonical Celsius on save
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ChoiceChip("°C", tempInC, Modifier.width(56.dp), enabled = !editBlocked) { tempInC = true }
                            ChoiceChip("°F", !tempInC, Modifier.width(56.dp), enabled = !editBlocked) { tempInC = false }
                        }
                    }
                    if (tempOutOfRange) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (s.isThai) "อุณหภูมิดูผิดปกติ ตรวจสอบหน่วย (C/F) อีกครั้ง"
                            else "Temperature looks wrong — check the unit (C/F)",
                            style = MaterialTheme.typography.labelSmall, color = Error
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FEELINGS.forEach { (key, label) ->
                            ChoiceChip(if (s.isThai) label.first else label.second, feeling == key, Modifier.weight(1f), enabled = !editBlocked) {
                                feeling = if (feeling == key) null else key
                            }
                        }
                    }
                }
            }

            SectionCard(if (s.isThai) "รายการอาการ" else "Symptoms", Icons.Filled.Sick) {
                Text(
                    if (s.isThai) "เลือกระดับความรุนแรงของแต่ละอาการ · ระดับความเจ็บปวดรวม $derivedPain / 10"
                    else "Pick a level for each symptom · overall pain $derivedPain / 10",
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
                Spacer(Modifier.height(4.dp))
                symptomKeys.forEach { key ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(symptomLabel(key, s.isThai), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SEVERITIES.forEach { (sevKey, label) ->
                                val selected = (severities[key] ?: "none") == sevKey
                                ChoiceChip(if (s.isThai) label.first else label.second, selected, Modifier.weight(1f), enabled = !editBlocked) {
                                    severities = severities + (key to sevKey)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customSymptom, onValueChange = { customSymptom = it },
                    enabled = !editBlocked, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (s.isThai) "เพิ่มอาการอื่นๆ..." else "Add other symptom...", color = Outline) },
                    trailingIcon = {
                        IconButton(
                            enabled = !editBlocked && customSymptom.isNotBlank(),
                            onClick = {
                                val key = customSymptom.trim().lowercase().replace(' ', '_')
                                // lands in the list above with its own level chips to adjust
                                severities = severities + (key to "mild")
                                customSymptom = ""
                            }
                        ) { Icon(Icons.Filled.AddCircle, null, tint = Primary) }
                    },
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            SectionCard(if (s.isThai) "สถานะอาการปัจจุบัน" else "Current status", Icons.Filled.Timeline) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_STATUSES.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !editBlocked) { dayStatus = key },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = dayStatus == key,
                                onClick = { if (!editBlocked) dayStatus = key },
                                enabled = !editBlocked,
                                colors = RadioButtonDefaults.colors(selectedColor = Primary)
                            )
                            Text(if (s.isThai) label.first else label.second, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SectionCard(if (s.isThai) "ข้อสังเกตเพิ่มเติม" else "Additional notes", Icons.Filled.EditNote) {
                OutlinedTextField(
                    value = notes, onValueChange = { if (!editBlocked) notes = it },
                    enabled = !editBlocked, modifier = Modifier.fillMaxWidth().height(96.dp),
                    placeholder = { Text(if (s.isThai) "รายละเอียดเพิ่มเติม (ไม่บังคับ)" else "Additional details (optional)", color = Outline) },
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            SectionCard(if (s.isThai) "รูปภาพประกอบ" else "Attached images", Icons.Filled.PhotoLibrary) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(100.dp).clip(RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
                            .clickable(enabled = !editBlocked && !state.healthPhotoUploading) {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddAPhoto, null, tint = if (editBlocked) Outline else Primary)
                            Text(if (s.isThai) "แนบรูป" else "Add photo", style = MaterialTheme.typography.labelSmall, color = Outline)
                        }
                    }
                    val preview = state.symptomLogPhotoSignedUrl
                    if (!preview.isNullOrBlank() || !photoPath.isNullOrBlank()) {
                        if (!preview.isNullOrBlank()) {
                            AsyncImage(
                                model = preview, contentDescription = null,
                                modifier = Modifier.size(100.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(Modifier.size(100.dp), shape = RoundedCornerShape(14.dp), color = Primary.copy(0.08f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Image, null, tint = Primary)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { submit() },
                enabled = record != null && !state.symptomLogSaving && !editBlocked && !tempOutOfRange,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.symptomLogSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                else Text(
                    when {
                        existingLog != null -> if (s.isThai) "อัปเดตบันทึกนี้" else "Update this entry"
                        dayLogs.isNotEmpty() -> if (s.isThai) "บันทึกเพิ่มอีกครั้ง" else "Add another entry"
                        else -> if (s.isThai) "บันทึกอาการ" else "Submit record"
                    },
                    fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

private fun ChronoDaysBetween(start: LocalDate, end: LocalDate): Int =
    java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(0)

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

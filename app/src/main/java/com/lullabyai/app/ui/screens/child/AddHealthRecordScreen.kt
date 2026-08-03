@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/** Stitch log_health_record_with_feeling_selection — number/feeling temp, status chips, photo. */
@Composable
fun AddHealthRecordScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var date by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    var celsius by remember { mutableStateOf(true) }
    var temperature by remember { mutableStateOf("") }
    // Feeling is the main way to record temperature; the number+unit field is opt-in.
    var useFeeling by remember { mutableStateOf(true) }
    var feeling by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var dayStatus by remember { mutableStateOf("started") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoPreview by remember { mutableStateOf<Any?>(null) }
    val symptoms = if (s.isThai) listOf("ตัวร้อน", "ไอ", "มีน้ำมูก", "ผื่น", "ซึม")
                   else listOf("Fever", "Cough", "Runny nose", "Rash", "Lethargic")
    var selected by remember { mutableStateOf(setOf<String>()) }

    val tempC = remember(temperature, celsius) {
        temperature.toDoubleOrNull()?.let { if (celsius) it else (it - 32) * 5 / 9 }
    }
    val tempOutOfRange = !useFeeling && tempC != null && (tempC < 35.0 || tempC > 43.0)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            photoPreview = it
            photoBytes = runCatching { context.contentResolver.openInputStream(it)?.use { st -> st.readBytes() } }.getOrNull()
        }
    }

    fun save() {
        val selectedList = selected.toList()
        val title = selectedList.firstOrNull() ?: (if (s.isThai) "บันทึกสุขภาพ" else "Health Record")
        val feelingNote = when (feeling) {
            "cold" -> if (s.isThai) "ความรู้สึก: หนาวสั่น" else "Feeling: chilly"
            "normal" -> if (s.isThai) "ความรู้สึก: ปกติ" else "Feeling: normal"
            "warm" -> if (s.isThai) "ความรู้สึก: ตัวอุ่น" else "Feeling: warm"
            "hot" -> if (s.isThai) "ความรู้สึก: ตัวร้อน" else "Feeling: hot"
            else -> null
        }
        val statusNote = when (dayStatus) {
            "started" -> if (s.isThai) "สถานะ: เริ่มมีอาการ" else "Status: just started"
            "active" -> if (s.isThai) "สถานะ: กำลังมีอาการ" else "Status: ongoing"
            "recovered" -> if (s.isThai) "สถานะ: หายแล้ว" else "Status: recovered"
            else -> null
        }
        val mergedDesc = listOfNotNull(
            description.ifBlank { null },
            if (useFeeling) feelingNote else null,
            statusNote
        ).joinToString("\n").ifBlank { null }
        childViewModel.addHealthRecord(
            HealthRecord(
                eventType = "log",
                title = title,
                description = mergedDesc,
                severity = "mild",
                temperature = if (useFeeling) null else tempC,
                additionalSymptoms = selectedList.drop(1).ifEmpty { null },
                recordedAt = date,
                status = if (dayStatus == "recovered") "recovered" else "monitoring"
            ),
            photoBytes = photoBytes,
            onDone = onBack
        )
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "บันทึกสุขภาพ" else "Health Record",
                onBack = onBack,
                actions = {
                    Button(
                        enabled = !tempOutOfRange,
                        onClick = { save() },
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text(if (s.isThai) "บันทึก" else "Save", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SectionLabel(if (s.isThai) "วันที่บันทึก" else "Record date")
            OutlinedTextField(
                value = com.lullabyai.app.ui.components.formatBirthDateDisplay(date, s.isThai),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                leadingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = Primary) },
                placeholder = { Text("YYYY-MM-DD", color = Outline) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = hrFieldColors()
            )

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "อุณหภูมิร่างกาย" else "Body temperature")
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), RoundedCornerShape(12.dp)).padding(4.dp)
            ) {
                listOf(false to (if (s.isThai) "ตัวเลข" else "Number"), true to (if (s.isThai) "ความรู้สึก" else "Feeling")).forEach { (feelMode, label) ->
                    val sel = useFeeling == feelMode
                    Surface(
                        onClick = { useFeeling = feelMode },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        color = if (sel) MaterialTheme.colorScheme.surface else Color.Transparent
                    ) {
                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(label, fontWeight = FontWeight.Bold, color = if (sel) Primary else Outline, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!useFeeling) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = temperature, onValueChange = { temperature = it },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Filled.Thermostat, null, tint = Primary) },
                        placeholder = { Text(if (celsius) "36.5" else "97.7", color = Outline) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = hrFieldColors()
                    )
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UnitToggle("°C", celsius) { celsius = true }
                        UnitToggle("°F", !celsius) { celsius = false }
                    }
                }
                if (tempOutOfRange) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.WarningAmber, null, tint = Error, modifier = Modifier.size(14.dp))
                        Text(
                            if (s.isThai) "อุณหภูมิดูผิดปกติ ตรวจสอบอีกครั้ง" else "Temperature looks wrong — check again",
                            style = MaterialTheme.typography.labelSmall, color = Error
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "normal" to (if (s.isThai) "ปกติ" else "Normal"),
                        "warm" to (if (s.isThai) "ตัวอุ่น" else "Warm"),
                        "hot" to (if (s.isThai) "ตัวร้อน" else "Hot"),
                        "cold" to (if (s.isThai) "หนาวสั่น" else "Chills")
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = feeling == key,
                            onClick = { feeling = if (feeling == key) null else key },
                            label = { Text(label) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.12f), selectedLabelColor = Primary)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "อาการทั่วไป" else "Common symptoms")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                symptoms.forEach { sym ->
                    val isSel = selected.contains(sym)
                    FilterChip(
                        selected = isSel,
                        onClick = { selected = if (isSel) selected - sym else selected + sym },
                        label = { Text(sym) },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(0.12f),
                            selectedLabelColor = Primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "สถานะอาการ" else "Symptom status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "started" to (if (s.isThai) "เริ่มมีอาการ" else "Just started"),
                    "active" to (if (s.isThai) "กำลังมีอาการ" else "Ongoing"),
                    "recovered" to (if (s.isThai) "หายแล้ว" else "Recovered")
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = dayStatus == key,
                        onClick = { dayStatus = key },
                        label = { Text(label) },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "อาการหรือสิ่งที่บันทึก" else "Symptoms or notes")
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text(if (s.isThai) "พิมพ์รายละเอียดเพิ่มเติมที่นี่..." else "Type more details here...", color = Outline) },
                shape = RoundedCornerShape(12.dp),
                colors = hrFieldColors()
            )

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "รูปภาพประกอบ" else "Attached images")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddAPhoto, null, tint = Primary, modifier = Modifier.size(28.dp))
                            Text(if (s.isThai) "เพิ่มรูป" else "Add photo", style = MaterialTheme.typography.labelSmall, color = Outline)
                        }
                    }
                }
                photoPreview?.let { preview ->
                    AsyncImage(
                        model = preview, contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val initialMillis = runCatching { LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() }
            .getOrDefault(LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli())
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString() }
                    showDatePicker = false
                }) { Text(if (s.isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun UnitToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (active) 1.dp else 0.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Primary else Outline,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun hrFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

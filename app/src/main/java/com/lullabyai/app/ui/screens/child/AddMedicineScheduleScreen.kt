@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.delay

private val QUICK_MEDICINES = listOf("Paracetamol", "Ibuprofen", "Amoxicillin")
private val DOSAGE_UNITS = listOf("mg", "ml", "drops", "pills")

@Composable
fun AddMedicineScheduleScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()

    var medicineName by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var dosageUnit by remember { mutableStateOf("mg") }
    var frequency by remember { mutableStateOf("every_8h") }
    var scheduleType by remember { mutableStateOf("times") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var numDoses by remember { mutableStateOf("3") }
    var notes by remember { mutableStateOf("") }

    // AI dose suggestion — mirrors the mockup's "AI Dose Recommendation" mini card. Debounced so
    // it doesn't fire on every keystroke, and only once there's a real medicine name to reason about.
    var doseAdvice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(medicineName.trim().lowercase(), state.child?.id) {
        val name = medicineName.trim()
        val child = state.child
        if (name.length < 3 || child == null) { doseAdvice = null; return@LaunchedEffect }
        doseAdvice = null
        delay(700)
        val prompt = if (s.isThai)
            "เด็กชื่อ ${child.name} น้ำหนัก ${child.weightKg ?: "ไม่ทราบ"} กก. จะเริ่มทานยา \"$name\" " +
                "ให้คำแนะนำขนาดยาโดยประมาณ 1 ประโยคสั้นๆ เป็นภาษาไทย ห้ามใช้ emoji หรือสัญลักษณ์พิเศษ " +
                "ถ้าไม่แน่ใจให้แนะนำให้ปรึกษาเภสัชกรหรือแพทย์แทนการเดา"
        else
            "A child weighing ${child.weightKg ?: "unknown"} kg is starting \"$name\". " +
                "Give one short sentence with an approximate standard dosage suggestion, no emoji or special symbols. " +
                "If unsure, say to consult a pharmacist or doctor rather than guessing."
        doseAdvice = runCatching { ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 300) }
            .getOrNull()?.takeIf { it.isNotBlank() && !it.contains("ขออภัย") && !it.contains("unavailable") }
    }

    val frequencies = listOf(
        "every_4h" to (if (s.isThai) "ทุก 4 ชั่วโมง" else "Every 4 hours"),
        "every_6h" to (if (s.isThai) "ทุก 6 ชั่วโมง" else "Every 6 hours"),
        "every_8h" to (if (s.isThai) "ทุก 8 ชั่วโมง" else "Every 8 hours"),
        "once_daily" to (if (s.isThai) "วันละครั้ง" else "Once daily"),
        "morning_evening" to (if (s.isThai) "เช้า-เย็น" else "Morning & evening")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "เพิ่มตารางการทานยา" else "Add Medicine Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onSurface) } },
                actions = {
                    TextButton(
                        onClick = {
                            if (medicineName.isNotBlank()) {
                                childViewModel.addMedicationSchedule(
                                    medicineName.trim(), dosage.toDoubleOrNull(), dosageUnit, frequency,
                                    scheduleType, startDate.ifBlank { null }, endDate.ifBlank { null },
                                    numDoses.toIntOrNull(), notes.ifBlank { null }, onDone = onBack
                                )
                            }
                        },
                        enabled = medicineName.isNotBlank() && !state.medicationSaving
                    ) {
                        if (state.medicationSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        else Text(if (s.isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Medicine name + quick picks
            FieldLabel(if (s.isThai) "ชื่อยา" else "Medicine Name")
            OutlinedTextField(
                value = medicineName, onValueChange = { medicineName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (s.isThai) "ค้นหาหรือพิมพ์ชื่อยา" else "Search or type medicine name", color = Outline) },
                singleLine = true, shape = RoundedCornerShape(12.dp), colors = amsFieldColors()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_MEDICINES.forEach { name ->
                    SuggestionChip(onClick = { medicineName = name }, label = { Text(name) }, shape = RoundedCornerShape(100.dp))
                }
            }

            // Dosage
            FieldLabel(if (s.isThai) "ขนาดยา" else "Dosage")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dosage, onValueChange = { dosage = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("5", color = Outline) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = amsFieldColors()
                )
                var unitExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = dosageUnit, onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                        shape = RoundedCornerShape(12.dp), colors = amsFieldColors()
                    )
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        DOSAGE_UNITS.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { dosageUnit = u; unitExpanded = false }) }
                    }
                }
            }

            // Frequency
            FieldLabel(if (s.isThai) "ความถี่" else "Frequency")
            var freqExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = freqExpanded, onExpandedChange = { freqExpanded = it }) {
                OutlinedTextField(
                    value = frequencies.first { it.first == frequency }.second, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(freqExpanded) },
                    shape = RoundedCornerShape(12.dp), colors = amsFieldColors()
                )
                ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                    frequencies.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { frequency = key; freqExpanded = false }) }
                }
            }

            // Schedule period type
            FieldLabel(if (s.isThai) "ระยะเวลาการทานยา" else "Schedule Period")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleTypeButton(Icons.Filled.CalendarMonth, if (s.isThai) "ช่วงวันที่" else "Date Range", scheduleType == "date_range", Modifier.weight(1f)) { scheduleType = "date_range" }
                ScheduleTypeButton(Icons.Filled.Repeat, if (s.isThai) "จำนวนครั้ง" else "Times", scheduleType == "times", Modifier.weight(1f)) { scheduleType = "times" }
                ScheduleTypeButton(Icons.Filled.Healing, if (s.isThai) "จนกว่าจะหมด" else "Until Gone", scheduleType == "until_gone", Modifier.weight(1f)) { scheduleType = "until_gone" }
            }

            when (scheduleType) {
                "date_range" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = startDate, onValueChange = { startDate = it }, modifier = Modifier.weight(1f), label = { Text(if (s.isThai) "วันเริ่ม" else "Start date") }, placeholder = { Text("YYYY-MM-DD", color = Outline) }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = amsFieldColors())
                    OutlinedTextField(value = endDate, onValueChange = { endDate = it }, modifier = Modifier.weight(1f), label = { Text(if (s.isThai) "วันสิ้นสุด" else "End date") }, placeholder = { Text("YYYY-MM-DD", color = Outline) }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = amsFieldColors())
                }
                "times" -> OutlinedTextField(
                    value = numDoses, onValueChange = { numDoses = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (s.isThai) "จำนวนครั้งที่ต้องทาน" else "Number of Doses") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = amsFieldColors()
                )
                else -> Text(
                    if (s.isThai) "ทานต่อเนื่องจนกว่ายาจะหมด — ระบบจะสร้างตารางล่วงหน้า 14 วัน" else "Continue until the medication is finished — a 14-day schedule will be generated ahead of time.",
                    style = MaterialTheme.typography.bodySmall, color = Outline
                )
            }

            // AI Dose Recommendation (per mockup) — only shown once we have a real suggestion,
            // never a placeholder/loading state, since a wrong or fabricated dosage tip is worse
            // than no tip at all.
            doseAdvice?.let { advice ->
                Surface(shape = RoundedCornerShape(16.dp), color = Tertiary.copy(0.08f)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                        Column {
                            Text(if (s.isThai) "คำแนะนำขนาดยาจาก AI" else "AI Dose Recommendation", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Tertiary)
                            Text(advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Notes
            FieldLabel(if (s.isThai) "หมายเหตุ (ถ้ามี)" else "Notes (optional)")
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth().height(90.dp),
                shape = RoundedCornerShape(12.dp), colors = amsFieldColors()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ScheduleTypeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = if (selected) Primary.copy(0.12f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = if (selected) Primary else Outline, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else Outline, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun amsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

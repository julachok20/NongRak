@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.components.ScheduleDatePickerField
import com.lullabyai.app.ui.components.ScheduleTimePickerField
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorAddAppointmentViewModel

/** Doctor manually books/confirms an appointment for an existing patient — search within the
 *  doctor's own patient list, pick a type, date/time, and notes. Mirrors Stitch
 *  "Premium Add Appointment (Redesign)". */
@Composable
fun DoctorAddAppointmentScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorAddAppointmentViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.loadPatients() }
    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }

    var query by remember { mutableStateOf("") }
    var apptType by remember { mutableStateOf("general") } // general | health_check | vaccine | followup
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var time by remember { mutableStateOf("09:00") }
    var notes by remember { mutableStateOf("") }

    // Vaccine selection state
    var isCustomVaccine by remember { mutableStateOf(false) }
    var selectedVaccineRecord by remember { mutableStateOf<com.lullabyai.app.data.model.VaccineRecord?>(null) }
    var customVaccineName by remember { mutableStateOf("") }

    val duplicateOrSimilarMatch = remember(customVaccineName, state.patientVaccines, isCustomVaccine) {
        if (isCustomVaccine && customVaccineName.trim().isNotBlank()) {
            com.lullabyai.app.util.VaccineSimilarityMatcher.findSimilarVaccine(customVaccineName, state.patientVaccines)
        } else null
    }

    val matches = remember(state.allPatients, query) {
        if (query.isBlank()) emptyList() else state.allPatients.filter { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "เพิ่มนัดหมายใหม่" else "Add New Appointment",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                Text(if (s.isThai) "ค้นหาผู้ป่วย" else "Search patient", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; vm.selectPatient(null); selectedVaccineRecord = null }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "ค้นหาด้วยชื่อผู้ป่วย..." else "Search by patient name...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) }, singleLine = true, shape = RoundedCornerShape(16.dp)
                )
            }
            if (state.selectedPatient == null && matches.isNotEmpty()) {
                items(matches, key = { it.id }) { child ->
                    Surface(onClick = { vm.selectPatient(child); query = child.name }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.ChildCare, null, tint = Primary)
                            Text(child.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            state.selectedPatient?.let { patient ->
                item { SelectedPatientCard(patient) { vm.selectPatient(null); query = ""; selectedVaccineRecord = null } }
            }
            item {
                Text(if (s.isThai) "ประเภทการตรวจ" else "Appointment type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeCard(Modifier.weight(1f), Icons.Filled.MedicalServices, if (s.isThai) "ตรวจทั่วไป" else "General", apptType == "general") { apptType = "general" }
                    TypeCard(Modifier.weight(1f), Icons.Filled.HealthAndSafety, if (s.isThai) "ตรวจสุขภาพ" else "Health check", apptType == "health_check") { apptType = "health_check" }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeCard(Modifier.weight(1f), Icons.Filled.Vaccines, if (s.isThai) "วัคซีน" else "Vaccine", apptType == "vaccine") { apptType = "vaccine" }
                    TypeCard(Modifier.weight(1f), Icons.Filled.EventRepeat, if (s.isThai) "ติดตามผล" else "Follow-up", apptType == "followup") { apptType = "followup" }
                }
            }

            if (apptType == "vaccine") {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Vaccines, null, tint = Primary)
                                Text(
                                    if (s.isThai) "เลือกวัคซีนที่จะฉีด" else "Select vaccine to administer",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !isCustomVaccine,
                                    onClick = { isCustomVaccine = false },
                                    label = { Text(if (s.isThai) "จากสมุด (${state.patientVaccines.size})" else "From Book (${state.patientVaccines.size})") },
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = isCustomVaccine,
                                    onClick = { isCustomVaccine = true },
                                    label = { Text(if (s.isThai) "+ วัคซีนใหม่ / อื่นๆ" else "+ New / Other") },
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (!isCustomVaccine) {
                                if (state.patientVaccines.isEmpty()) {
                                    Text(
                                        if (s.isThai) "ยังไม่มีข้อมูลสมุดวัคซีน — ระบุชื่อวัคซีนใหม่ได้ด้านบน"
                                        else "No vaccines in book — enter custom name above",
                                        style = MaterialTheme.typography.bodySmall, color = Outline
                                    )
                                } else {
                                    var expanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                        OutlinedTextField(
                                            value = selectedVaccineRecord?.let { "${it.vaccineName} (${it.status})" }
                                                ?: (if (s.isThai) "เลือกวัคซีนจากสมุด..." else "Select vaccine..."),
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(if (s.isThai) "วัคซีนในสมุด" else "Vaccine in book") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        )
                                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            state.patientVaccines.forEach { vac ->
                                                DropdownMenuItem(
                                                    text = { Text("${vac.vaccineName} ${vac.ageGroup?.let { "• $it" }.orEmpty()} [${vac.status}]") },
                                                    onClick = { selectedVaccineRecord = vac; expanded = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = customVaccineName,
                                    onValueChange = { customVaccineName = it },
                                    label = { Text(if (s.isThai) "ชื่อวัคซีนใหม่" else "New vaccine name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )

                                if (duplicateOrSimilarMatch != null) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                    ) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                Text(
                                                    if (s.isThai) "พบวัคซีนที่ใกล้เคียงในสมุดคนไข้!" else "Similar vaccine found in book!",
                                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Text(
                                                "\"${duplicateOrSimilarMatch.matchedRecord.vaccineName}\" (${duplicateOrSimilarMatch.matchedRecord.status})",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Button(
                                                onClick = {
                                                    isCustomVaccine = false
                                                    selectedVaccineRecord = duplicateOrSimilarMatch.matchedRecord
                                                    customVaccineName = ""
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                shape = RoundedCornerShape(100.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(if (s.isThai) "สลับไปเลือกรายการนี้จากสมุด" else "Switch to select from book")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScheduleDatePickerField(
                        value = date,
                        onValueChange = { date = it },
                        modifier = Modifier.weight(1f),
                        label = if (s.isThai) "วันที่นัดหมาย" else "Date",
                        isThai = s.isThai
                    )
                    ScheduleTimePickerField(
                        value = time,
                        onValueChange = { time = it },
                        modifier = Modifier.weight(1f),
                        label = if (s.isThai) "เวลานัดหมาย" else "Time",
                        isThai = s.isThai
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (s.isThai) "หมายเหตุ / อาการเบื้องต้น" else "Notes / initial symptoms") }
                )
            }
            state.error?.let { item { Text(it, color = Error, style = MaterialTheme.typography.labelMedium) } }

            val isVaccineValid = apptType != "vaccine" ||
                (!isCustomVaccine && selectedVaccineRecord != null) ||
                (isCustomVaccine && customVaccineName.trim().isNotBlank())

            item {
                Button(
                    onClick = {
                        val bookingType = when (apptType) {
                            "vaccine" -> com.lullabyai.app.util.AppointmentBookingType.VACCINE
                            "followup" -> com.lullabyai.app.util.AppointmentBookingType.FOLLOW_UP
                            "health_check" -> com.lullabyai.app.util.AppointmentBookingType.HEALTH_CHECK
                            else -> com.lullabyai.app.util.AppointmentBookingType.NORMAL
                        }
                        val vacName = if (bookingType == com.lullabyai.app.util.AppointmentBookingType.VACCINE) {
                            if (isCustomVaccine) customVaccineName.trim() else selectedVaccineRecord?.vaccineName
                        } else null
                        val vacId = if (bookingType == com.lullabyai.app.util.AppointmentBookingType.VACCINE && !isCustomVaccine) {
                            selectedVaccineRecord?.id
                        } else null

                        vm.submit(
                            type = bookingType,
                            notes = notes,
                            scheduledAtIso = "${date}T$time:00",
                            vaccineName = vacName,
                            vaccineRecordId = vacId
                        )
                    },
                    enabled = state.selectedPatient != null && !state.isSaving && isVaccineValid,
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(100.dp)
                ) {
                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text(if (s.isThai) "ยืนยันการนัดหมาย" else "Confirm Appointment", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SelectedPatientCard(child: Child, onClear: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChildCare, null, tint = OnSecondaryContainer)
                }
                Column {
                    Text(child.name, fontWeight = FontWeight.Bold)
                    Text(child.birthdate, style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, null, tint = Error) }
        }
    }
}

@Composable
private fun TypeCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary.copy(0.1f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else OutlineVariant)
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Primary)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

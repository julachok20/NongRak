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
                    value = query, onValueChange = { query = it; vm.selectPatient(null) }, modifier = Modifier.fillMaxWidth(),
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
                item { SelectedPatientCard(patient) { vm.selectPatient(null); query = "" } }
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
            item {
                Button(
                    onClick = {
                        val typeLabel = when (apptType) {
                            "vaccine" -> if (s.isThai) "นัดวัคซีน" else "Vaccine appointment"
                            "followup" -> if (s.isThai) "นัดติดตามผล" else "Follow-up appointment"
                            "health_check" -> if (s.isThai) "ตรวจสุขภาพ" else "Health check"
                            else -> if (s.isThai) "ตรวจทั่วไป" else "General checkup"
                        }
                        vm.submit(if (notes.isNotBlank()) "$typeLabel: $notes" else typeLabel, "${date}T$time:00")
                    },
                    enabled = state.selectedPatient != null && !state.isSaving,
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

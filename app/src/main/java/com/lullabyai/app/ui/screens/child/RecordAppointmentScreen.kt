@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val REASON_KEYS = listOf("annual_checkup", "vaccine", "follow_up", "acute_illness", "other")
private fun reasonLabel(key: String, isThai: Boolean) = when (key) {
    "annual_checkup" -> if (isThai) "ตรวจสุขภาพประจำปี" else "Annual checkup"
    "vaccine" -> if (isThai) "รับวัคซีน" else "Vaccination"
    "follow_up" -> if (isThai) "ติดตามอาการ" else "Follow-up"
    "acute_illness" -> if (isThai) "อาการป่วยเฉียบพลัน" else "Acute illness"
    "other" -> if (isThai) "อื่นๆ" else "Other"
    else -> if (isThai) "เลือกเหตุผล" else "Select reason"
}

/** "บันทึกนัดหมายล่วงหน้า" — creates a doctor_visits row with visit_type='scheduled'. Reminders are
 *  persisted as a jsonb list of free-text labels; no actual notification-firing is wired up here
 *  (out of scope — see spec), just the data. */
@Composable
fun RecordAppointmentScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()

    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var facility by remember { mutableStateOf("") }
    var doctorName by remember { mutableStateOf("") }
    var reasonKey by remember { mutableStateOf<String?>(null) }
    var otherReason by remember { mutableStateOf("") }
    var reminders by remember {
        mutableStateOf(listOf(if (s.isThai) "แจ้งเตือนก่อน 1 วัน" else "Remind 1 day before", if (s.isThai) "แจ้งเตือนก่อน 2 ชั่วโมง" else "Remind 2 hours before"))
    }
    var newReminder by remember { mutableStateOf("") }
    var preparationNotes by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var reasonExpanded by remember { mutableStateOf(false) }

    val canSave = date != null && time != null

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "บันทึกนัดหมาย" else "Record Appointment",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (s.isThai) "บันทึกนัดหมายล่วงหน้า" else "Schedule an upcoming appointment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            VisitSectionCard(if (s.isThai) "วันและเวลานัดหมาย" else "Appointment date & time") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VisitPickerField(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.CalendarToday,
                        value = date?.let { formatDate(it, s.isThai) } ?: "",
                        placeholder = if (s.isThai) "วันที่นัดหมาย" else "Date"
                    ) { showDatePicker = true }
                    VisitPickerField(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.AccessTime,
                        value = time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "",
                        placeholder = if (s.isThai) "เวลานัดหมาย" else "Time"
                    ) { showTimePicker = true }
                }
            }

            VisitSectionCard(if (s.isThai) "สถานพยาบาล / คลินิก" else "Facility / clinic") {
                OutlinedTextField(
                    value = facility, onValueChange = { facility = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (s.isThai) "เช่น โรงพยาบาลเด็ก" else "e.g. Children's Hospital", color = Outline) },
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            VisitSectionCard(if (s.isThai) "ชื่อแพทย์ / แผนก" else "Doctor / department") {
                OutlinedTextField(
                    value = doctorName, onValueChange = { doctorName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (s.isThai) "เช่น นพ.สมชาย (กุมารเวช)" else "e.g. Dr. Smith (Pediatrics)", color = Outline) },
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            VisitSectionCard(if (s.isThai) "เหตุผลในการพบแพทย์" else "Reason for visit") {
                ExposedDropdownMenuBox(expanded = reasonExpanded, onExpandedChange = { reasonExpanded = it }) {
                    OutlinedTextField(
                        value = reasonKey?.let { reasonLabel(it, s.isThai) } ?: "",
                        onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        placeholder = { Text(reasonLabel("", s.isThai), color = Outline) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reasonExpanded) },
                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                    )
                    ExposedDropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                        REASON_KEYS.forEach { key ->
                            DropdownMenuItem(text = { Text(reasonLabel(key, s.isThai)) }, onClick = { reasonKey = key; reasonExpanded = false })
                        }
                    }
                }
                if (reasonKey == "other") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = otherReason, onValueChange = { otherReason = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(if (s.isThai) "ระบุเหตุผลอื่นๆ" else "Specify other reason", color = Outline) },
                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                    )
                }
            }

            VisitSectionCard(if (s.isThai) "การแจ้งเตือน" else "Reminders") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminders.forEachIndexed { index, label ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { reminders = reminders.toMutableList().also { it.removeAt(index) } }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.DeleteOutline, if (s.isThai) "ลบ" else "Delete", tint = Error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newReminder, onValueChange = { newReminder = it }, modifier = Modifier.weight(1f), singleLine = true,
                            placeholder = { Text(if (s.isThai) "เช่น แจ้งเตือนก่อน 1 สัปดาห์" else "e.g. Remind 1 week before", color = Outline) },
                            shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                        )
                        TextButton(onClick = {
                            if (newReminder.isNotBlank()) { reminders = reminders + newReminder.trim(); newReminder = "" }
                        }) { Text(if (s.isThai) "+ เพิ่ม" else "+ Add", color = Primary, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            VisitSectionCard(if (s.isThai) "สิ่งที่ต้องเตรียมหรือคำถามสำหรับคุณหมอ" else "What to prepare / questions for the doctor") {
                OutlinedTextField(
                    value = preparationNotes, onValueChange = { preparationNotes = it }, modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text(if (s.isThai) "พิมพ์รายละเอียด..." else "Type details...", color = Outline) },
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            Button(
                onClick = save@{
                    val d = date ?: return@save
                    val t = time ?: return@save
                    val zone = ZoneId.systemDefault()
                    val visitDatetime = LocalDateTime.of(d, t).atZone(zone).toInstant().toString()
                    val reason = if (reasonKey == "other") otherReason.ifBlank { null } else reasonKey?.let { reasonLabel(it, s.isThai) }
                    childViewModel.addAppointment(
                        visitDatetime = visitDatetime, facilityName = facility.ifBlank { null },
                        doctorName = doctorName.ifBlank { null }, reason = reason,
                        reminders = reminders, preparationNotes = preparationNotes.ifBlank { null },
                        onDone = onBack
                    )
                },
                enabled = canSave && !state.doctorVisitSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.doctorVisitSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (s.isThai) "บันทึกข้อมูลนัดหมาย" else "Save appointment", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val initialMillis = (date ?: LocalDate.now()).atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
                    showDatePicker = false
                }) { Text(if (s.isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val now = time ?: LocalTime.now()
        val pickerState = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(pickerState.hour, pickerState.minute); showTimePicker = false }) { Text(if (s.isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
internal fun VisitSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun VisitPickerField(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, placeholder: String, onClick: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = true, modifier = modifier,
        placeholder = { Text(placeholder, color = Outline, style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            IconButton(onClick = onClick) { Icon(Icons.Filled.ArrowDropDown, null, tint = Outline) }
        },
        singleLine = true, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
    )
}

internal fun formatDate(d: LocalDate, isThai: Boolean): String {
    val monthsTh = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
    val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return if (isThai) "${d.dayOfMonth} ${monthsTh[d.monthValue - 1]} ${d.year + 543}"
    else "${d.dayOfMonth} ${monthsEn[d.monthValue - 1]} ${d.year}"
}

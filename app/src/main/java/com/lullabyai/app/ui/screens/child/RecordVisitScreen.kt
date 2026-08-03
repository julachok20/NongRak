@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** "บันทึกประวัติการรักษา" — logs (or edits, when ChildUiState.openDoctorVisitId is already set by
 *  VisitDetailsScreen's "แก้ไข" button) a completed doctor_visits row. Photo attachments need a row
 *  id to tag against: the first attach silently saves a draft (via [ChildViewModel.saveDoctorVisit])
 *  so state.openDoctorVisitId becomes non-null, same id the final Save button then updates. */
@Composable
fun RecordVisitScreen(childViewModel: ChildViewModel, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val record = state.doctorVisits.firstOrNull { it.id == state.openDoctorVisitId }
    val isEdit = record != null

    var date by remember(record?.id) {
        mutableStateOf(
            record?.visitDatetime?.let { iso -> com.lullabyai.app.util.TimeUtils.localDate(iso) }
                ?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }
        )
    }
    var time by remember(record?.id) {
        mutableStateOf(
            record?.visitDatetime?.let { iso ->
                com.lullabyai.app.util.TimeUtils.localTime(iso)?.let { hm -> LocalTime.parse(hm) }
            }
        )
    }
    var facility by remember(record?.id) { mutableStateOf(record?.facilityName ?: "") }
    var doctorName by remember(record?.id) { mutableStateOf(record?.doctorName ?: "") }
    var weight by remember(record?.id) { mutableStateOf(record?.weightKg?.toString() ?: "") }
    var height by remember(record?.id) { mutableStateOf(record?.heightCm?.toString() ?: "") }
    var temperature by remember(record?.id) { mutableStateOf(record?.temperatureC?.toString() ?: "") }
    var bloodPressure by remember(record?.id) { mutableStateOf(record?.bloodPressure ?: "") }
    var symptoms by remember(record?.id) { mutableStateOf((record?.symptoms ?: emptyList()).toSet()) }
    var reason by remember(record?.id) { mutableStateOf(record?.reason ?: "") }
    var diagnosis by remember(record?.id) { mutableStateOf(record?.diagnosis ?: "") }
    var treatmentPlan by remember(record?.id) { mutableStateOf(record?.treatmentPlan ?: "") }
    var nextAppointment by remember(record?.id) { mutableStateOf(record?.nextAppointmentDate) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNextAppointmentPicker by remember { mutableStateOf(false) }
    var showSymptomPicker by remember { mutableStateOf(false) }

    LaunchedEffect(record?.id) { record?.let { childViewModel.loadVisitDocuments(it.id) } }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val d = date; val t = time
        if (d == null || t == null) return@rememberLauncherForActivityResult // gate: need a datetime before we can create/tag a row
        runCatching {
            val cr = context.contentResolver
            var fname = "document"
            cr.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) fname = c.getString(idx) ?: fname
            }
            val mime = cr.getType(uri)
            val bytes = cr.openInputStream(uri)?.use { st -> st.readBytes() }
            if (bytes == null || bytes.size > 10 * 1024 * 1024) return@rememberLauncherForActivityResult
            val existingId = record?.id
            if (existingId != null) {
                childViewModel.uploadVisitDocument(existingId, fname, mime, bytes)
            } else {
                val visitDatetime = LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toString()
                childViewModel.saveDoctorVisit(
                    visitId = null, visitDatetime = visitDatetime, facilityName = facility.ifBlank { null },
                    doctorName = doctorName.ifBlank { null }, weightKg = weight.toDoubleOrNull(), heightCm = height.toDoubleOrNull(),
                    temperatureC = temperature.toDoubleOrNull(), bloodPressure = bloodPressure.ifBlank { null },
                    symptoms = symptoms.toList(), reason = reason.ifBlank { null }, diagnosis = diagnosis.ifBlank { null },
                    treatmentPlan = treatmentPlan.ifBlank { null }, nextAppointmentDate = nextAppointment
                ) { newId -> childViewModel.uploadVisitDocument(newId, fname, mime, bytes) }
            }
        }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "บันทึกการหาหมอ" else "Record Doctor Visit",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (s.isThai) "บันทึกประวัติการรักษา" else "Log a treatment record", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            VisitSectionCard(if (s.isThai) "วันและเวลาที่พบแพทย์" else "Visit date & time") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VisitPickerField(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.CalendarToday,
                        value = date?.let { formatDate(it, s.isThai) } ?: "", placeholder = if (s.isThai) "วันที่" else "Date"
                    ) { showDatePicker = true }
                    VisitPickerField(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.AccessTime,
                        value = time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "", placeholder = if (s.isThai) "เวลา" else "Time"
                    ) { showTimePicker = true }
                }
            }

            VisitSectionCard(if (s.isThai) "สถานพยาบาล / คลินิก" else "Facility / clinic") {
                OutlinedTextField(
                    value = facility, onValueChange = { facility = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            VisitSectionCard(if (s.isThai) "ชื่อแพทย์ผู้ทำการรักษา" else "Treating doctor") {
                OutlinedTextField(
                    value = doctorName, onValueChange = { doctorName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            VisitSectionCard(if (s.isThai) "สัญญาณชีพ" else "Vitals") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VitalField(weight, { weight = it }, if (s.isThai) "น้ำหนัก (กก.)" else "Weight (kg)", Modifier.weight(1f))
                    VitalField(height, { height = it }, if (s.isThai) "ส่วนสูง (ซม.)" else "Height (cm)", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VitalField(temperature, { temperature = it }, if (s.isThai) "อุณหภูมิ (°C)" else "Temp (°C)", Modifier.weight(1f))
                    OutlinedTextField(
                        value = bloodPressure, onValueChange = { bloodPressure = it }, modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text(if (s.isThai) "ความดัน 90/60" else "BP 90/60", color = Outline, style = MaterialTheme.typography.bodySmall) },
                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                    )
                }
            }

            VisitSectionCard(if (s.isThai) "อาการ" else "Symptoms") {
                OutlinedButton(
                    onClick = { showSymptomPicker = true },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(Icons.Filled.ChecklistRtl, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.isThai) "เลือกอาการจากประวัติการป่วย" else "Pick symptoms from illness history", color = Primary, fontWeight = FontWeight.Bold)
                }
                if (symptoms.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        symptoms.forEach { sym ->
                            InputChip(
                                selected = true, onClick = {}, label = { Text(sym) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp).clip(RoundedCornerShape(100.dp)))
                                },
                                modifier = Modifier,
                                colors = InputChipDefaults.inputChipColors(selectedContainerColor = Primary.copy(0.12f), selectedLabelColor = Primary)
                            )
                        }
                    }
                    TextButton(onClick = { symptoms = emptySet() }) { Text(if (s.isThai) "ล้างการเลือก" else "Clear selection", color = Error) }
                }
            }

            VisitSectionCard(if (s.isThai) "เหตุผลการเข้าพบ / อาการเบื้องต้น" else "Reason for visit / initial symptoms") {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth().height(90.dp),
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            VisitSectionCard(if (s.isThai) "ผลการวินิจฉัยของแพทย์" else "Doctor's diagnosis") {
                OutlinedTextField(
                    value = diagnosis, onValueChange = { diagnosis = it }, modifier = Modifier.fillMaxWidth().height(90.dp),
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            VisitSectionCard(if (s.isThai) "แผนการรักษาและยาที่ได้รับ" else "Treatment plan & medication") {
                OutlinedTextField(
                    value = treatmentPlan, onValueChange = { treatmentPlan = it }, modifier = Modifier.fillMaxWidth().height(90.dp),
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            VisitSectionCard(if (s.isThai) "นัดหมายครั้งต่อไป (ถ้ามี)" else "Next appointment (optional)") {
                VisitPickerField(
                    icon = Icons.Filled.EventRepeat,
                    value = nextAppointment?.let { runCatching { formatDate(LocalDate.parse(it), s.isThai) }.getOrDefault(it) } ?: "",
                    placeholder = if (s.isThai) "เลือกวันที่ (ไม่บังคับ)" else "Pick a date (optional)"
                ) { showNextAppointmentPicker = true }
            }

            VisitSectionCard(if (s.isThai) "แนบเอกสาร" else "Attachments") {
                Text(
                    if (s.isThai) "ถ่ายรูปใบรับรองแพทย์, ใบสั่งยา หรือฟิล์มเอกซเรย์" else "Photograph a medical certificate, prescription, or x-ray film",
                    style = MaterialTheme.typography.bodySmall, color = Outline
                )
                Spacer(Modifier.height(10.dp))
                val docsGate = date != null && time != null
                if (state.visitDocuments.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), modifier = Modifier.height((((state.visitDocuments.size - 1) / 3 + 1) * 72).dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.visitDocuments, key = { it.id }) { doc ->
                            val isPdf = doc.mimeType?.contains("pdf") == true
                            Box(Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)).background((if (isPdf) Error else Primary).copy(0.1f)), contentAlignment = Alignment.Center) {
                                Icon(if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Image, null, tint = if (isPdf) Error else Primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = { docPicker.launch("*/*") },
                    enabled = docsGate && !state.visitDocUploading,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                ) {
                    if (state.visitDocUploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                    else Icon(Icons.Filled.AddAPhoto, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.isThai) "เพิ่มรูปภาพ / เอกสาร" else "Add photo / document", color = Primary, fontWeight = FontWeight.Bold)
                }
                if (!docsGate) {
                    Spacer(Modifier.height(6.dp))
                    Text(if (s.isThai) "กรุณาเลือกวันและเวลาก่อนแนบเอกสาร" else "Pick a date & time before attaching a document", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }

            Button(
                onClick = save@{
                    val d = date ?: return@save
                    val t = time ?: return@save
                    val visitDatetime = LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toString()
                    childViewModel.saveDoctorVisit(
                        visitId = record?.id, visitDatetime = visitDatetime, facilityName = facility.ifBlank { null },
                        doctorName = doctorName.ifBlank { null }, weightKg = weight.toDoubleOrNull(), heightCm = height.toDoubleOrNull(),
                        temperatureC = temperature.toDoubleOrNull(), bloodPressure = bloodPressure.ifBlank { null },
                        symptoms = symptoms.toList(), reason = reason.ifBlank { null }, diagnosis = diagnosis.ifBlank { null },
                        treatmentPlan = treatmentPlan.ifBlank { null }, nextAppointmentDate = nextAppointment
                    ) { onBack() }
                },
                enabled = date != null && time != null && !state.doctorVisitSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.doctorVisitSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (s.isThai) "บันทึกข้อมูล" else "Save", fontWeight = FontWeight.Bold, color = Color.White)
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
        // Numeric input by default -- the dial is fiddly to hit precise minutes on; a toggle lets
        // whoever prefers the dial switch back.
        var useDial by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(pickerState.hour, pickerState.minute); showTimePicker = false }) { Text(if (s.isThai) "ตกลง" else "OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (useDial) TimePicker(state = pickerState) else TimeInput(state = pickerState)
                    TextButton(onClick = { useDial = !useDial }) {
                        Icon(if (useDial) Icons.Filled.Keyboard else Icons.Filled.Schedule, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (useDial) (if (s.isThai) "พิมพ์เวลาแทน" else "Type time instead") else (if (s.isThai) "ใช้หน้าปัดแทน" else "Use dial instead"), color = Primary)
                    }
                }
            }
        )
    }

    if (showSymptomPicker) {
        SymptomPickerSheet(
            childViewModel = childViewModel, isThai = s.isThai,
            onDismiss = { showSymptomPicker = false },
            onConfirm = { picked -> symptoms = symptoms + picked; showSymptomPicker = false }
        )
    }

    if (showNextAppointmentPicker) {
        val zone = ZoneId.systemDefault()
        val initialMillis = (nextAppointment?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()).atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showNextAppointmentPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> nextAppointment = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString() }
                    showNextAppointmentPicker = false
                }) { Text(if (s.isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showNextAppointmentPicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun VitalField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' || it == '/' }) },
        modifier = modifier, singleLine = true,
        placeholder = { Text(label, color = Outline, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
    )
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.ui.components.ScheduleDatePickerField
import com.lullabyai.app.ui.components.ScheduleTimePickerField
import com.lullabyai.app.viewmodel.DoctorPatientScheduleViewModel

/** Doctor's full appointment schedule with per-row actions (view / complete / skip with reason +
 *  reschedule). Mirrors Stitch "Patient Schedule with Skip & Reschedule Flow". */
@Composable
fun DoctorPatientScheduleScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientScheduleViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    var skipTarget by remember { mutableStateOf<Appointment?>(null) }
    // Stitch shows one day at a time with a เลือกวันที่ picker. The view model already loads every
    // appointment for this doctor, so the day filter is client-side.
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val sorted = remember(state.appointments, selectedDate) {
        state.appointments.filter { appt ->
            val day = com.lullabyai.app.ui.screens.doctorconnect.parseScheduleInstant(appt.scheduledAt)
                ?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
            // Requests with no time yet still need attention, so they ride along on today.
            day == selectedDate || (day == null && selectedDate == java.time.LocalDate.now())
        }.sortedBy { it.scheduledAt ?: "" }
    }
    val total = sorted.size
    val pending = sorted.count { it.status == "pending" || it.status == "confirmed" }
    val completed = sorted.count { it.status == "completed" }

    Scaffold(
        topBar = {
            // Standard doctor header (Stitch b2387b3e…): back arrow + centred bold title.
            // Schedule left the footer in the 4->3 tab cut, so without onBack this page had no
            // way out except the system gesture.
            // Stitch b2387b3e… draws back — title — bell — avatar here, which is also the app-wide
            // header rule, so both agree on this screen.
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ตารางนัดหมาย" else "Schedule",
                onBack = { navController.popBackStack() },
                navController = navController,
                centerTitle = true,
                isDoctor = true
            )
        },
        // PATIENTS, not SCHEDULE: the bar is Home | Patients | Settings, so SCHEDULE matched no item
        // and the footer rendered with nothing highlighted. This page hangs off the Patients hub.
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            // Date header — the design leads with "Schedule" / "ตารางนัดหมายวันนี้" and the long-form
            // Thai date before any appointment rows.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Schedule",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Column {
                        Text(
                            if (selectedDate == java.time.LocalDate.now()) {
                                if (s.isThai) "ตารางนัดหมายวันนี้" else "Today's appointments"
                            } else {
                                if (s.isThai) "ตารางนัดหมาย" else "Appointments"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            longThaiDate(selectedDate, s.isThai),
                            style = MaterialTheme.typography.bodySmall,
                            color = Outline
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.isThai) "เลือกวันที่" else "Pick date", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = { navController.navigate(Screen.DoctorAddAppointment.route) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.isThai) "เพิ่มนัดหมาย" else "Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) } }
            } else if (sorted.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.EventBusy, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(
                            if (s.isThai) "ไม่มีนัดหมายในวันที่เลือก" else "No appointments on this date",
                            color = Outline
                        )
                    }
                }
            } else {
                items(sorted, key = { it.id }) { appt ->
                    val child = state.childrenById[appt.childId]
                    ScheduleCard(
                        appt = appt,
                        childName = child?.name?.takeIf { it.isNotBlank() }
                            ?: com.lullabyai.app.data.repository.intakeNameFromNotes(appt.notes)
                            ?: "-",
                        isThai = s.isThai,
                        childAge = child?.birthdate?.let { ageLabel(it, s.isThai) },
                        hn = state.hnByChildId[appt.childId],
                        onOpen = { navController.navigate(Screen.DoctorPatientInfo.route(appt.childId)) },
                        onComplete = { navController.navigate(Screen.DoctorClinicalRecordEntry.route(appt.id)) },
                        onConfirm = { vm.confirm(appt.id) },
                        onSkip = { skipTarget = appt }
                    )
                }
                // "สรุปวันนี้" sits BELOW the timeline in the design, not above it.
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            if (s.isThai) "สรุปวันนี้" else "Today's summary",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Outline
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScheduleStat(Modifier.weight(1f), if (s.isThai) "นัดหมายทั้งหมด" else "All appointments", "$total", Primary)
                            ScheduleStat(Modifier.weight(1f), if (s.isThai) "ในคิวปัจจุบัน" else "In queue now", "$pending", Tertiary)
                            ScheduleStat(Modifier.weight(1f), if (s.isThai) "คนไข้ที่ตรวจแล้ว" else "Patients seen", "$completed", Secondary)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(if (s.isThai) "ตกลง" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }

    skipTarget?.let { appt ->
        SkipRescheduleSheet(
            isThai = s.isThai,
            onDismiss = { skipTarget = null },
            onConfirmSkip = { reason -> vm.skip(appt.id, reason); skipTarget = null },
            onReschedule = { newIso, notes -> vm.reschedule(appt.id, newIso, notes); skipTarget = null }
        )
    }
}

/** "วันพุธที่ 24 พฤษภาคม 2567" — long-form Thai date with Buddhist era, as the design shows. */
private fun longThaiDate(date: java.time.LocalDate, isThai: Boolean): String {
    if (!isThai) return date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", java.util.Locale.getDefault()))
    val th = java.util.Locale("th", "TH")
    val dow = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", th))
    val month = date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM", th))
    return "${dow}ที่ ${date.dayOfMonth} $month ${date.year + 543}"
}

/** "5 ปี 4 เดือน" from a yyyy-MM-dd birthdate; null when unparseable. */
private fun ageLabel(birthdate: String, isThai: Boolean): String? {
    val bd = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull() ?: return null
    val p = java.time.Period.between(bd, java.time.LocalDate.now())
    return if (isThai) "${p.years} ปี ${p.months} เดือน" else "${p.years}y ${p.months}m"
}

@Composable
private fun ScheduleStat(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
        }
    }
}

@Composable
private fun ScheduleCard(
    appt: Appointment, childName: String, isThai: Boolean, childAge: String?, hn: String?,
    onOpen: () -> Unit, onComplete: () -> Unit, onConfirm: () -> Unit, onSkip: () -> Unit
) {
    val time = com.lullabyai.app.util.TimeUtils.localTime(appt.scheduledAt) ?: "-"
    val date = com.lullabyai.app.util.TimeUtils.localDate(appt.scheduledAt) ?: ""
    val (badgeColor, badgeText) = when (appt.status) {
        "completed" -> HealthGood to (if (isThai) "เสร็จสิ้น" else "COMPLETED")
        "confirmed" -> Primary to (if (isThai) "ยืนยันแล้ว" else "CONFIRMED")
        "pending" -> Tertiary to (if (isThai) "รออนุมัติ" else "Awaiting approval")
        "skipped" -> Outline to (if (isThai) "ข้าม" else "SKIPPED")
        "rescheduled" -> Color(0xFFF57C00) to (if (isThai) "เลื่อนนัด" else "RESCHEDULED")
        "cancelled" -> Error to (if (isThai) "ยกเลิก" else "CANCELLED")
        else -> Tertiary to (if (isThai) "รอดำเนินการ" else "PENDING")
    }
    val isActionable = appt.status == "pending" || appt.status == "confirmed"

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(badgeColor.copy(0.12f)), contentAlignment = Alignment.Center) {
                        Text(time, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor)
                    }
                    Column {
                        Text(childName, fontWeight = FontWeight.Bold)
                        // "HN: 124-55-90 • 5 ปี 4 เดือน" per the design. HN is clinic-assigned and
                        // omitted entirely until this doctor has set one — no placeholder, since a
                        // stand-in hospital number on a clinical screen is worse than none.
                        Text(
                            listOfNotNull(
                                hn?.let { "HN: $it" },
                                childAge,
                                date.takeIf { it.isNotBlank() }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall, color = Outline
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.12f)) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor)
                }
            }
            appt.reason?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.MedicalServices, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Outline)
                }
            }
            // Completed rows keep one action in the design: แก้ไขบันทึก back into the record entry.
            if (appt.status == "completed") {
                TextButton(onClick = onComplete, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(16.dp), tint = Primary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isThai) "แก้ไขบันทึก" else "Edit record",
                        style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isActionable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        Text(if (isThai) "ดูข้อมูล" else "View", style = MaterialTheme.typography.labelMedium)
                    }
                    if (appt.status == "pending") {
                        OutlinedButton(onClick = onConfirm, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            Text(if (isThai) "ยืนยัน" else "Confirm", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(onClick = onComplete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            Text(if (isThai) "บันทึก" else "Record", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        Icon(Icons.Filled.Block, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (isThai) "ข้าม" else "Skip", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipRescheduleSheet(isThai: Boolean, onDismiss: () -> Unit, onConfirmSkip: (String) -> Unit, onReschedule: (String, String?) -> Unit) {
    // Preset reasons, per the design's radio list, rather than a free-text box. "อื่นๆ" falls back
    // to the details field so nothing is lost when none of the presets fit.
    val presetReasons = remember(isThai) {
        if (isThai) listOf("คนไข้ยังไม่มาตามนัด", "คนไข้ยังไม่พร้อมเข้าตรวจ", "อื่นๆ")
        else listOf("Patient has not arrived", "Patient not ready to be seen", "Other")
    }
    var reason by remember { mutableStateOf(presetReasons.first()) }
    var newDate by remember { mutableStateOf("") }
    var newTime by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    val isOther = reason == presetReasons.last()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isThai) "ระบุเหตุผลที่ข้าม" else "Reason for skipping", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            presetReasons.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { reason = option }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(selected = reason == option, onClick = { reason = option })
                    Text(option, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(if (isThai) "เลื่อนนัดหมายเป็น (ถ้ามี)" else "Reschedule to (optional)", style = MaterialTheme.typography.labelMedium, color = Outline)
            ScheduleDatePickerField(
                value = newDate,
                onValueChange = { newDate = it },
                placeholder = if (isThai) "เลือกวันที่" else "Pick date",
                isThai = isThai
            )
            ScheduleTimePickerField(
                value = newTime,
                onValueChange = { newTime = it },
                placeholder = if (isThai) "เลือกเวลา" else "Pick time",
                isThai = isThai
            )
            OutlinedTextField(
                value = details, onValueChange = { details = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isThai) "รายละเอียดเพิ่มเติม (เพื่อการวิเคราะห์)" else "Additional details (for analysis)") }
            )
            Text(
                if (isThai) "การข้ามนัดหมายจะไม่ส่งผลเสียต่อการประมวลผลของ AI และไม่กระทบประวัติของคนไข้"
                else "Skipping does not harm AI processing or affect the patient's history",
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
            Button(
                // "อื่นๆ" carries no meaning on its own, so it requires the details text.
                enabled = !isOther || details.isNotBlank(),
                onClick = {
                    val finalReason = if (isOther) details.trim() else reason
                    if (newDate.isNotBlank() && newTime.isNotBlank()) {
                        onReschedule("${newDate}T$newTime:00", details.ifBlank { finalReason })
                    } else {
                        onConfirmSkip(finalReason)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp)
            ) { Text(if (isThai) "ยืนยัน" else "Confirm", fontWeight = FontWeight.Bold) }
        }
    }
}

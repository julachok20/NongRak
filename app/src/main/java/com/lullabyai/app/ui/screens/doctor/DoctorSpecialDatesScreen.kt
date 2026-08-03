@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.DoctorSpecialDate
import com.lullabyai.app.viewmodel.DoctorSpecialDatesViewModel
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.viewmodel.DoctorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Must match [Screen.DoctorSpecialDates]. */
const val DOCTOR_SPECIAL_DATES_ROUTE = "doctor_special_dates"

private val THAI_MONTHS = listOf(
    "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
    "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
)

/** "12 ธ.ค. 2567" in Thai (Buddhist era, as in the Stitch design) / "12 Dec 2024" in English. */
private fun formatDate(iso: String, isThai: Boolean): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return if (isThai) "${d.dayOfMonth} ${THAI_MONTHS[d.monthValue - 1]} ${d.year + 543}"
    else "${d.dayOfMonth} ${THAI_MONTHS_EN[d.monthValue - 1]} ${d.year}"
}

private val THAI_MONTHS_EN = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Stitch "Special Dates & Hours Management"
 * (project 10774270498029237432, screen 0696a03a629945a6a760165daf11f0b0).
 *
 * Two sections, exactly as the design: one-off date exceptions (closure or altered hours, backed by
 * `doctor_special_dates`) and the recurring weekly grid (shared with [DoctorWeeklyHoursScreen]).
 * A special date overrides the weekly row for that day.
 */
@Composable
fun DoctorSpecialDatesScreen(
    authViewModel: AuthViewModel,
    doctorViewModel: DoctorViewModel,
    navController: NavController
) {
    val isThai = LocalStrings.current.isThai
    val vm: DoctorSpecialDatesViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    // null = dialog closed; DoctorSpecialDate() = adding a new one; populated = editing.
    var editing by remember { mutableStateOf<DoctorSpecialDate?>(null) }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "จัดการเวลาทำงาน" else "Manage Work Hours",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // --- Special dates ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.EventBusy, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(if (isThai) "วันหยุดพิเศษ" else "Special Dates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (isThai) "ระบุวันหยุดหรือตั้งเวลาเปิด-ปิดพิเศษรายวัน"
                        else "Mark holidays, or set one-off opening hours for a single date",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when {
                        state.isLoading && state.dates.isEmpty() ->
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary)
                            }
                        state.dates.isEmpty() ->
                            Text(
                                if (isThai) "ยังไม่มีวันหยุดหรือเวลาพิเศษ" else "No special dates yet",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                        else -> state.dates.forEach { entry ->
                            SpecialDateRow(
                                entry = entry,
                                isThai = isThai,
                                onEdit = { editing = entry },
                                onDelete = { vm.delete(entry.id) }
                            )
                        }
                    }

                    state.error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

                    OutlinedButton(
                        onClick = { editing = DoctorSpecialDate() },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isThai) "เพิ่มวันหยุดหรือเวลาพิเศษ" else "Add holiday or special hours")
                    }
                }
            }

            // --- Weekly recurring hours (shared with DoctorWeeklyHoursScreen) ---
            DoctorWeeklyHoursSection(authViewModel, doctorViewModel, isThai)

            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { entry ->
        SpecialDateDialog(
            entry = entry,
            isThai = isThai,
            onDismiss = { editing = null },
            onSave = { date, isClosed, start, end, note ->
                vm.save(date, isClosed, start, end, note)
                editing = null
            }
        )
    }
}

@Composable
private fun SpecialDateRow(
    entry: DoctorSpecialDate,
    isThai: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(SurfaceContainerLow, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(18.dp))
                Column {
                    Text(formatDate(entry.specialDate, isThai), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (entry.isClosed) (if (isThai) "ปิดทำการ (วันหยุด)" else "Closed (holiday)")
                        else (if (isThai) "เวลาพิเศษ" else "Special hours"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.isClosed) Error else Outline
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = Error, modifier = Modifier.size(20.dp)) }
            }
        }
        if (!entry.isClosed) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadOnlyTimeBox(if (isThai) "เวลาเปิด" else "Start", entry.startHhMm(), Modifier.weight(1f))
                ReadOnlyTimeBox(if (isThai) "เวลาปิด" else "End", entry.endHhMm(), Modifier.weight(1f))
            }
        }
        entry.note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
        }
    }
}

@Composable
private fun ReadOnlyTimeBox(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AccessTime, null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(value ?: "--:--", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Add/edit one special date. Its confirm button carries the design's "บันทึกวันหยุด" label —
 * the design also drew a separate list-level save, but every change here persists immediately,
 * so a second always-enabled save button would do nothing.
 * // ponytail: one dialog, upsert on confirm. No draft/batch state until someone asks for it.
 */
@Composable
private fun SpecialDateDialog(
    entry: DoctorSpecialDate,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onSave: (date: String, isClosed: Boolean, start: String?, end: String?, note: String?) -> Unit
) {
    var date by remember { mutableStateOf(runCatching { LocalDate.parse(entry.specialDate) }.getOrNull() ?: LocalDate.now()) }
    var isClosed by remember { mutableStateOf(entry.isClosed) }
    var start by remember { mutableStateOf(entry.startHhMm()?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(entry.endHhMm()?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(17, 0)) }
    var note by remember { mutableStateOf(entry.note.orEmpty()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var timePickerForStart by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (entry.id.isBlank()) (if (isThai) "เพิ่มวันหยุดหรือเวลาพิเศษ" else "Add holiday or special hours")
                else (if (isThai) "แก้ไขวันพิเศษ" else "Edit special date"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = { showDatePicker = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(formatDate(date.toString(), isThai), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isThai) "ปิดทำการทั้งวัน" else "Closed all day", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isClosed, onCheckedChange = { isClosed = it })
                }
                if (!isClosed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeField(if (isThai) "เวลาเปิด" else "Start", start, Modifier.weight(1f)) { timePickerForStart = true }
                        TimeField(if (isThai) "เวลาปิด" else "End", end, Modifier.weight(1f)) { timePickerForStart = false }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(if (isThai) "หมายเหตุ" else "Note") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                // A special date with the end before the start would silently produce an unbookable
                // day, so block it here rather than writing it.
                enabled = isClosed || end.isAfter(start),
                onClick = {
                    onSave(
                        date.toString(), isClosed,
                        if (isClosed) null else "%02d:%02d".format(start.hour, start.minute),
                        if (isClosed) null else "%02d:%02d".format(end.hour, end.minute),
                        note.ifBlank { null }
                    )
                }
            ) { Text(if (isThai) "บันทึกวันหยุด" else "Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    timePickerForStart?.let { isStart ->
        com.lullabyai.app.ui.components.TimeSelectDialog(
            initial = if (isStart) start else end,
            isThai = isThai,
            title = if (isStart) {
                if (isThai) "เลือกเวลาเปิด" else "Select opening time"
            } else {
                if (isThai) "เลือกเวลาปิด" else "Select closing time"
            },
            onDismiss = { timePickerForStart = null },
            onConfirm = { picked ->
                if (isStart) start = picked else end = picked
                timePickerForStart = null
            }
        )
    }
}

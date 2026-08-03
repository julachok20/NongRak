@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.lullabyai.app.data.model.DoctorAppointmentSlot
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorManageSlotsViewModel
import com.lullabyai.app.viewmodel.SlotRepeatMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Doctor manages bookable appointment slots for a chosen day — add a slot, open/close existing
 *  ones. Mirrors Stitch "Manage Specific Appointment Slots". */
@Composable
fun DoctorManageSlotsScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorManageSlotsViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dateMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDate) { vm.load(selectedDate.toString()) }

    LifecycleResumeEffect(selectedDate) {
        vm.load(selectedDate.toString())
        onPauseOrDispose { }
    }

    LaunchedEffect(state.openDateOptions) {
        val open = state.openDateOptions
        if (open.isNotEmpty() && open.none { it == selectedDate }) {
            selectedDate = open.first()
        }
    }

    val fallbackDates = remember { (0 until 60).map { LocalDate.now().plusDays(it.toLong()) } }
    val selectableDates = if (state.openDateOptions.isNotEmpty()) state.openDateOptions else fallbackDates
    val dayOpen = state.daySchedule?.isOpen == true

    var startTime by remember { mutableStateOf("09:00") }
    var intervalMinutes by remember { mutableStateOf(10) }
    var repeatMode by remember { mutableStateOf(SlotRepeatMode.ONCE) }
    var showManualAdd by remember { mutableStateOf(false) }
    var manualTime by remember { mutableStateOf("09:00") }

    val slotsForDay = remember(state.slots, selectedDate) {
        state.slots.filter { it.slotDate == selectedDate.toString() }
            .sortedBy { it.startTime }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ตั้งค่าช่วงเวลานัดหมาย" else "Manage Appointment Slots",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = {
            DoctorBottomNav(navController, DoctorTab.PATIENTS)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (s.isThai) "วันทำงานที่เปิดรับนัด" else "Working days open for appointments",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            // "LIVE SYNC" badge from the design: slot generation is bounded by the
                            // doctor's weekly hours, so the two screens genuinely stay in step.
                            Row(
                                Modifier.background(HealthGood.copy(alpha = 0.14f), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(Modifier.size(6.dp).background(HealthGood, RoundedCornerShape(50)))
                                Text("LIVE SYNC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = HealthGood)
                            }
                        }
                        Text(
                            if (s.isThai) "เลือกวันที่ต้องการกำหนดช่วงเวลานัดหมาย" else "Pick the day you want to configure slots for",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExposedDropdownMenuBox(
                            expanded = dateMenuExpanded,
                            onExpandedChange = { dateMenuExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = formatSlotDateLabel(selectedDate, s.isThai),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dateMenuExpanded)
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Primary)
                                },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = dateMenuExpanded,
                                onDismissRequest = { dateMenuExpanded = false }
                            ) {
                                selectableDates.forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(formatSlotDateLabel(d, s.isThai)) },
                                        onClick = {
                                            selectedDate = d
                                            dateMenuExpanded = false
                                        },
                                        leadingIcon = if (d == selectedDate) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, tint = Primary) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!dayOpen) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.EventBusy, null, tint = Error, modifier = Modifier.size(32.dp))
                            Text(
                                if (s.isThai) "วันนี้คลินิกปิดทำการ" else "Clinic closed on this day",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (s.isThai)
                                    "ไม่สามารถตั้งช่วงเวลานัดหมายได้ในวันปิดทำการ กรุณาเลือกวันที่เปิดรับนัดจากด้านบน หรือแก้ไขเวลาทำงานในเมนูจัดการเวลาทำงาน"
                                else
                                    "Appointment slots cannot be set on closed days. Pick an open date above or update working hours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.DoctorSpecialDates.route) }) {
                                Text(if (s.isThai) "ไปที่จัดการเวลาทำงาน" else "Manage working hours", color = Primary)
                            }
                        }
                    }
                }
            } else {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(40.dp).background(Primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Sync, null, tint = Primary, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    if (s.isThai) "ตั้งค่าการทำซ้ำ" else "Repeat settings",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (s.isThai) "เลือกรูปแบบการทำซ้ำสำหรับช่วงเวลานี้"
                                    else "Choose how to repeat these slots",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        RepeatOptionCard(
                            title = if (s.isThai) "เฉพาะวันนี้" else "This day only",
                            subtitle = formatWeekdayLabel(selectedDate, s.isThai),
                            selected = repeatMode == SlotRepeatMode.ONCE,
                            onClick = { repeatMode = SlotRepeatMode.ONCE }
                        )
                        RepeatOptionCard(
                            title = if (s.isThai) "ทำซ้ำทุกวัน" else "Repeat daily",
                            subtitle = if (s.isThai) "(ขึ้นอยู่กับเวลาเปิด-ปิดของคลินิก · 14 วัน)" else "(Clinic open days · next 14 days)",
                            selected = repeatMode == SlotRepeatMode.DAILY,
                            onClick = { repeatMode = SlotRepeatMode.DAILY }
                        )
                        RepeatOptionCard(
                            title = if (s.isThai) "ทำซ้ำทุกสัปดาห์" else "Repeat weekly",
                            subtitle = if (s.isThai) "(เฉพาะ${formatWeekdayLabel(selectedDate, s.isThai)})"
                            else "(Same weekday: ${formatWeekdayLabel(selectedDate, s.isThai)})",
                            selected = repeatMode == SlotRepeatMode.WEEKLY,
                            onClick = { repeatMode = SlotRepeatMode.WEEKLY }
                        )
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Primary)
                            }
                            Text(
                                if (s.isThai) "กำหนดเวลานัดหมาย" else "Define appointment time",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            (if (s.isThai) "เพิ่มช่วงเวลาที่ว่างสำหรับการตรวจรักษาในวัน " else "Add available consultation slots for ") +
                                formatWeekdayLabel(selectedDate, s.isThai),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (s.isThai) "เวลาเริ่มต้น" else "Start time") },
                            placeholder = { Text("HH:mm") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.Schedule, null, tint = Primary) }
                        )
                        Text(
                            if (s.isThai) "ช่วงความห่าง (นาที)" else "Spacing (minutes)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 10, 15, 30).forEach { mins ->
                                val selected = intervalMinutes == mins
                                FilterChip(
                                    selected = selected,
                                    onClick = { intervalMinutes = mins },
                                    label = {
                                        Text(
                                            if (s.isThai) "$mins นาที" else "$mins min",
                                            maxLines = 1
                                        )
                                    },
                                    shape = RoundedCornerShape(100.dp)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (startTime.isNotBlank()) {
                                    vm.autoGenerateWithRepeat(
                                        selectedDate.toString(),
                                        startTime,
                                        intervalMinutes,
                                        repeatMode
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (s.isThai) "เพิ่มช่วงเวลาอัตโนมัติ" else "Auto-add time slots",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (s.isThai) "รายการช่วงเวลา" else "Slot list",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (slotsForDay.any { it.bookedCount == 0 }) {
                                TextButton(onClick = { vm.clearDeletableSlotsForDay(selectedDate.toString()) }) {
                                    Icon(Icons.Filled.DeleteSweep, null, modifier = Modifier.size(16.dp), tint = Error)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (s.isThai) "ล้างทั้งหมด" else "Clear all",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Error
                                    )
                                }
                            }
                        }

                        if (state.isLoading) {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary)
                            }
                        } else {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val cellWidth = (maxWidth - 20.dp) / 3
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    slotsForDay.forEach { slot ->
                                        TimeSlotGridChip(
                                            slot = slot,
                                            isThai = s.isThai,
                                            cellWidth = cellWidth,
                                            onToggleStatus = { newStatus -> vm.setStatus(slot.id, newStatus) },
                                            onDelete = { vm.deleteSlot(slot.id) }
                                        )
                                    }
                                    DashedAddSlotChip(
                                        isThai = s.isThai,
                                        cellWidth = cellWidth,
                                        onClick = { showManualAdd = true }
                                    )
                                }
                            }
                            if (slotsForDay.isEmpty()) {
                                Text(
                                    if (s.isThai) "ยังไม่มีช่วงเวลา — กดเพิ่มอัตโนมัติหรือ +"
                                    else "No slots yet — auto-add or tap +",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Outline,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Lightbulb, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(
                                if (s.isThai) "คำแนะนำสำหรับการตั้งค่า" else "Setup tips",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Primary
                            )
                        }
                        Text(
                            if (s.isThai) "การเปลี่ยนแปลงสล็อตเวลาจะไม่ส่งผลต่อนัดหมายที่ได้รับการยืนยันแล้ว แต่จะส่งผลต่อการจองใหม่ทันที (LIVE SYNC กับผู้ปกครอง)"
                            else "Slot changes do not affect confirmed appointments; new bookings update immediately for parents (LIVE SYNC).",
                            style = MaterialTheme.typography.bodySmall, color = Primary
                        )
                    }
                }
            }
            }
        }
    }

    if (showManualAdd) {
        AlertDialog(
            onDismissRequest = { showManualAdd = false },
            title = { Text(if (s.isThai) "เพิ่มช่วงเวลา" else "Add time slot") },
            text = {
                OutlinedTextField(
                    value = manualTime,
                    onValueChange = { manualTime = it },
                    label = { Text(if (s.isThai) "เวลาเริ่ม (HH:mm)" else "Start (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.addManualSlot(selectedDate.toString(), manualTime, intervalMinutes)
                    showManualAdd = false
                }) { Text(if (s.isThai) "เพิ่ม" else "Add") }
            },
            dismissButton = {
                TextButton(onClick = { showManualAdd = false }) {
                    Text(if (s.isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }
}

/** "วันจันทร์" / "Monday" for the generator's instruction line. */
private fun formatWeekdayLabel(date: LocalDate, isThai: Boolean): String {
    val locale = if (isThai) Locale("th", "TH") else Locale.getDefault()
    return date.format(DateTimeFormatter.ofPattern("EEEE", locale))
}

private fun formatSlotDateLabel(date: LocalDate, isThai: Boolean): String {
    val locale = if (isThai) Locale("th", "TH") else Locale.getDefault()
    val pattern = if (isThai) "EEE d MMM yyyy" else "EEE, MMM d yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
private fun DashedAddSlotChip(isThai: Boolean, cellWidth: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .width(cellWidth)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = if (isThai) "เพิ่มช่วงเวลา" else "Add slot", tint = Outline)
    }
}

@Composable
private fun TimeSlotGridChip(
    slot: DoctorAppointmentSlot,
    isThai: Boolean,
    cellWidth: androidx.compose.ui.unit.Dp,
    onToggleStatus: (String) -> Unit,
    onDelete: () -> Unit
) {
    val isOpen = slot.status == "open"
    val timeLabel = slot.startTime.take(5)
    Box(
        modifier = Modifier.width(cellWidth)
    ) {
        Surface(
            onClick = { onToggleStatus(if (isOpen) "closed" else "open") },
            shape = RoundedCornerShape(12.dp),
            color = if (isOpen) Primary.copy(alpha = 0.12f) else SurfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                if (isOpen) Primary.copy(alpha = 0.55f) else Color.Transparent
            ),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Box(
                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    timeLabel,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOpen) MaterialTheme.colorScheme.onSurface else Outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (slot.bookedCount == 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Error)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = if (isThai) "ลบ" else "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun RepeatOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Primary.copy(alpha = 0.6f) else Outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

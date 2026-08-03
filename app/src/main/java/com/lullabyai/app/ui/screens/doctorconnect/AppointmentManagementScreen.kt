@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.DoctorAppointmentSlot
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.HealthGood
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

/** Route pattern for this screen. Defined here because `Screen.kt` is owned by the orchestrator,
 *  which registers the destination; callers navigate with [appointmentManagementRoute]. */
const val APPOINTMENT_MANAGEMENT_ROUTE = "appointment_management/{appointmentId}"

fun appointmentManagementRoute(appointmentId: String) = "appointment_management/$appointmentId"

/**
 * "จัดการนัดหมาย" — the **แก้ไข/ยกเลิก** destination from the My Doctors Hub follow-up CTAs.
 *
 * Reschedule picks from the doctor's own open slots (`doctor_appointment_slots`, filtered to today
 * onward) and writes back through the same `updateAppointmentStatus(id, status, scheduledAt)`
 * transition the doctor side uses, so there's no second scheduling mechanism.
 */
@Composable
fun AppointmentManagementScreen(appointmentId: String, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val vm: AppointmentManageViewModel = viewModel()
    val ui by vm.ui.collectAsState()

    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedSlotId by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }

    LaunchedEffect(appointmentId) { vm.load(appointmentId) }
    LaunchedEffect(ui.dates) { if (selectedDate == null) selectedDate = ui.dates.firstOrNull() }
    LaunchedEffect(ui.done) { if (ui.done) onBack() }

    val times = ui.timesFor(selectedDate)
    val selectedSlot = times.firstOrNull { it.id == selectedSlotId }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "จัดการนัดหมาย" else "Manage Appointment",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            Column {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedSlot?.let { vm.reschedule(it) } ?: vm.confirm() },
                            enabled = !ui.working && ui.appointment != null,
                            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(100.dp)
                        ) {
                            if (ui.working) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Text(
                                    if (selectedSlot != null) {
                                        if (s.isThai) "ยืนยันการเปลี่ยนนัดหมาย" else "Confirm reschedule"
                                    } else {
                                        if (s.isThai) "ยืนยันนัดหมาย" else "Confirm appointment"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                ConnectBottomNav()
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val appt = ui.appointment
        if (ui.loading || appt == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (ui.loading) CircularProgressIndicator()
                else Text(if (s.isThai) "ไม่พบข้อมูลนัดหมาย" else "Appointment not found", color = Outline)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current appointment
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DoctorAvatar(ui.doctorProfile?.clinicPhotoUrl, size = 52.dp)
                        Column {
                            Text(
                                ui.doctorName?.takeIf { it.isNotBlank() } ?: (if (s.isThai) "ไม่ระบุชื่อแพทย์" else "Unnamed doctor"),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                            )
                            ui.doctorProfile?.specialty?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Primary)
                            }
                        }
                    }
                    ui.doctorProfile?.clinicName?.takeIf { it.isNotBlank() }?.let {
                        InfoLine(Icons.Filled.LocalHospital, it)
                    }
                    InfoLine(
                        Icons.Filled.CalendarMonth,
                        thaiDateTimeLabel(appt.scheduledAt, s.isThai) ?: (if (s.isThai) "รอกำหนดเวลา" else "Awaiting scheduling")
                    )
                }
            }

            // Reschedule
            ConnectSectionHeader(
                if (s.isThai) "เลื่อนนัดหมาย" else "Reschedule",
                trailing = {
                    if (ui.dates.isNotEmpty()) StatusPill(if (s.isThai) "ว่าง" else "Available", HealthGood)
                }
            )

            if (ui.dates.isEmpty()) {
                Text(
                    if (s.isThai) "คุณหมอยังไม่ได้เปิดช่วงเวลาว่างใหม่ กรุณาลองอีกครั้งภายหลัง"
                    else "This doctor hasn't opened any new slots yet — please check back later.",
                    style = MaterialTheme.typography.bodySmall, color = Outline
                )
            } else {
                Text(
                    if (s.isThai) "เลือกวันนัดหมายใหม่" else "Pick a new date",
                    style = MaterialTheme.typography.labelMedium, color = Outline
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.dates, key = { it }) { date ->
                        FilterChip(
                            selected = date == selectedDate,
                            onClick = { selectedDate = date; selectedSlotId = null },
                            label = {
                                Text(
                                    thaiDateTimeLabel(date, s.isThai, withTime = false) ?: date,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(100.dp)
                        )
                    }
                }

                Text(
                    if (s.isThai) "เลือกเวลาที่ต้องการ" else "Pick a time",
                    style = MaterialTheme.typography.labelMedium, color = Outline
                )
                TimeSlotGrid(times, selectedSlotId) { selectedSlotId = it }
            }

            // Cancellation
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.06f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                if (s.isThai) "ยกเลิกนัดหมาย" else "Cancel appointment",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (s.isThai) "หากต้องการยกเลิกนัดหมาย โปรดยืนยันด้านล่าง การยกเลิกควรทำล่วงหน้าอย่างน้อย 24 ชั่วโมง"
                                else "Confirm below to cancel. Cancellations should be made at least 24 hours in advance.",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { cancelReason = ""; confirmCancel = true },
                        enabled = !ui.working && appt.status != "cancelled",
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                    ) {
                        Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.isThai) "ยกเลิกนัดหมายนี้" else "Cancel this appointment", fontWeight = FontWeight.Bold)
                    }
                }
            }

            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { if (!ui.working) confirmCancel = false },
            title = { Text(if (s.isThai) "ยืนยันการยกเลิก" else "Confirm cancellation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (s.isThai) "ต้องการยกเลิกนัดหมายนี้ใช่หรือไม่? กรุณาระบุเหตุผล (จำเป็น)"
                        else "Cancel this appointment? Please provide a reason (required)."
                    )
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (s.isThai) "เหตุผล" else "Reason") },
                        minLines = 2,
                        enabled = !ui.working
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.cancel(cancelReason) },
                    enabled = cancelReason.isNotBlank() && !ui.working
                ) {
                    if (ui.working) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(if (s.isThai) "ยกเลิกนัดหมาย" else "Cancel appointment", color = Error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }, enabled = !ui.working) {
                    Text(if (s.isThai) "ไม่ใช่" else "Keep it")
                }
            }
        )
    }
}

/** Fixed 3-up chip grid; slots at capacity render disabled, matching the Stitch mock. */
@Composable
private fun TimeSlotGrid(slots: List<DoctorAppointmentSlot>, selectedId: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { slot ->
                    val full = slot.bookedCount >= slot.capacity
                    OutlinedButton(
                        onClick = { onSelect(slot.id) },
                        enabled = !full,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (slot.id == selectedId) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = Primary.copy(alpha = 0.14f), contentColor = Primary
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(slot.startTime.take(5), style = MaterialTheme.typography.labelLarge)
                    }
                }
                // Keep the last row's chips the same width as a full row.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

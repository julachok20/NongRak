@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.formatBirthDateDisplay
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.AppointmentBookingMeta
import com.lullabyai.app.util.AppointmentBookingType
import com.lullabyai.app.viewmodel.AppointmentDetailsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Stitch 43.Appointment — parent appointment detail with status hero, doctor card, reason, map, AI tips. */
@Composable
fun AppointmentDetailsScreen(appointmentId: String, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val vm: AppointmentDetailsViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current

    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }

    LaunchedEffect(appointmentId) { vm.load(appointmentId) }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "รายละเอียดการนัดหมาย" else "Appointment Details",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val appt = ui.appointment
        if (ui.loading || appt == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (ui.loading) CircularProgressIndicator(color = Primary)
                else Text(if (s.isThai) "ไม่พบข้อมูลนัดหมาย" else "Appointment not found", color = Outline)
            }
            return@Scaffold
        }

        val bookingType = AppointmentBookingMeta.parseType(appt.reason, appt.notes)
        val isEmergency = AppointmentBookingMeta.isEmergency(appt.notes)
        val statusHero = statusHeroContent(appt.status, appt.notes, s.isThai)
        val displayReason = AppointmentBookingMeta.displayReason(appt.reason, s.isThai)
        val rejectionMessage = appt.notes
            ?.takeIf { appt.status == "cancelled" && it.startsWith("Rejected:", ignoreCase = true) }
            ?.removePrefix("Rejected:")?.removePrefix("rejected:")?.trim()?.takeIf { it.isNotBlank() }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            if (ui.queueDelayed && appt.status in listOf("pending", "confirmed")) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HealthAttention.copy(0.12f))) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HourglassTop, null, tint = HealthAttention)
                        Text(
                            if (s.isThai) "คิวล่าช้า — คุณหมอยังตรวจผู้ป่วยก่อนหน้า กรุณารอสักครู่"
                            else "Queue delayed — the doctor is still with an earlier patient. Please wait.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = HealthAttention
                        )
                    }
                }
            }

            if (isEmergency) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Error.copy(0.1f))) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Emergency, null, tint = Error)
                        Text(
                            if (s.isThai) "นัดหมายฉุกเฉิน — คิวลำดับความสำคัญสูงสุด"
                            else "Emergency booking — highest priority in queue",
                            fontWeight = FontWeight.Bold,
                            color = Error
                        )
                    }
                }
            }

            // Status hero
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(statusHero.bg), contentAlignment = Alignment.Center) {
                        Icon(statusHero.icon, null, tint = statusHero.fg, modifier = Modifier.size(36.dp))
                    }
                    Text(statusHero.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("#NR-${appt.id.take(8).uppercase()}", style = MaterialTheme.typography.labelMedium, color = Outline)
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                        Text(bookingType.label(s.isThai), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Doctor + date/time
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DoctorAvatar(ui.photoUrl ?: ui.doctorProfile?.clinicPhotoUrl, size = 72.dp)
                        Column {
                            Text(
                                ui.profile?.let { doctorDisplayName(it, s.isThai) } ?: (if (s.isThai) "ไม่ระบุชื่อแพทย์" else "Unnamed doctor"),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                            )
                            ui.doctorProfile?.specialty?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Primary, fontWeight = FontWeight.SemiBold)
                            }
                            ui.doctorProfile?.clinicName?.takeIf { it.isNotBlank() }?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.LocalHospital, null, tint = Outline, modifier = Modifier.size(14.dp))
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = SurfaceContainer)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DateTimeColumn(
                            icon = Icons.Filled.CalendarMonth,
                            label = if (s.isThai) "วันที่" else "Date",
                            value = formatApptDate(appt.scheduledAt, s.isThai)
                        )
                        VerticalDivider(Modifier.height(48.dp), color = SurfaceContainer)
                        DateTimeColumn(
                            icon = Icons.Filled.Schedule,
                            label = if (s.isThai) "เวลา" else "Time",
                            value = formatApptTime(appt.scheduledAt, s.isThai)
                        )
                    }
                }
            }

            // Reason
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.MedicalInformation, null, tint = Primary)
                        Text(if (s.isThai) "เหตุผลในการเข้ารับบริการ" else "Reason for visit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerLow) {
                        Text(displayReason.ifBlank { "—" }, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            rejectionMessage?.let { message ->
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Error.copy(0.08f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (s.isThai) "เหตุผลที่ไม่อนุมัติ" else "Reason for rejection", fontWeight = FontWeight.Bold, color = Error)
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            ui.doctorProfile?.clinicAddress?.takeIf { it.isNotBlank() }?.let { address ->
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.LocationOn, null, tint = Primary)
                            Text(if (s.isThai) "สถานที่ตั้งคลินิก" else "Clinic location", fontWeight = FontWeight.Bold)
                        }
                        Text(address, style = MaterialTheme.typography.bodyMedium, color = Outline)
                        Button(
                            onClick = {
                                val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Directions, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.isThai) "เปิดแผนที่" else "Directions")
                        }
                    }
                }
            }

            // AI tips
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary)
                        Text(if (s.isThai) "คำแนะนำจาก NongRak AI" else "Tips from NongRak AI", color = Primary, fontWeight = FontWeight.Bold)
                    }
                    aiTip(s.isThai, if (s.isThai) "เตรียมสมุดบันทึกสุขภาพเด็ก (เล่มสีชมพู) มาด้วยทุกครั้ง" else "Bring the child's health record (pink book) every visit.")
                    aiTip(s.isThai, if (s.isThai) "มาถึงก่อนเวลานัด 15 นาที เพื่อชั่งน้ำหนักและวัดส่วนสูง" else "Arrive 15 minutes early for weight and height.")
                    if (bookingType == AppointmentBookingType.VACCINE) {
                        aiTip(s.isThai, if (s.isThai) "หลังฉีดวัคซีน สังเกตอาการ 24 ชม. แรก" else "Watch for reactions for 24 hours after vaccination.")
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val startMillis = runCatching { Instant.parse(appt.scheduledAt).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60 * 60 * 1000)
                        putExtra(CalendarContract.Events.TITLE, (if (s.isThai) "นัดหมายแพทย์" else "Doctor appointment") + (ui.doctorProfile?.clinicName?.let { " - $it" } ?: ""))
                        ui.doctorProfile?.clinicAddress?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                    }
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.EventAvailable, null, tint = Primary)
                Spacer(Modifier.width(8.dp))
                Text(if (s.isThai) "บันทึกลงปฏิทิน" else "Add to calendar", color = Primary, fontWeight = FontWeight.Bold)
            }

            if (appt.status == "completed") {
                Button(onClick = { navController.navigate(Screen.LabResults.route) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (s.isThai) "ดูผลตรวจ" else "Lab results", fontWeight = FontWeight.Bold)
                }
            }

            if (appt.status != "cancelled" && appt.status != "completed") {
                Button(
                    onClick = { cancelReason = ""; showCancelDialog = true },
                    enabled = !ui.cancelling,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer, contentColor = Error)
                ) {
                    Icon(Icons.Filled.Cancel, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "ยกเลิกการนัดหมาย" else "Cancel appointment", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { if (!ui.cancelling) showCancelDialog = false },
            title = { Text(if (s.isThai) "ยกเลิกการนัดหมาย" else "Cancel appointment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (s.isThai) "กรุณาระบุเหตุผลในการยกเลิก (จำเป็น)" else "Please provide a reason (required).")
                    OutlinedTextField(value = cancelReason, onValueChange = { cancelReason = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !ui.cancelling)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.cancel(appointmentId, cancelReason) { showCancelDialog = false; cancelReason = "" } }, enabled = cancelReason.isNotBlank() && !ui.cancelling) {
                    Text(if (s.isThai) "ยืนยันยกเลิก" else "Confirm", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }, enabled = !ui.cancelling) {
                    Text(if (s.isThai) "ไม่ยกเลิก" else "Keep")
                }
            }
        )
    }
}

private data class StatusHero(val icon: androidx.compose.ui.graphics.vector.ImageVector, val bg: Color, val fg: Color, val title: String)

private fun statusHeroContent(status: String, notes: String?, isThai: Boolean): StatusHero = when (status) {
    "confirmed" -> StatusHero(Icons.Filled.CheckCircle, HealthGood.copy(0.12f), HealthGood, if (isThai) "ยืนยันการนัดหมายแล้ว" else "Appointment confirmed")
    "completed" -> StatusHero(Icons.Filled.TaskAlt, HealthGood.copy(0.12f), HealthGood, if (isThai) "เสร็จสิ้นการตรวจ" else "Visit completed")
    "cancelled" -> {
        val rejected = notes?.startsWith("Rejected:", ignoreCase = true) == true
        StatusHero(Icons.Filled.Cancel, Error.copy(0.12f), Error, when {
            rejected && isThai -> "ไม่อนุมัติโดยแพทย์"
            rejected -> "Rejected by doctor"
            isThai -> "ยกเลิกแล้ว"
            else -> "Cancelled"
        })
    }
    else -> StatusHero(Icons.Filled.PendingActions, Primary.copy(0.12f), Primary, if (isThai) "รอยืนยันจากคุณหมอ" else "Awaiting doctor confirmation")
}

private fun formatApptDate(iso: String?, isThai: Boolean): String {
    if (iso.isNullOrBlank()) return if (isThai) "รอกำหนด" else "TBD"
    return runCatching {
        val local = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
        formatBirthDateDisplay(local.toString(), isThai)
    }.getOrDefault(iso.take(10))
}

private fun formatApptTime(iso: String?, isThai: Boolean): String {
    if (iso.isNullOrBlank()) return if (isThai) "รอกำหนด" else "TBD"
    return runCatching {
        val local = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalTime()
        val t = local.format(DateTimeFormatter.ofPattern("HH:mm"))
        if (isThai) "$t น." else t
    }.getOrDefault("—")
}

@Composable
private fun DateTimeColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun aiTip(isThai: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Primary, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Outline)
    }
}

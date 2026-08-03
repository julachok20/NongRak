@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.DoctorViewModel
import java.time.LocalTime

private data class DayDef(val dayOfWeek: Int, val th: String, val en: String)

// Displayed Monday-first per the Stitch design's "M-T-W-T-F-S-S" row order; day_of_week in the
// DB is 0=Sunday..6=Saturday (see DoctorAvailability), so Sunday is listed last here.
private val WEEK_DAYS = listOf(
    DayDef(1, "วันจันทร์", "Monday"),
    DayDef(2, "วันอังคาร", "Tuesday"),
    DayDef(3, "วันพุธ", "Wednesday"),
    DayDef(4, "วันพฤหัสบดี", "Thursday"),
    DayDef(5, "วันศุกร์", "Friday"),
    DayDef(6, "วันเสาร์", "Saturday"),
    DayDef(0, "วันอาทิตย์", "Sunday")
)

/**
 * "Weekly Hours" standalone page, reached from Edit Profile's "เวลาทำการ" row. Backs onto
 * doctor_availability (getAvailability/setAvailability).
 *
 * The one-off "Special Days Off" section that used to be faked here now lives for real in
 * [DoctorSpecialDatesScreen], backed by the doctor_special_dates table.
 */
@Composable
fun DoctorWeeklyHoursScreen(
    authViewModel: AuthViewModel,
    doctorViewModel: DoctorViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val isThai = com.lullabyai.app.ui.localization.LocalStrings.current.isThai

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "จัดการเวลาทำงาน" else "Manage Work Hours", onBack = onBack, navController = navController, isDoctor = true) },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            DoctorWeeklyHoursSection(authViewModel, doctorViewModel, isThai)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The "เวลาทำการรายสัปดาห์" card plus its own save button and time picker. Shared by
 * [DoctorWeeklyHoursScreen] and [DoctorSpecialDatesScreen] — the Stitch "Special Dates & Hours
 * Management" screen shows both this and the special-dates card on one page, so it is extracted
 * rather than duplicated. Caller supplies the surrounding scroll container.
 */
@Composable
internal fun DoctorWeeklyHoursSection(
    authViewModel: AuthViewModel,
    doctorViewModel: DoctorViewModel,
    isThai: Boolean
) {
    val doctorState by doctorViewModel.uiState.collectAsState()
    val doctorId = authViewModel.getCurrentUserId()

    LaunchedEffect(Unit) { doctorId?.let { doctorViewModel.loadAvailability(it) } }

    val isOffMap = remember { mutableStateMapOf<Int, Boolean>() }
    val startMap = remember { mutableStateMapOf<Int, LocalTime?>() }
    val endMap = remember { mutableStateMapOf<Int, LocalTime?>() }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(doctorState.availability) {
        if (!seeded && doctorState.availability.isNotEmpty()) {
            doctorState.availability.forEach { a ->
                isOffMap[a.dayOfWeek] = a.isDayOff
                startMap[a.dayOfWeek] = a.startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                endMap[a.dayOfWeek] = a.endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            }
            seeded = true
        }
    }

    var timePickerFor by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // dayOfWeek to isStart

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Schedule, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(if (isThai) "เวลาทำการรายสัปดาห์" else "Weekly working hours", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                if (isThai) "ตั้งค่าวันและเวลาเปิดให้บริการ" else "Set business days and hours",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WEEK_DAYS.forEach { day ->
                val isOff = isOffMap[day.dayOfWeek] ?: false
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isThai) day.th else day.en, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (isOff) (if (isThai) "ปิดทำการ" else "Closed") else (if (isThai) "เปิดทำการ" else "Open"),
                                style = MaterialTheme.typography.labelMedium, color = if (isOff) Outline else Color(0xFF2E7D32)
                            )
                            Switch(
                                checked = !isOff, onCheckedChange = { isOffMap[day.dayOfWeek] = !it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                            )
                        }
                    }
                    if (!isOff) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeField(
                                label = if (isThai) "เวลาเปิด" else "Start", time = startMap[day.dayOfWeek],
                                modifier = Modifier.weight(1f)
                            ) { timePickerFor = day.dayOfWeek to true }
                            TimeField(
                                label = if (isThai) "เวลาปิด" else "End", time = endMap[day.dayOfWeek],
                                modifier = Modifier.weight(1f)
                            ) { timePickerFor = day.dayOfWeek to false }
                        }
                    }
                }
            }
        }
    }

    doctorState.error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

    // Confirmation is the whole point of this button: without it the doctor pressed Save, saw nothing
    // change, and reasonably concluded nothing had been recorded.
    var savedNotice by remember { mutableStateOf(false) }
    LaunchedEffect(savedNotice) {
        if (savedNotice) { kotlinx.coroutines.delay(2500); savedNotice = false }
    }

    PrimaryButton(
        text = if (isThai) "บันทึกการตั้งค่าทั้งหมด" else "Save all settings",
        isLoading = doctorState.isLoading,
        onClick = {
            doctorId?.let { id ->
                doctorViewModel.saveWeeklyAvailability(
                    doctorId = id,
                    days = WEEK_DAYS.map { day ->
                        val isOff = isOffMap[day.dayOfWeek] ?: false
                        DoctorViewModel.WeeklyAvailabilityInput(
                            dayOfWeek = day.dayOfWeek,
                            startTime = if (isOff) null else startMap[day.dayOfWeek]?.toString(),
                            endTime = if (isOff) null else endMap[day.dayOfWeek]?.toString(),
                            isDayOff = isOff
                        )
                    },
                    onSaved = { savedNotice = true }
                )
            }
        }
    )

    if (savedNotice) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (isThai) "บันทึกเวลาทำการแล้ว" else "Working hours saved",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )
        }
    }

    val pending = timePickerFor
    if (pending != null) {
        val (dayOfWeek, isStart) = pending
        com.lullabyai.app.ui.components.TimeSelectDialog(
            initial = if (isStart) startMap[dayOfWeek] else endMap[dayOfWeek],
            isThai = isThai,
            title = if (isStart) {
                if (isThai) "เลือกเวลาเปิด" else "Select opening time"
            } else {
                if (isThai) "เลือกเวลาปิด" else "Select closing time"
            },
            onDismiss = { timePickerFor = null },
            onConfirm = { picked ->
                if (isStart) startMap[dayOfWeek] = picked else endMap[dayOfWeek] = picked
                timePickerFor = null
            }
        )
    }
}

@Composable
internal fun TimeField(label: String, time: LocalTime?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AccessTime, null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "--:--", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

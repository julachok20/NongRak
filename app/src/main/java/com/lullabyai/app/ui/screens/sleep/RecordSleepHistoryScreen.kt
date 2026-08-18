@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Route constant — registered by AppNavigation (screens don't touch the nav graph). */
const val RECORD_SLEEP_HISTORY_ROUTE = "record_sleep_history"

/**
 * Stitch "Record Sleep History (บันทึกการนอนย้อนหลัง)"
 *
 * Rules:
 * 1. For cross-midnight/cross-date sleep (e.g. 20:00 -> 06:30), the main selected date is the WAKE-UP date (end sleep).
 * 2. Start time and end time are saved strictly as the user-selected times (not the time of recording).
 * 3. Time Picker UI with interactive clock selector is used instead of typing.
 */
@Composable
fun RecordSleepHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()

    var sleepType by remember { mutableStateOf("main") }
    // Main date = Wake up / End sleep date (e.g. today or yesterday morning)
    var recordDate by remember { mutableStateOf(LocalDate.now()) }
    var startHour by remember { mutableIntStateOf(20) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(6) }
    var endMinute by remember { mutableIntStateOf(30) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val startMinutesTotal = startHour * 60 + startMinute
    val endMinutesTotal = endHour * 60 + endMinute

    // Check if sleep crosses midnight
    val isCrossMidnight = endMinutesTotal <= startMinutesTotal

    // Compute effective dates
    val actualStartDate = if (isCrossMidnight) recordDate.minusDays(1) else recordDate
    val actualEndDate = recordDate

    val durationMinutes = remember(startMinutesTotal, endMinutesTotal) {
        val diff = endMinutesTotal - startMinutesTotal
        if (diff <= 0) diff + 24 * 60 else diff
    }

    val startTimeFormatted = "%02d:%02d".format(startHour, startMinute)
    val endTimeFormatted = "%02d:%02d".format(endHour, endMinute)

    // Date Picker Dialog
    if (showDatePicker) {
        val initial = recordDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        recordDate = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") }
            }
        ) { DatePicker(state = dateState) }
    }

    // Start Time Picker Dialog
    if (showStartTimePicker) {
        SleepTimePickerDialog(
            title = if (isThai) "เลือกเวลาเริ่มนอน" else "Select Bedtime",
            initialHour = startHour,
            initialMinute = startMinute,
            isThai = isThai,
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                showStartTimePicker = false
                formError = null
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    // End Time Picker Dialog
    if (showEndTimePicker) {
        SleepTimePickerDialog(
            title = if (isThai) "เลือกเวลาตื่นนอน" else "Select Wake-up Time",
            initialHour = endHour,
            initialMinute = endMinute,
            isThai = isThai,
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                showEndTimePicker = false
                formError = null
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "บันทึกการนอนย้อนหลัง" else "Record Sleep History",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
                onHome = {}, onChatbot = {}, onChild = {}, onStats = {}, onSettings = {}
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // --- วันที่ตื่นนอน (Main Reference Date) ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(if (isThai) "วันที่บันทึก (วันที่ตื่นนอน)" else "Record Date (Wake-up Date)")
                Surface(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, OutlineVariant.copy(0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 15.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            recordDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ExpandMore, null, tint = Outline)
                    }
                }
            }

            // --- ประเภทการนอน ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(if (isThai) "ประเภทการนอน" else "Sleep Type")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepTypeCard(
                        icon = Icons.Filled.Bedtime,
                        label = if (isThai) "เวลานอนหลัก" else "Main sleep",
                        selected = sleepType == "main",
                        modifier = Modifier.weight(1f)
                    ) { sleepType = "main" }
                    SleepTypeCard(
                        icon = Icons.Filled.WbSunny,
                        label = if (isThai) "นอนกลางวัน" else "Nap",
                        selected = sleepType == "nap",
                        modifier = Modifier.weight(1f)
                    ) { sleepType = "nap" }
                }
            }

            // --- เวลาเริ่ม / เวลาจบ (Time Picker UI) ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(if (isThai) "ช่วงเวลาการนอน" else "Sleep Time Interval")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePickerCard(
                        icon = Icons.Filled.Bedtime,
                        label = if (isThai) "เวลาเริ่มนอน" else "Bedtime",
                        timeText = startTimeFormatted,
                        subDateText = actualStartDate.format(DateTimeFormatter.ofPattern("d MMM")),
                        modifier = Modifier.weight(1f),
                        onClick = { showStartTimePicker = true }
                    )
                    TimePickerCard(
                        icon = Icons.Filled.WbSunny,
                        label = if (isThai) "เวลาตื่นนอน" else "Wake Up",
                        timeText = endTimeFormatted,
                        subDateText = actualEndDate.format(DateTimeFormatter.ofPattern("d MMM")),
                        modifier = Modifier.weight(1f),
                        onClick = { showEndTimePicker = true }
                    )
                }
            }

            // --- Cross-Midnight Notice & Calculation Card ---
            if (isCrossMidnight) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.NightlightRound, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (isThai) "รอบการนอนข้ามคืน (Overnight Sleep)" else "Overnight Sleep Session",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                        Text(
                            text = if (isThai)
                                "• เข้านอน: ${actualStartDate.format(DateTimeFormatter.ofPattern("d MMM"))} เวลา $startTimeFormatted น.\n• ตื่นนอน: ${actualEndDate.format(DateTimeFormatter.ofPattern("d MMM"))} เวลา $endTimeFormatted น.\n• การนอนข้ามคืนจะถูกนับเป็นยอดการนอนของวันที่ตื่น (${actualEndDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))})"
                            else
                                "• Bedtime: ${actualStartDate.format(DateTimeFormatter.ofPattern("d MMM"))} at $startTimeFormatted\n• Wake up: ${actualEndDate.format(DateTimeFormatter.ofPattern("d MMM"))} at $endTimeFormatted\n• Counted towards wake-up date (${actualEndDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.WbSunny, null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                        Text(
                            text = if (isThai)
                                "รอบการนอนในวันเดียวกัน: ${recordDate.format(DateTimeFormatter.ofPattern("d MMM"))} ($startTimeFormatted - $endTimeFormatted น.)"
                            else
                                "Same-day sleep session: ${recordDate.format(DateTimeFormatter.ofPattern("d MMM"))} ($startTimeFormatted - $endTimeFormatted)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- ระยะเวลาทั้งหมด ---
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Timelapse, null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        if (isThai) "ระยะเวลาทั้งหมด" else "Total duration",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (isThai) "${durationMinutes / 60} ชม. ${durationMinutes % 60} นาที"
                        else "${durationMinutes / 60}h ${durationMinutes % 60}m",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            // --- AI note ---
            Surface(shape = RoundedCornerShape(14.dp), color = Secondary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    Text(
                        if (isThai)
                            "การบันทึกข้อมูลย้อนหลังที่ถูกต้อง จะช่วยให้ AI แนะนำตารางนอนในวันถัดไปได้แม่นยำยิ่งขึ้น"
                        else
                            "Accurate back-filled records help the AI suggest a more precise schedule for tomorrow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            formError?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            // --- Save Button ---
            Button(
                onClick = {
                    if (state.child == null) {
                        formError = if (isThai) "ยังไม่ได้เลือกข้อมูลลูก" else "No child selected"
                        return@Button
                    }
                    // Exact start & end ISO strings from user-filled times
                    val startIso = "${actualStartDate}T$startTimeFormatted:00"
                    val endIso = "${actualEndDate}T$endTimeFormatted:00"
                    childViewModel.addManualSleepLog(sleepType, startIso, endIso)
                    onBack()
                },
                enabled = !state.sleepSaving,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.sleepSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกประวัติการนอน" else "Save sleep history", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SleepTypeCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, Primary) else BorderStroke(1.dp, OutlineVariant.copy(0.4f)),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = if (selected) Primary else Outline, modifier = Modifier.size(26.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimePickerCard(
    icon: ImageVector,
    label: String,
    timeText: String,
    subDateText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OutlineVariant.copy(0.4f)),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subDateText,
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    isThai: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) {
                Text(if (isThai) "ตกลง" else "OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isThai) "ยกเลิก" else "Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

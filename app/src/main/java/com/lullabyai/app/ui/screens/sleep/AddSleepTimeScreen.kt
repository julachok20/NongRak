@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

/** Weekday codes as stored in `sleep_schedules.days_of_week`, in the order the design lays the
 *  chips out (Monday first). `ALL` is persisted instead when every day is selected. */
private val DAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

private fun dayLabels(isThai: Boolean) =
    if (isThai) listOf("จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.", "อา.")
    else listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Stitch "Add Sleep Time (เพิ่มเวลาการนอน)" — reached from Child Sleep Schedule's add/edit.
 *
 * This writes a **schedule** (`sleep_schedules`), not a sleep log. The design settles it: it has a
 * ทำซ้ำทุกวัน weekday repeat selector and its button reads บันทึกตารางการนอน. A recurring weekly
 * pattern is a plan. Recording sleep that actually happened is [RecordSleepHistoryScreen], whose
 * button reads บันทึกประวัติการนอน instead.
 *
 * Passing [scheduleId] prefills an existing row and turns the save into an update; the design draws
 * one screen for both, so there is no separate edit layout.
 */
@Composable
fun AddSleepTimeScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    scheduleId: String? = null,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()

    val existing = remember(scheduleId, state.sleepSchedules) {
        scheduleId?.let { id -> state.sleepSchedules.find { it.id == id } }
    }

    LaunchedEffect(state.child?.id, state.sleepLogs.size) {
        state.child?.let { chatViewModel.loadSleepInsight(it, state.sleepLogs, s.isThai) }
    }

    var sleepType by remember { mutableStateOf("main") }
    var startTime by remember { mutableStateOf("20:00") }
    var endTime by remember { mutableStateOf("06:00") }
    var days by remember { mutableStateOf(DAY_CODES.toSet()) }

    // Prefill lands once the schedule list arrives, which can be after first composition.
    LaunchedEffect(existing?.id) {
        existing?.let {
            sleepType = it.sleepType
            startTime = it.startTime
            endTime = it.endTime
            days = if (it.daysOfWeek.equals("ALL", true)) DAY_CODES.toSet()
            else it.daysOfWeek.split(",").map { d -> d.trim().uppercase() }.filter { d -> d in DAY_CODES }.toSet()
        }
    }

    val durationMinutes = remember(startTime, endTime) { minutesBetween(startTime, endTime) }
    val canSave = durationMinutes != null && days.isNotEmpty() && !state.sleepSaving

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "เพิ่มเวลาการนอน" else "Add Sleep Time",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                if (s.isThai) "ประเภทการนอน" else "Sleep Type",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SleepTypeButton(Icons.Filled.Bedtime, if (s.isThai) "เวลานอนหลัก" else "Main bedtime", sleepType == "main", Modifier.weight(1f)) { sleepType = "main" }
                SleepTypeButton(Icons.Filled.Flare, if (s.isThai) "นอนกลางวัน" else "Nap", sleepType == "nap", Modifier.weight(1f)) { sleepType = "nap" }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(if (s.isThai) "เวลาเริ่ม" else "Start", Icons.Filled.Login, startTime, Modifier.weight(1f)) { startTime = it }
                TimeField(if (s.isThai) "เวลาจบ" else "End", Icons.Filled.Logout, endTime, Modifier.weight(1f)) { endTime = it }
            }

            if (durationMinutes != null) DurationRing(durationMinutes, s.isThai)

            RepeatDaysPicker(days, s.isThai) { code ->
                days = if (code in days) days - code else days + code
            }

            com.lullabyai.app.ui.components.AIInsightCard(
                text = chatState.sleepInsight ?: "",
                isLoading = chatState.sleepInsight == null
            )

            Button(
                onClick = {
                    val label = if (sleepType == "nap") {
                        if (s.isThai) "นอนกลางวัน" else "Nap"
                    } else {
                        if (s.isThai) "เวลานอนหลัก" else "Main bedtime"
                    }
                    val encodedDays = if (days.size == DAY_CODES.size) "ALL"
                    else DAY_CODES.filter { it in days }.joinToString(",")
                    if (existing != null) {
                        childViewModel.updateSleepSchedule(existing.id, label, sleepType, startTime, endTime, encodedDays) { onBack() }
                    } else {
                        childViewModel.addSleepSchedule(label, sleepType, startTime, endTime, encodedDays) { onBack() }
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.sleepSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.Save, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.isThai) "บันทึกตารางการนอน" else "Save sleep schedule",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "HH:mm" pair → elapsed minutes, wrapping past midnight (night sleep ends the next morning). */
internal fun minutesBetween(start: String, end: String): Int? {
    fun toMin(t: String): Int? {
        val p = t.split(":")
        if (p.size != 2) return null
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = p[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
    val a = toMin(start) ?: return null
    val b = toMin(end) ?: return null
    val diff = b - a
    return if (diff <= 0) diff + 24 * 60 else diff
}

/** Stitch "ทำซ้ำทุกวัน" — seven toggleable weekday chips. */
@Composable
private fun RepeatDaysPicker(selected: Set<String>, isThai: Boolean, onToggle: (String) -> Unit) {
    val labels = dayLabels(isThai)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Bedtime, null, tint = Primary, modifier = Modifier.size(18.dp))
            Text(
                if (isThai) "ทำซ้ำทุกวัน" else "Repeat daily",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DAY_CODES.forEachIndexed { i, code ->
                val on = code in selected
                Surface(
                    onClick = { onToggle(code) },
                    modifier = Modifier.weight(1f).height(44.dp).clip(CircleShape),
                    shape = CircleShape,
                    color = if (on) Primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            labels[i],
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Circular visualization of the entered duration, capped visually at 16h (covers realistic
 *  nightly-sleep + nap durations) — purely computed from the two time fields, no backend. */
@Composable
private fun DurationRing(durationMinutes: Int, isThai: Boolean) {
    val maxMinutes = 16 * 60
    val fraction = (durationMinutes / maxMinutes.toFloat()).coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = Color(0xFFE0E3E5), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2))
            drawArc(color = Primary, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isThai) "ระยะเวลาทั้งหมด" else "Total duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${durationMinutes / 60}${if (isThai) " ชม. " else "h "}${durationMinutes % 60}${if (isThai) " นาที" else "m"}",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary
            )
        }
    }
}

@Composable
private fun SleepTypeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (selected) Color.White else Outline, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("HH:mm", color = Outline) }, singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

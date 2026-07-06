@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.background
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
import androidx.navigation.NavController
import com.lullabyai.app.data.model.SleepSchedule
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

private val DAY_KEYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private fun dayLabel(key: String, isThai: Boolean) = if (isThai) {
    mapOf("MON" to "จ.", "TUE" to "อ.", "WED" to "พ.", "THU" to "พฤ.", "FRI" to "ศ.", "SAT" to "ส.", "SUN" to "อา.")[key] ?: key
} else key.take(1)

/** "HH:mm" start/end -> duration in minutes, handling schedules that cross midnight. */
private fun scheduleDurationMinutes(start: String, end: String): Int {
    fun toMin(t: String): Int? {
        val p = t.split(":"); if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) * 60 + (p[1].toIntOrNull() ?: return null)
    }
    val s0 = toMin(start) ?: return 0
    val e0 = toMin(end) ?: return 0
    val diff = e0 - s0
    return if (diff <= 0) diff + 24 * 60 else diff
}

@Composable
fun SleepScheduleScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val schedules = state.sleepSchedules

    LaunchedEffect(state.child?.id) { childViewModel.loadSleep() }

    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<SleepSchedule?>(null) }
    var recommendationDismissed by remember { mutableStateOf(false) }
    var prefillNap by remember { mutableStateOf(false) }

    val targetHours = childViewModel.sleepTargetHours()
    val scheduledMinutes = schedules.sumOf { scheduleDurationMinutes(it.startTime, it.endTime) }
    val targetPct = (scheduledMinutes * 100 / (targetHours * 60)).coerceIn(0, 999)
    // Recurring schedule templates and actually-tracked logs (from the Sleep Tracker /
    // Add Sleep Time screens) are two separate data sources — a parent who's been logging
    // real naps but never added a recurring nap *schedule* was still told "you have no nap"
    // forever, since this only checked `schedules`. Checking recent logs too keeps the
    // recommendation honest about what's actually been recorded.
    val recentNapLogged = state.sleepLogs.any { log ->
        log.sleepType == "nap" &&
            com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)
                ?.isAfter(java.time.Instant.now().minus(java.time.Duration.ofDays(7))) == true
    }
    val showNapRecommendation = !recommendationDismissed && scheduledMinutes < targetHours * 60 &&
        schedules.none { it.sleepType == "nap" } && !recentNapLogged

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ตารางการนอน" else "Sleep Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (showNapRecommendation) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text(if (s.isThai) "คำแนะนำ" else "Recommendation", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                if (s.isThai) "NongRak AI แนะนำเพิ่มเวลานอนกลางวันตามช่วงวัย เป้าหมายรวม $targetHours ชม./วัน"
                                else "NongRak AI recommends adding a nap for this age. Daily target: $targetHours h/day",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { prefillNap = true; showAdd = true }, shape = RoundedCornerShape(100.dp)) { Text(if (s.isThai) "เพิ่ม" else "Add") }
                                OutlinedButton(onClick = { recommendationDismissed = true }, shape = RoundedCornerShape(100.dp)) { Text(if (s.isThai) "ข้าม" else "Skip") }
                            }
                        }
                    }
                }
            }

            if (schedules.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ยังไม่มีตารางการนอน" else "No sleep schedule yet", color = Outline) } }
            } else {
                items(schedules, key = { it.id }) { sc -> ScheduleRow(sc, onDelete = { toDelete = sc }) }
            }

            item {
                OutlinedButton(onClick = { prefillNap = false; showAdd = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp)) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (s.isThai) "เพิ่มช่วงเวลานอน" else "Add sleep period")
                }
            }

            if (schedules.isNotEmpty()) {
                item { ScheduleSummaryCard(scheduledMinutes, targetHours, targetPct, s.isThai) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showAdd) {
        AddScheduleDialog(
            initialType = if (prefillNap) "nap" else "main",
            onDismiss = { showAdd = false },
            onAdd = { label, type, start, end, days ->
                childViewModel.addSleepSchedule(label, type, start, end, days)
                showAdd = false
            }
        )
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = { Text(if (s.isThai) "ลบตารางนี้?" else "Delete this schedule?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "ลบ \"${target.label}\" ออกจากตารางการนอนใช่หรือไม่?" else "Remove \"${target.label}\" from the sleep schedule?") },
            confirmButton = { TextButton(onClick = { childViewModel.deleteSleepSchedule(target.id); toDelete = null }) { Text(if (s.isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

@Composable
private fun ScheduleSummaryCard(scheduledMinutes: Int, targetHours: Int, targetPct: Int, isThai: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (isThai) "สรุปเวลานอน" else "Sleep summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    (if (isThai) "เป้าหมาย: " else "Target: ") + "$targetHours " + (if (isThai) "ชม. / วัน" else "h/day"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val inset = stroke.width / 2
                    drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke,
                        size = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width), topLeft = androidx.compose.ui.geometry.Offset(inset, inset))
                    drawArc(color = Primary, startAngle = -90f, sweepAngle = 360f * (targetPct / 100f).coerceIn(0f, 1f), useCenter = false, style = stroke,
                        size = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width), topLeft = androidx.compose.ui.geometry.Offset(inset, inset))
                }
                Text("$targetPct%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
            }
        }
    }
}

@Composable
private fun ScheduleRow(sc: SleepSchedule, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val days = if (sc.daysOfWeek == "ALL") (if (s.isThai) "ทุกวัน" else "ALL DAYS") else sc.daysOfWeek.split(",").joinToString(" ") { dayLabel(it, s.isThai) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(if (sc.sleepType == "nap") Icons.Filled.WbSunny else Icons.Filled.Bedtime, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(sc.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("${sc.startTime} - ${sc.endTime} • $days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun AddScheduleDialog(initialType: String = "main", onDismiss: () -> Unit, onAdd: (String, String, String, String, String) -> Unit) {
    val s = LocalStrings.current
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var start by remember { mutableStateOf("20:00") }
    var end by remember { mutableStateOf("06:00") }
    var selectedDays by remember { mutableStateOf(DAY_KEYS.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.isThai) "เพิ่มช่วงเวลานอน" else "Add sleep period", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(if (s.isThai) "ชื่อช่วงเวลา" else "Label") }, placeholder = { Text(if (s.isThai) "เช่น เวลานอนคืนนี้, งีบที่ 1" else "e.g. Bedtime, Nap 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("main" to (if (s.isThai) "นอนหลัก" else "Bedtime"), "nap" to (if (s.isThai) "งีบ" else "Nap")).forEach { (key, lbl) ->
                        val sel = type == key
                        Surface(onClick = { type = key }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = if (sel) Primary else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(lbl, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text(if (s.isThai) "เวลานอน" else "Bedtime") }, placeholder = { Text("HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text(if (s.isThai) "เวลาตื่น" else "Wake up") }, placeholder = { Text("HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text(if (s.isThai) "วันที่ทำซ้ำ" else "Repeat on", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_KEYS.forEach { day ->
                        val sel = day in selectedDays
                        Surface(
                            onClick = { selectedDays = if (sel) selectedDays - day else selectedDays + day },
                            shape = RoundedCornerShape(100.dp), color = if (sel) Primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(dayLabel(day, s.isThai), modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isNotBlank()) {
                    val days = if (selectedDays.size == 7) "ALL" else DAY_KEYS.filter { it in selectedDays }.joinToString(",")
                    onAdd(label.trim(), type, start, end, days)
                }
            }) { Text(if (s.isThai) "บันทึกตาราง" else "Save", color = Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
    )
}

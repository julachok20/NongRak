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
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun SleepTrackerScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val active = state.activeSleepSession
    val logs = state.sleepLogs

    LaunchedEffect(state.child?.id) { childViewModel.loadSleep() }

    // Tick every 30s so the elapsed-time display stays live while a session is active.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(active?.id) {
        while (active != null) { kotlinx.coroutines.delay(30_000); tick++ }
    }

    val elapsed = active?.let { runCatching { Duration.between(LocalDateTime.parse(it.startTime), LocalDateTime.now()) }.getOrNull() }
    val today = LocalDateTime.now().toLocalDate()
    val todayLogs = logs.filter { runCatching { LocalDateTime.parse(it.startTime).toLocalDate() == today }.getOrDefault(false) }
    val totalMinutesToday = todayLogs.sumOf { log ->
        val start = runCatching { LocalDateTime.parse(log.startTime) }.getOrNull()
        val end = log.endTime?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now()
        if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ติดตามการนอน" else "Sleep Tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // One-tap start/stop
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(96.dp).clip(CircleShape).background(if (active != null) Primary else Primary.copy(0.1f))
                                .then(Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { childViewModel.toggleSleepSession() }, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    if (active != null) Icons.Filled.Hotel else Icons.Filled.Bedtime,
                                    null, tint = if (active != null) Color.White else Primary, modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Text(
                            if (active != null) (if (s.isThai) "กำลังหลับ" else "Sleeping") else (if (s.isThai) "ตื่นอยู่" else "Awake"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        if (active != null && elapsed != null) {
                            Text("${elapsed.toHours()}${if (s.isThai) " ชม. " else "h "}${elapsed.toMinutes() % 60}${if (s.isThai) " นาที" else "m"}", style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Text(if (s.isThai) "แตะเพื่อเริ่ม/หยุดบันทึกการนอน" else "Tap to start/stop tracking sleep", style = MaterialTheme.typography.labelMedium, color = Outline)
                    }
                }
            }

            // Daily totals
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepStat(if (s.isThai) "เวลานอนรวมวันนี้" else "Total today", "${totalMinutesToday / 60}${if (s.isThai) " ชม. " else "h "}${totalMinutesToday % 60}${if (s.isThai) " น." else "m"}", Modifier.weight(1f))
                    SleepStat(if (s.isThai) "จำนวนครั้ง" else "Sessions", "${todayLogs.size}", Modifier.weight(1f))
                }
            }

            // Quick actions
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { navController.navigate(Screen.AddSleepTime.route) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (s.isThai) "บันทึกเอง" else "Log manually", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = { navController.navigate(Screen.SleepSchedule.route) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                        Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (s.isThai) "ตารางการนอน" else "Schedule", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                TextButton(onClick = { navController.navigate(Screen.SleepGuidance.route) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Lightbulb, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (s.isThai) "คำแนะนำการนอน" else "Sleep guidance", color = Primary, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Recent sessions
            item { Text(if (s.isThai) "บันทึกล่าสุด" else "Recent sessions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            if (logs.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ยังไม่มีบันทึกการนอน" else "No sleep logs yet", color = Outline) } }
            } else {
                items(logs.take(15), key = { it.id }) { log -> SleepLogRow(log, onDelete = { childViewModel.deleteSleepLog(log.id) }) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SleepStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SleepLogRow(log: SleepLog, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val start = runCatching { LocalDateTime.parse(log.startTime) }.getOrNull()
    val end = log.endTime?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    val durationText = if (start != null) {
        val dur = Duration.between(start, end ?: LocalDateTime.now())
        "${dur.toHours()}${if (s.isThai) "ชม." else "h"} ${dur.toMinutes() % 60}${if (s.isThai) "น." else "m"}"
    } else ""
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(if (log.sleepType == "nap") Icons.Filled.WbSunny else Icons.Filled.Bedtime, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (log.sleepType == "nap") (if (s.isThai) "งีบ" else "Nap") else (if (s.isThai) "นอนหลัก" else "Main sleep"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    (start?.toLocalTime()?.toString()?.take(5) ?: "") + " - " + (end?.toLocalTime()?.toString()?.take(5) ?: (if (s.isThai) "กำลังหลับ" else "ongoing")),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (durationText.isNotBlank()) Text(durationText, style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(16.dp)) }
        }
    }
}

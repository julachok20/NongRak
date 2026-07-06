@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.NoiseTrack
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.util.WhiteNoisePlayer
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun SleepTrackerScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val active = state.activeSleepSession
    val logs = state.sleepLogs
    val child = state.child

    LaunchedEffect(state.child?.id) { childViewModel.loadSleep() }
    LaunchedEffect(child, logs.size) { child?.let { chatViewModel.loadSleepInsight(it, logs, s.isThai) } }

    val noisePlayer = remember { WhiteNoisePlayer() }
    DisposableEffect(Unit) { onDispose { noisePlayer.release() } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val moodSavedMsg = if (s.isThai) "บันทึกอารมณ์แล้ว" else "Mood saved"

    // Tick every 30s so the elapsed-time display stays live while a session is active.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(active?.id) {
        if (active == null) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(30_000); tick++ }
    }

    val elapsed = active?.let { TimeUtils.parseInstant(it.startTime)?.let { s -> Duration.between(s, Instant.now()) } }
    val today = LocalDate.now()
    val todayLogs = logs.filter { TimeUtils.parseInstant(it.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate() == today }
    val totalMinutesToday = todayLogs.sumOf { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
        if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
    }
    val targetHours = childViewModel.sleepTargetHours()
    val targetMinutes = targetHours * 60

    // Extended stats over the last 7 days (including today).
    val last7Start = today.minusDays(6)
    val last7Logs = logs.filter { log ->
        val d = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate()
        d != null && !d.isBefore(last7Start) && !d.isAfter(today)
    }
    val weeklyTotalMinutes = last7Logs.sumOf { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
        if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
    }
    val weeklyAvgMinutes = weeklyTotalMinutes / 7
    val daysWithMain = (0..6).count { offset ->
        val day = today.minusDays(offset.toLong())
        last7Logs.any { it.sleepType == "main" && TimeUtils.parseInstant(it.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate() == day }
    }
    val consistencyPct = daysWithMain * 100 / 7
    // Today's mood tag, if any — reflects the REAL saved value (same target-log resolution as
    // ChildViewModel.logMoodForToday), not just "the last tile the user happened to tap".
    val todaysMoodTarget = active ?: todayLogs.maxByOrNull { it.startTime }
    val todaysMood = todaysMoodTarget?.mood

    val lastCompleted = logs.filter { it.endTime != null }.maxByOrNull { it.startTime }
    val lastSleepText = lastCompleted?.let { log ->
        val start = TimeUtils.parseInstant(log.startTime); val end = log.endTime?.let { TimeUtils.parseInstant(it) }
        if (start != null && end != null) {
            val d = Duration.between(start, end)
            "${d.toHours()}${if (s.isThai) "ชม." else "h"} ${d.toMinutes() % 60}${if (s.isThai) "น." else "m"}"
        } else "-"
    } ?: "-"
    val latestWake = logs.filter { it.sleepType == "main" && it.endTime != null }.maxByOrNull { it.startTime }
        ?.let { TimeUtils.localTime(it.endTime) } ?: "-"

    ChildScreenScaffold(
        title = if (s.isThai) "ติดตามการนอน" else "Sleep Tracking",
        currentTab = "sleep",
        navController = navController,
        snackbarHostState = snackbarHostState
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // One-tap start/stop + daily progress toward age-based target
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(96.dp).clip(CircleShape).background(if (active != null) Primary else Primary.copy(0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { childViewModel.toggleSleepSession() }, enabled = !state.sleepSaving, modifier = Modifier.fillMaxSize()) {
                                if (state.sleepSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = if (active != null) Color.White else Primary)
                                } else {
                                    Icon(
                                        if (active != null) Icons.Filled.Hotel else Icons.Filled.Bedtime,
                                        null, tint = if (active != null) Color.White else Primary, modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            if (active != null) (if (s.isThai) "กำลังหลับ" else "Sleeping") else (if (s.isThai) "ตื่นอยู่" else "Awake"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        if (active != null && elapsed != null) {
                            Text("${elapsed.toHours()}${if (s.isThai) " ชม. " else "h "}${elapsed.toMinutes() % 60}${if (s.isThai) " นาที" else "m"}", style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${totalMinutesToday / 60}${if (s.isThai) " ชม. " else "h "}${totalMinutesToday % 60}${if (s.isThai) " นาที" else "m"} / ${if (s.isThai) "เป้าหมาย" else "goal"} $targetHours${if (s.isThai) " ชม." else "h"}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { (totalMinutesToday / targetMinutes.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
                            color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(if (s.isThai) "แตะเพื่อเริ่ม/หยุดบันทึกการนอน" else "Tap to start/stop tracking sleep", style = MaterialTheme.typography.labelMedium, color = Outline)
                    }
                }
            }

            // AI insight
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(if (s.isThai) "ข้อมูลเชิงลึกจาก AI" else "AI Insight", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        com.lullabyai.app.ui.components.AiTextOrTimeout(
                            text = chatState.sleepInsight,
                            timeoutMessage = if (s.isThai) "ไม่สามารถโหลดข้อมูลเพิ่มเติม" else "Unable to load additional info",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 24h timeline
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (s.isThai) "เส้นเวลาการนอน (24 ชม.)" else "Sleep timeline (24h)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        SleepTimelineChart(todayLogs)
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

            // Mood log
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (s.isThai) "เช็กอารมณ์" else "Mood check-in", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    MoodGrid(selectedMood = todaysMood, onSelect = { mood ->
                        childViewModel.logMoodForToday(mood)
                        scope.launch { snackbarHostState.showSnackbar(moodSavedMsg) }
                    })
                }
            }

            // White-noise player
            item { NoisePlayerCard(noisePlayer) }

            // Extended stats
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SleepStat(if (s.isThai) "นอนครั้งล่าสุด" else "Last sleep", lastSleepText, Modifier.weight(1f))
                        SleepStat(if (s.isThai) "เฉลี่ยต่อวัน" else "Avg/day", "${weeklyAvgMinutes / 60}${if (s.isThai) " ชม." else "h"}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SleepStat(if (s.isThai) "ความสม่ำเสมอ" else "Consistency", "$consistencyPct%", Modifier.weight(1f))
                        SleepStat(if (s.isThai) "เวลาตื่นล่าสุด" else "Latest wake", latestWake, Modifier.weight(1f))
                    }
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
private fun SleepTimelineChart(todayLogs: List<SleepLog>, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column {
        Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
            val barTop = size.height * 0.15f
            val barHeight = size.height * 0.7f
            todayLogs.forEach { log ->
                val startInstant = TimeUtils.parseInstant(log.startTime)
                val endInstant = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
                if (startInstant != null) {
                    val startSec = startInstant.atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay()
                    val endSec = endInstant.atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay()
                    val x0 = (startSec / 86400f) * size.width
                    val x1 = (endSec / 86400f) * size.width
                    drawRoundRect(
                        color = Primary,
                        topLeft = Offset(x0, barTop),
                        size = Size((x1 - x0).coerceAtLeast(4f), barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00:00", "06:00", "12:00", "18:00", if (s.isThai) "ตอนนี้" else "now").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
            }
        }
    }
}

@Composable
private fun MoodGrid(selectedMood: String?, onSelect: (String) -> Unit) {
    val s = LocalStrings.current
    val moods = listOf(
        Triple("good", if (s.isThai) "อารมณ์ดี" else "Happy", "😊"),
        Triple("fussy", if (s.isThai) "งอแง" else "Fussy", "😣"),
        Triple("sleepy", if (s.isThai) "ง่วงนอน" else "Sleepy", "😴"),
        Triple("energetic", if (s.isThai) "ร่าเริง" else "Energetic", "🧸")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            moods.take(2).forEach { (key, label, emoji) -> MoodTile(emoji, label, key == selectedMood, Modifier.weight(1f)) { onSelect(key) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            moods.drop(2).forEach { (key, label, emoji) -> MoodTile(emoji, label, key == selectedMood, Modifier.weight(1f)) { onSelect(key) } }
        }
    }
}

@Composable
private fun MoodTile(emoji: String, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary.copy(0.12f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun NoisePlayerCard(player: WhiteNoisePlayer) {
    val s = LocalStrings.current
    val isPlaying by player.isPlaying.collectAsState()
    val elapsed by player.elapsedSeconds.collectAsState()
    val track by player.selectedTrack.collectAsState()
    fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.GraphicEq, null, tint = Color.White)
                Column {
                    Text(if (s.isThai) "เครื่องเล่นเสียงขาว" else "White Noise Player", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (s.isThai) track.labelTh else track.labelEn, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                }
            }
            LinearProgressIndicator(
                progress = { (elapsed / player.durationSeconds.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(100.dp)),
                color = Color.White, trackColor = Color.White.copy(0.2f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(elapsed), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                Text(fmt(player.durationSeconds), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { if (isPlaying) player.pause() else player.play() },
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White)
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.size(28.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoiseTrack.entries.forEach { t ->
                    val selected = t == track
                    Surface(
                        onClick = { player.selectTrack(t) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp),
                        color = if (selected) Color.White else Color.White.copy(0.12f)
                    ) {
                        Text(
                            if (s.isThai) t.labelTh else t.labelEn,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.inverseSurface else Color.White
                        )
                    }
                }
            }
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
    val startInstant = TimeUtils.parseInstant(log.startTime)
    val endInstant = log.endTime?.let { TimeUtils.parseInstant(it) }
    val durationText = if (startInstant != null) {
        val dur = Duration.between(startInstant, endInstant ?: Instant.now())
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
                    (TimeUtils.localTime(log.startTime) ?: "") + " - " + (TimeUtils.localTime(log.endTime) ?: (if (s.isThai) "กำลังหลับ" else "ongoing")),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (durationText.isNotBlank()) Text(durationText, style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(16.dp)) }
        }
    }
}

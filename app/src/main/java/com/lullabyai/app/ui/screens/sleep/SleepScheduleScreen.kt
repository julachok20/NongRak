@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.lullabyai.app.data.model.SleepSchedule
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private val DAY_KEYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private fun dayLabel(key: String, isThai: Boolean) = if (isThai) {
    mapOf("MON" to "จ.", "TUE" to "อ.", "WED" to "พ.", "THU" to "พฤ.", "FRI" to "ศ.", "SAT" to "ส.", "SUN" to "อา.")[key] ?: key
} else key.take(1)

private fun toMinutes(t: String): Int? {
    val p = t.split(":")
    if (p.size != 2) return null
    val h = p[0].trim().toIntOrNull() ?: return null
    val m = p[1].trim().toIntOrNull() ?: return null
    return h * 60 + m
}

/** "HH:mm" start/end -> duration in minutes, handling schedules that cross midnight. */
private fun scheduleDurationMinutes(start: String, end: String): Int {
    val a = toMinutes(start) ?: return 0
    val b = toMinutes(end) ?: return 0
    val diff = b - a
    return if (diff <= 0) diff + 24 * 60 else diff
}

/** The design labels times as "07:00 AM"; storage is 24h "HH:mm". */
private fun to12h(t: String): String {
    val m = toMinutes(t) ?: return t
    val h24 = m / 60
    val suffix = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 % 12 == 0 -> 12
        else -> h24 % 12
    }
    return "%02d:%02d %s".format(h12, m % 60, suffix)
}

private fun durationText(minutes: Int, isThai: Boolean): String {
    val h = minutes / 60
    val mm = minutes % 60
    return when {
        mm == 0 && isThai -> "$h ชม."
        mm == 0 -> "${h}h"
        isThai -> "$h ชม. $mm นาที"
        else -> "${h}h ${mm}m"
    }
}

/** One row of the design's "Active Daily Routine" timeline: the wake window that precedes a
 *  sleep block, plus the block itself. */
private data class RoutineEntry(val schedule: SleepSchedule, val wakeWindowMinutes: Int?, val napIndex: Int?)

/**
 * Stitch "Child Sleep Schedule (ตารางการนอน)" — reached from MAIN's ตาราง tile.
 *
 * Renders the stored `sleep_schedules` rows as the design's day timeline: wake-up, then each sleep
 * block separated by its wake window. Add/edit both open [AddSleepTimeScreen]; the old inline
 * add-dialog is gone because the design routes to that page instead.
 */
@Composable
fun SleepScheduleScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val schedules = state.sleepSchedules
    val canDelete = state.isChildOwner

    LaunchedEffect(state.child?.id) { childViewModel.loadSleep() }

    var toDelete by remember { mutableStateOf<SleepSchedule?>(null) }
    var recommendationDismissed by remember { mutableStateOf(false) }

    val targetHours = childViewModel.sleepTargetHours()
    val scheduledMinutes = schedules.sumOf { scheduleDurationMinutes(it.startTime, it.endTime) }

    // Recurring schedule templates and actually-tracked logs are separate data sources — a parent
    // who logs real naps but never added a recurring nap *schedule* was told "you have no nap"
    // forever when this only checked `schedules`.
    val recentNapLogged = state.sleepLogs.any { log ->
        log.sleepType == "nap" &&
            com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)
                ?.isAfter(Instant.now().minus(Duration.ofDays(7))) == true
    }
    val showNapRecommendation = !recommendationDismissed && scheduledMinutes < targetHours * 60 &&
        schedules.none { it.sleepType == "nap" } && !recentNapLogged

    // Recommended-bedtime prompt re-asks on a cooldown (not one-and-done) until a "main" schedule
    // exists, since the parent may keep declining for good reasons.
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val (recBedStart, recBedEnd) = childViewModel.recommendedBedtime()
    var respondedAt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { settingsManager.sleepRecRespondedAt.collect { respondedAt = it } }
    val cooldownOver = respondedAt == null ||
        runCatching { Duration.between(Instant.parse(respondedAt), Instant.now()) > Duration.ofDays(3) }.getOrDefault(true)
    var showBedtimeReason by remember { mutableStateOf(false) }
    val showBedtimePrompt = cooldownOver && schedules.none { it.sleepType == "main" }

    val night = schedules.firstOrNull { it.sleepType == "main" }
    val wakeUp = night?.endTime

    // Order the day from wake-up: naps in the order they occur, night sleep last.
    val routine = remember(schedules, wakeUp) {
        val anchor = wakeUp?.let { toMinutes(it) } ?: 0
        val ordered = schedules.sortedBy { sc ->
            val start = toMinutes(sc.startTime) ?: 0
            ((start - anchor) + 24 * 60) % (24 * 60)
        }
        var prevEnd = anchor
        var napSeen = 0
        ordered.map { sc ->
            val start = toMinutes(sc.startTime) ?: prevEnd
            val gap = ((start - prevEnd) + 24 * 60) % (24 * 60)
            prevEnd = toMinutes(sc.endTime) ?: start
            val napIdx = if (sc.sleepType == "nap") ++napSeen else null
            RoutineEntry(sc, if (wakeUp != null) gap else null, napIdx)
        }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ตารางการนอน" else "Sleep Schedule",
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // Stitch "บทวิเคราะห์จาก AI / Live Insight" card. The actionable prompts the app
            // already had (bedtime suggestion, missing-nap) render inside it rather than as
            // separate banners, so there is one AI slot like the design shows.
            item {
                AiInsightCard(
                    isThai = s.isThai,
                    body = when {
                        showBedtimePrompt -> if (s.isThai)
                            "ลูกวัยนี้ควรเข้านอนประมาณ $recBedStart น. ถึง $recBedEnd น. เห็นด้วยไหมคะ?"
                        else "For this age, bedtime around $recBedStart–$recBedEnd is recommended. Does this work?"
                        showNapRecommendation -> if (s.isThai)
                            "น้องควรนอนกลางวันเพิ่ม เพื่อให้ครบเป้าหมายรวม $targetHours ชม./วัน"
                        else "Add a nap to reach the daily target of $targetHours h/day"
                        else -> if (s.isThai)
                            "ตารางการนอนสม่ำเสมอช่วยให้น้องหลับได้ลึกขึ้นและตื่นน้อยลงในเวลากลางคืน"
                        else "A consistent sleep schedule helps your baby sleep deeper and wake less at night"
                    },
                    actions = {
                        when {
                            showBedtimePrompt -> {
                                Button(
                                    onClick = {
                                        scope.launch { settingsManager.setSleepRecResponded() }
                                        navController.navigate(Screen.AddSleepTime.create())
                                    },
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                ) { Text(if (s.isThai) "ใช่" else "Yes") }
                                OutlinedButton(onClick = { showBedtimeReason = true }, shape = RoundedCornerShape(100.dp)) {
                                    Text(if (s.isThai) "ไม่ใช่" else "No")
                                }
                            }
                            showNapRecommendation -> {
                                Button(
                                    onClick = { navController.navigate(Screen.AddSleepTime.create()) },
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                ) { Text(if (s.isThai) "เพิ่ม" else "Add") }
                                OutlinedButton(onClick = { recommendationDismissed = true }, shape = RoundedCornerShape(100.dp)) {
                                    Text(if (s.isThai) "ข้าม" else "Skip")
                                }
                            }
                        }
                    }
                )
            }

            // "ตารางปัจจุบัน / Active Daily Routine" section header
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (s.isThai) "ตารางปัจจุบัน" else "Current Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Active Daily Routine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.History, null, tint = Outline, modifier = Modifier.size(14.dp))
                        Text(
                            if (s.isThai) "ปรับปรุงล่าสุด: วันนี้" else "Updated: today",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (schedules.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ยังไม่มีตารางการนอน" else "No sleep schedule yet", color = Outline)
                    }
                }
            } else {
                // Wake-up marker opens the timeline, taken from the night sleep's end time.
                wakeUp?.let { w ->
                    item {
                        TimelineMarker(
                            icon = Icons.Filled.WbSunny,
                            time = to12h(w),
                            eyebrow = "Start Day",
                            title = if (s.isThai) "ตื่นนอน (Wake Up)" else "Wake Up"
                        )
                    }
                }
                items(routine.size, key = { routine[it].schedule.id }) { i ->
                    val entry = routine[i]
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entry.wakeWindowMinutes?.takeIf { it > 0 }?.let { gap ->
                            WakeWindowRow(durationText(gap, s.isThai), s.isThai)
                        }
                        RoutineCard(
                            entry = entry,
                            isThai = s.isThai,
                            canDelete = canDelete,
                            onEdit = { navController.navigate(Screen.AddSleepTime.edit(entry.schedule.id)) },
                            onDelete = { toDelete = entry.schedule }
                        )
                    }
                }
            }

            item {
                RoutineSummaryCard(scheduledMinutes, targetHours, s.isThai)
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.AddSleepTime.create()) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.isThai) "เพิ่มเวลาการนอน" else "Add Sleep Time",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            }

            item {
                Text(
                    if (s.isThai) "\"การมีตารางการนอนที่สม่ำเสมอช่วยให้สมองลูกน้อยเติบโตอย่างเต็มที่\""
                    else "\"A consistent sleep schedule helps your baby's brain grow to its full potential\"",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showBedtimeReason) {
        BedtimeReasonDialog(
            isThai = s.isThai,
            onReason = { scope.launch { settingsManager.setSleepRecResponded() }; showBedtimeReason = false },
            onDismiss = { showBedtimeReason = false }
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

/** Stitch AI card: `auto_awesome` + บทวิเคราะห์จาก AI / Live Insight + body + footnote. */
@Composable
private fun AiInsightCard(isThai: Boolean, body: String, actions: @Composable RowScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                Text(
                    if (isThai) "บทวิเคราะห์จาก AI" else "AI Analysis",
                    style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(100.dp), color = Primary) {
                    Text(
                        "Live Insight", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
            Text(
                "AI Recommendation • " + (if (isThai) "เมื่อสักครู่" else "Just now"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Stitch `hourglass_empty` "Wake Window: ..." connector between two sleep blocks. */
@Composable
private fun WakeWindowRow(text: String, isThai: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.HourglassEmpty, null, tint = Outline, modifier = Modifier.size(14.dp))
        Text(
            (if (isThai) "ช่วงตื่น: " else "Wake Window: ") + text,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimelineMarker(icon: androidx.compose.ui.graphics.vector.ImageVector, time: String, eyebrow: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(SecondaryFixed.copy(0.55f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(time, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Outline, fontWeight = FontWeight.Bold)
    }
}

/** One sleep block: times, duration pill, Thai + English names, and edit/delete. */
@Composable
private fun RoutineCard(entry: RoutineEntry, isThai: Boolean, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val sc = entry.schedule
    val isNap = sc.sleepType == "nap"
    val minutes = scheduleDurationMinutes(sc.startTime, sc.endTime)
    val days = if (sc.daysOfWeek.equals("ALL", true)) (if (isThai) "ทุกวัน" else "Every day")
    else sc.daysOfWeek.split(",").joinToString(" ") { dayLabel(it.trim().uppercase(), isThai) }

    val thaiName = if (isNap) {
        (if (isThai) "นอนกลางวันรอบที่ " else "Nap ") + (entry.napIndex ?: 1)
    } else {
        if (isThai) "นอนกลางคืน" else "Night sleep"
    }
    val engName = if (isNap) "Nap Session ${entry.napIndex ?: 1}" else "Night Sleep Routine"

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.10f)), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isNap) Icons.Filled.Bedtime else Icons.Filled.NightlightRound,
                        null, tint = Primary, modifier = Modifier.size(20.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "${to12h(sc.startTime)} — ${to12h(sc.endTime)}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )
                    Text(thaiName, style = MaterialTheme.typography.bodySmall)
                    Text(engName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(100.dp), color = SecondaryFixed.copy(0.55f)) {
                    Text(
                        durationText(minutes, isThai), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(days, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(17.dp))
                    }
                    // The design shows delete on naps only — the night block is the schedule's
                    // anchor, and removing it would leave every wake window undefined.
                    if (isNap && canDelete) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Stitch summary: Total Sleep vs age target, plus a 5-star quality read. */
@Composable
private fun RoutineSummaryCard(scheduledMinutes: Int, targetHours: Int, isThai: Boolean) {
    val hours = scheduledMinutes / 60f
    val ratio = if (targetHours > 0) hours / targetHours else 0f
    // 5 stars only once the schedule actually covers the age target; below ~60% reads as 1.
    val stars = when {
        ratio >= 0.98f -> 5
        ratio >= 0.85f -> 4
        ratio >= 0.72f -> 3
        ratio >= 0.60f -> 2
        else -> 1
    }
    val qualityLabel = when (stars) {
        5 -> if (isThai) "ดีเยี่ยม (Excellent)" else "Excellent"
        4 -> if (isThai) "ดีมาก (Good)" else "Good"
        3 -> if (isThai) "พอใช้ (Fair)" else "Fair"
        else -> if (isThai) "ควรปรับ (Needs work)" else "Needs work"
    }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Total Sleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        String.format("%.1f", hours),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary
                    )
                    Text(
                        if (isThai) "ชม." else "h",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    (if (isThai) "เป้าหมาย: " else "Target: ") + "$targetHours-${targetHours + 2} " + (if (isThai) "ชม." else "h"),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    repeat(5) { i ->
                        Icon(
                            Icons.Filled.Grade, null,
                            tint = if (i < stars) Primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(qualityLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun bedtimeReasons(isThai: Boolean) =
    if (isThai) listOf("ลูกยังไม่ง่วงตอนนี้", "เวลานี้ไม่สะดวกสำหรับครอบครัว", "อื่นๆ")
    else listOf("Not sleepy at this time yet", "Doesn't fit our family's schedule", "Other")

/** "No, and here's why" — reuses the same preset-reason-chip pattern as the milestone/vaccine
 *  skip flows elsewhere in the app, rather than inventing a free-text dialog. The reason itself
 *  isn't persisted (nothing reads it yet); only the response timestamp gates the re-ask cooldown. */
@Composable
private fun BedtimeReasonDialog(isThai: Boolean, onReason: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isThai) "เวลานี้ไม่เหมาะเหรอคะ?" else "This time doesn't work?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bedtimeReasons(isThai).forEach { reason ->
                    OutlinedButton(onClick = { onReason(reason) }, modifier = Modifier.fillMaxWidth()) { Text(reason) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
    )
}

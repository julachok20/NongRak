@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ExerciseLog
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.EmptyChartState
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.WeeklyBarChart
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SleepActivityInsightsScreen(childViewModel: ChildViewModel, chatViewModel: ChatViewModel, navController: NavController) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child
    val today = remember { LocalDate.now() }
    val last7Days = remember { (0..6).map { today.minusDays((6 - it).toLong()) } }
    val sleepTargetMin = childViewModel.sleepTargetHours() * 60

    LaunchedEffect(child?.id) { childViewModel.loadSleep() }

    var exerciseLogs by remember { mutableStateOf<List<ExerciseLog>>(emptyList()) }
    LaunchedEffect(child?.id) {
        child?.let { c -> exerciseLogs = NutritionRepository().getExerciseLogsRange(c.id, last7Days.first().toString(), today.toString()) }
    }

    val sleepMinutesByDay = remember(state.sleepLogs) {
        last7Days.associateWith { day ->
            state.sleepLogs.filter { log ->
                TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate() == day
            }.sumOf { log ->
                val start = TimeUtils.parseInstant(log.startTime)
                val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
                if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
            }
        }
    }
    val exerciseMinByDay = remember(exerciseLogs) {
        last7Days.associateWith { day -> exerciseLogs.filter { it.logDate == day.toString() }.sumOf { it.durationMin ?: 0 } }
    }
    val hasSleepData = state.sleepLogs.isNotEmpty()
    val hasExerciseData = exerciseLogs.isNotEmpty()
    val avgExerciseMin = if (hasExerciseData) exerciseMinByDay.values.sum() / 7 else 0
    val todaySleepMin = sleepMinutesByDay[today] ?: 0L
    val energyPct = if (sleepTargetMin > 0) (todaySleepMin.toFloat() / sleepTargetMin).coerceIn(0f, 1f) else 0f

    LaunchedEffect(child?.id, sleepMinutesByDay, exerciseMinByDay) {
        child?.let {
            if (hasSleepData || hasExerciseData) {
                val summary = buildString {
                    appendLine((if (isThai) "ชั่วโมงนอนรายวัน (7 วัน): " else "Daily sleep hours (7d): ") + sleepMinutesByDay.values.joinToString(", ") { "%.1f".format(it / 60f) })
                    appendLine((if (isThai) "เป้าหมาย WHO: " else "WHO target: ") + "${childViewModel.sleepTargetHours()} ${if (isThai) "ชม./วัน" else "hrs/day"}")
                    appendLine((if (isThai) "กิจกรรมทางกายเฉลี่ย: " else "Avg physical activity: ") + "$avgExerciseMin ${if (isThai) "นาที/วัน" else "min/day"}")
                }
                chatViewModel.loadSleepActivityInsight(it, summary, isThai)
            }
        }
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "สถิติการนอนและกิจกรรม" else "Sleep & Activity Statistics", onBack = { navController.popBackStack() }, navController = navController) },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
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
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Lightbulb, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(if (isThai) "คำแนะนำจาก AI" else "AI Advice", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        if (!hasSleepData && !hasExerciseData) {
                            Text(if (isThai) "ยังไม่มีข้อมูลการนอนหรือกิจกรรมสัปดาห์นี้" else "No sleep or activity logs this week yet.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.9f))
                        } else if (chatState.sleepActivityInsight != null) {
                            Text(chatState.sleepActivityInsight!!, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isThai) "แนวโน้มการนอน (7 วันล่าสุด)" else "Sleep trend (last 7 days)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (!hasSleepData) {
                            EmptyChartState(isThai)
                        } else {
                            WeeklyBarChart(last7Days.map { it to (sleepMinutesByDay[it]?.let { m -> (m / 60).toInt() } ?: 0) }, sleepTargetMin / 60f, isThai, unit = "h")
                            Surface(shape = RoundedCornerShape(12.dp), color = Primary.copy(0.08f), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        (if (isThai) "เกณฑ์แนะนำจาก WHO สำหรับเด็กช่วงวัยนี้คือ " else "WHO recommendation for this age group: ") + "${childViewModel.sleepTargetHours()} " + (if (isThai) "ชั่วโมงต่อวัน" else "hours/day"),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isThai) "คุณภาพการนอนรายสัปดาห์" else "Weekly sleep quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (isThai) "ช่วงเวลาที่มีการบันทึกว่านอนหลับในแต่ละวัน" else "Hours logged as asleep, by day",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!hasSleepData) EmptyChartState(isThai) else SleepQualityGrid(last7Days, state.sleepLogs, isThai)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isThai) "กิจกรรมทางกาย" else "Physical activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(if (isThai) "นาทีที่ทำกิจกรรม/ออกกำลังกาย" else "Minutes of activity/exercise", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!hasExerciseData) {
                            EmptyChartState(isThai)
                        } else {
                            WeeklyBarChart(last7Days.map { it to (exerciseMinByDay[it] ?: 0) }, null, isThai)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$avgExerciseMin " + (if (isThai) "นาที" else "min"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary)
                                    Text(if (isThai) "ค่าเฉลี่ยสัปดาห์นี้" else "Average this week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Tertiary.copy(0.1f))) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("😊", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.weight(1f)) {
                            Text(if (isThai) "ระดับพลังงานวันนี้" else "Today's energy level", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (isThai) "ประเมินจากชั่วโมงนอนเทียบเป้าหมายวันนี้" else "Estimated from today's sleep vs target",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(5) { i ->
                                    val filled = i < (energyPct * 5).toInt().coerceAtLeast(if (todaySleepMin > 0) 1 else 0)
                                    Box(Modifier.weight(1f).height(8.dp).background(if (filled) Tertiary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.NightsStay, null, tint = Primary, modifier = Modifier.size(28.dp))
                        Text(
                            if (isThai) "สภาพแวดล้อมที่เงียบสงบและมืดช่วยให้ลูกน้อยหลับสนิทขึ้น" else "A quiet, dark environment helps your child sleep more soundly",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** Real per-day sleep coverage (not a fabricated "sleep depth/stage" reading — this app only ever
 *  records start/end times, never sleep-stage data). Each day row shows which 2-hour buckets have
 *  at least one recorded sleep session overlapping them. */
@Composable
private fun SleepQualityGrid(days: List<LocalDate>, logs: List<SleepLog>, isThai: Boolean) {
    val buckets = 0 until 24 step 2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(28.dp))
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.weight(1f))
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        days.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, if (isThai) Locale.forLanguageTag("th") else Locale.ENGLISH),
                    modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = Outline
                )
                buckets.forEach { hour ->
                    val asleep = logs.any { log ->
                        val start = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())
                        val end = (log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()).atZone(ZoneId.systemDefault())
                        if (start == null) return@any false
                        val bucketStart = day.atTime(hour, 0).atZone(ZoneId.systemDefault())
                        val bucketEnd = bucketStart.plusHours(2)
                        start.isBefore(bucketEnd) && end.isAfter(bucketStart)
                    }
                    Box(
                        Modifier.weight(1f).height(16.dp)
                            .background(if (asleep) Primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).background(Primary, CircleShape))
            Text(if (isThai) "นอน" else "Asleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            Text(if (isThai) "ตื่น" else "Awake", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

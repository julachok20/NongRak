@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ExerciseLog
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Monthly Summary Report — unlike WeeklyHealthReportScreen (profile fields only, explicitly avoids
 *  citing history it doesn't have), this pulls real 30-day aggregates across sleep/nutrition/
 *  activity/development/elimination — all built for the Statistics rebuild — and grounds the AI
 *  narrative in them. */
@Composable
fun MonthlyReportScreen(childViewModel: ChildViewModel, chatViewModel: ChatViewModel, navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child
    val today = remember { LocalDate.now() }
    val monthAgo = remember { today.minusDays(29) }

    LaunchedEffect(child?.id) {
        childViewModel.loadSleep()
        childViewModel.loadWasteRecords()
    }

    var foodLogs by remember { mutableStateOf<List<FoodLog>>(emptyList()) }
    var exerciseLogs by remember { mutableStateOf<List<ExerciseLog>>(emptyList()) }
    LaunchedEffect(child?.id) {
        child?.let { c ->
            val repo = NutritionRepository()
            foodLogs = repo.getFoodLogsRange(c.id, monthAgo.toString(), today.toString())
            exerciseLogs = repo.getExerciseLogsRange(c.id, monthAgo.toString(), today.toString())
        }
    }

    val sleepInMonth = remember(childState.sleepLogs) {
        childState.sleepLogs.filter { log ->
            val d = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            d != null && !d.isBefore(monthAgo) && !d.isAfter(today)
        }
    }
    val avgSleepHrPerDay = remember(sleepInMonth) {
        val totalMin = sleepInMonth.sumOf { log ->
            val start = TimeUtils.parseInstant(log.startTime)
            val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
            if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
        }
        totalMin / 60f / 30f
    }
    val avgKcalPerDay = if (foodLogs.isNotEmpty()) foodLogs.sumOf { it.calories ?: 0 } / 30 else null
    val avgExerciseMinPerDay = if (exerciseLogs.isNotEmpty()) exerciseLogs.sumOf { it.durationMin ?: 0 } / 30 else null
    val milestonesThisMonth = remember(childState.milestones) {
        childState.milestones.count { m ->
            m.isAchieved && m.achievedAt?.let { a -> TimeUtils.parseInstant(a)?.atZone(ZoneId.systemDefault())?.toLocalDate() }?.let { !it.isBefore(monthAgo) } == true
        }
    }
    val illnessThisMonth = remember(childState.healthRecords) {
        childState.healthRecords.count { r ->
            r.eventType == "illness" && (r.recordedAt ?: "").take(10).let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { !it.isBefore(monthAgo) } == true
        }
    }
    val eliminationThisMonth = remember(childState.wasteRecords) {
        childState.wasteRecords.count { r ->
            val d = TimeUtils.parseInstant(r.recordedAt)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            d != null && !d.isBefore(monthAgo)
        }
    }
    val hasAnyData = sleepInMonth.isNotEmpty() || foodLogs.isNotEmpty() || exerciseLogs.isNotEmpty() || milestonesThisMonth > 0 || illnessThisMonth > 0 || eliminationThisMonth > 0

    LaunchedEffect(child?.id, hasAnyData) {
        child?.let {
            if (hasAnyData) {
                val summary = buildString {
                    appendLine((if (isThai) "ชั่วโมงนอนเฉลี่ยต่อวัน: " else "Avg sleep/day: ") + "%.1f".format(avgSleepHrPerDay) + (if (isThai) " ชม." else " hr"))
                    avgKcalPerDay?.let { appendLine((if (isThai) "แคลอรี่เฉลี่ยต่อวัน: " else "Avg calories/day: ") + "$it kcal") }
                    avgExerciseMinPerDay?.let { appendLine((if (isThai) "กิจกรรมทางกายเฉลี่ยต่อวัน: " else "Avg activity/day: ") + "$it " + (if (isThai) "นาที" else "min")) }
                    appendLine((if (isThai) "ทักษะที่ทำได้ใหม่เดือนนี้: " else "New milestones this month: ") + "$milestonesThisMonth")
                    appendLine((if (isThai) "จำนวนครั้งป่วยเดือนนี้: " else "Illness episodes this month: ") + "$illnessThisMonth")
                    appendLine((if (isThai) "จำนวนครั้งบันทึกการขับถ่ายเดือนนี้: " else "Elimination logs this month: ") + "$eliminationThisMonth")
                }
                chatViewModel.loadMonthlyReport(it, summary, isThai)
            }
        }
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "สรุปรายงานประจำเดือน" else "Monthly Summary Report", onBack = onBack) },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.STATS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(if (isThai) "รายงานโดย AI" else "AI REPORT", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(if (isThai) "สรุปรายงานประจำเดือน" else "Monthly Summary Report", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${monthAgo} – ${today}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.85f))
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!hasAnyData) {
                        Text(
                            if (isThai) "ยังไม่มีข้อมูลเพียงพอในช่วง 30 วันที่ผ่านมา ลองบันทึกการนอน อาหาร หรือกิจกรรมเพิ่มเติม" else "Not enough data in the last 30 days yet — try logging more sleep, food, or activity.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (chatState.monthlyReport != null) {
                        Text(chatState.monthlyReport!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isThai) "ข้อมูลที่ใช้ในการสรุป (30 วัน)" else "Data used in this summary (30d)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(4.dp)) {
                        val rows = buildList {
                            add((if (isThai) "นอนเฉลี่ย/วัน" else "Avg sleep/day") to "%.1f ".format(avgSleepHrPerDay) + (if (isThai) "ชม." else "hr"))
                            avgKcalPerDay?.let { add((if (isThai) "แคลอรี่เฉลี่ย/วัน" else "Avg calories/day") to "$it kcal") }
                            avgExerciseMinPerDay?.let { add((if (isThai) "กิจกรรมเฉลี่ย/วัน" else "Avg activity/day") to "$it " + (if (isThai) "นาที" else "min")) }
                            add((if (isThai) "ทักษะใหม่เดือนนี้" else "New milestones") to "$milestonesThisMonth")
                            add((if (isThai) "จำนวนครั้งป่วย" else "Illness episodes") to "$illnessThisMonth")
                            add((if (isThai) "บันทึกการขับถ่าย" else "Elimination logs") to "$eliminationThisMonth")
                        }
                        rows.forEachIndexed { i, (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            Text(
                if (isThai) "รายงานนี้จัดทำจากข้อมูลที่ผู้ปกครองบันทึกไว้ในแอปเท่านั้น ไม่ใช่การวินิจฉัยทางการแพทย์ กรุณาปรึกษาแพทย์หากมีข้อกังวล"
                else "This report is generated only from data recorded in the app by the parent — it is not a medical diagnosis. Please consult a doctor if you have concerns.",
                style = MaterialTheme.typography.labelSmall, color = Outline
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

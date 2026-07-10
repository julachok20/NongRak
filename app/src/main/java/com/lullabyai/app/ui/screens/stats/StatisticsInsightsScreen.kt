@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.NoChildDataCard
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.OnboardingViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class StatsPeriod(val days: Int, val labelTh: String, val labelEn: String) {
    DAY(1, "วัน", "Day"), WEEK(7, "สัปดาห์", "Week"), MONTH(30, "เดือน", "Month"), YEAR(365, "ปี", "Year")
}

@Composable
fun StatisticsInsightsScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    onboardingViewModel: OnboardingViewModel,
    navController: NavController
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child
    val today = remember { LocalDate.now() }

    var period by remember { mutableStateOf(StatsPeriod.WEEK) }
    var childToDelete by remember { mutableStateOf<com.lullabyai.app.data.model.Child?>(null) }

    // This screen never triggered its own load before (relied on Dashboard having already populated
    // the shared ChildViewModel) — fine when Dashboard is always visited first, but leaves isLoading
    // stuck at its true default forever if this screen is ever reached directly.
    LaunchedEffect(Unit) {
        if (state.children.isEmpty() && state.child == null) childViewModel.loadChild()
    }

    // Feeding consistency needs a per-day fetch (NutritionRepository has no bulk range query),
    // so it's only computed for Day/Week to avoid issuing 30-365 network calls for Month/Year.
    var recentFoodLogs by remember { mutableStateOf<Map<LocalDate, List<FoodLog>>>(emptyMap()) }
    LaunchedEffect(child?.id) {
        child?.let { c ->
            childViewModel.loadSleep()
            val repo = NutritionRepository()
            val days = (0..6).map { today.minusDays(it.toLong()) }
            recentFoodLogs = days.associateWith { d -> repo.getFoodLogs(c.id, d.toString()) }
        }
    }

    val windowStart = today.minusDays((period.days - 1).toLong())
    val sleepInWindow = state.sleepLogs.filter { log ->
        val d = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate()
        d != null && !d.isBefore(windowStart) && !d.isAfter(today)
    }
    val sleepConsistencyPct = remember(sleepInWindow, period) {
        val checkDays = minOf(period.days, 30) // cap the day-by-day check to a sane loop size
        val daysWithMain = (0 until checkDays).count { offset ->
            val day = today.minusDays(offset.toLong())
            sleepInWindow.any { it.sleepType == "main" && TimeUtils.parseInstant(it.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate() == day }
        }
        daysWithMain * 100 / checkDays
    }
    val feedingConsistencyPct = if (period == StatsPeriod.DAY || period == StatsPeriod.WEEK) {
        val relevant = recentFoodLogs.entries.filter { !it.key.isBefore(windowStart) }
        val daysWithFood = relevant.count { it.value.isNotEmpty() }
        if (relevant.isNotEmpty()) daysWithFood * 100 / relevant.size else 0
    } else null
    val milestoneAchievedPct = remember(state.milestones) {
        val ageAppropriate = state.milestones.filter {
            val exp = it.ageMonthsExpected ?: return@filter false
            com.lullabyai.app.data.VaccineSetupCatalog.ageMonthsFromBirthdate(child?.birthdate ?: "") >= exp
        }
        if (ageAppropriate.isEmpty()) 0 else ageAppropriate.count { it.isAchieved } * 100 / ageAppropriate.size
    }
    val healthScore = remember(sleepConsistencyPct, feedingConsistencyPct, milestoneAchievedPct) {
        val components = listOfNotNull(sleepConsistencyPct, feedingConsistencyPct, milestoneAchievedPct)
        if (components.isEmpty()) 0 else components.sum() / components.size
    }
    val scoreLabel = when {
        healthScore >= 80 -> if (s.isThai) "ดีเยี่ยม" else "Excellent"
        healthScore >= 60 -> if (s.isThai) "ดี" else "Good"
        healthScore >= 40 -> if (s.isThai) "ปานกลาง" else "Fair"
        else -> if (s.isThai) "ต้องติดตาม" else "Needs attention"
    }

    // Development trend: cumulative achieved-milestone count over real achieved_at dates.
    val achievedSorted = remember(state.milestones) {
        state.milestones.filter { it.isAchieved && it.achievedAt != null }.sortedBy { it.achievedAt }
    }

    // Sleep quality this week (always a fixed 7-day row, matching the mockup's weekday layout).
    val last7Days = remember { (0..6).map { today.minusDays((6 - it).toLong()) } }
    val sleepTargetMin = childViewModel.sleepTargetHours() * 60
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

    val todayFood = recentFoodLogs[today] ?: emptyList()
    val todayKcal = todayFood.sumOf { it.calories ?: 0 }
    var todayExerciseMin by remember { mutableStateOf(0) }
    LaunchedEffect(child?.id) {
        child?.let { c -> todayExerciseMin = NutritionRepository().getExerciseLogs(c.id).sumOf { it.durationMin ?: 0 } }
    }

    LaunchedEffect(child?.id, sleepInWindow.size, recentFoodLogs.size) {
        child?.let {
            val summary = buildString {
                appendLine((if (s.isThai) "ความสม่ำเสมอการนอน: " else "Sleep consistency: ") + "$sleepConsistencyPct%")
                feedingConsistencyPct?.let { p -> appendLine((if (s.isThai) "ความสม่ำเสมอการกิน: " else "Feeding consistency: ") + "$p%") }
                appendLine((if (s.isThai) "พัฒนาการตามวัยที่ทำได้แล้ว: " else "Age-appropriate milestones achieved: ") + "$milestoneAchievedPct%")
            }
            chatViewModel.loadTrendInsights(it, summary, s.isThai)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "สถิติและข้อมูลเชิงลึก" else "Statistics & Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.STATS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = {},
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading && state.children.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        } else if (state.children.isEmpty() && !state.isLoading && state.error != null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            com.lullabyai.app.ui.components.ChildLoadErrorCard(s.isThai) { childViewModel.loadChild() }
        }
        } else if (state.children.isEmpty() && !state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            NoChildDataCard(s.isThai) { navController.navigate(Screen.AddChildChoice.route) }
        }
        } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Child switcher row (initials-based avatars — no per-child signed-URL fetch, to keep this cheap)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                state.children.forEach { c ->
                    val selected = c.id == child?.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { childViewModel.selectChild(c.id) }) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(if (selected) Primary else Primary.copy(0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(c.name.take(1).uppercase(), color = if (selected) Color.White else Primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Text(c.name, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { navController.navigate(Screen.AddChildChoice.route) }) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, null, tint = Outline) }
                    Text(if (s.isThai) "เพิ่มลูก" else "Add child", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }

            // Period tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsPeriod.entries.forEach { p ->
                    val selected = p == period
                    Surface(
                        onClick = { period = p }, shape = RoundedCornerShape(100.dp),
                        color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            if (s.isThai) p.labelTh else p.labelEn, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Health score ring
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            val inset = stroke.width / 2
                            drawArc(color = Color(0xFFE0E3E5), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke,
                                size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = Offset(inset, inset))
                            drawArc(color = Primary, startAngle = -90f, sweepAngle = 360f * (healthScore / 100f), useCenter = false, style = stroke,
                                size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = Offset(inset, inset))
                        }
                        Text("$healthScore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                    }
                    Text(scoreLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (s.isThai) "คะแนนสุขภาพโดยรวม จากความสม่ำเสมอการนอน การกิน และพัฒนาการตามวัย"
                        else "Overall score from sleep/feeding consistency and age-appropriate development",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Bento nav grid — hub-style entry points into dedicated Statistics detail screens
            // (each with its own real charts), matching the 20.Statics mockup's 5 entry points:
            // weight / height / nutrition / sleep&activity as quarter-tiles, elimination as a wide card.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatsBentoCard(Icons.Filled.MonitorWeight, if (s.isThai) "สถิติน้ำหนัก" else "Weight stats", if (s.isThai) "ติดตามการเติบโต" else "Track growth", Primary, Modifier.weight(1f)) {
                    navController.navigate(Screen.WhoWeightForAgeDetail.route)
                }
                StatsBentoCard(Icons.Filled.Straighten, if (s.isThai) "สถิติส่วนสูง" else "Height stats", if (s.isThai) "พัฒนาทางร่างกาย" else "Physical development", Primary, Modifier.weight(1f)) {
                    navController.navigate(Screen.WhoHeightForAgeDetail.route)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatsBentoCard(Icons.Filled.Restaurant, if (s.isThai) "สถิติโภชนาการ" else "Nutrition stats", if (s.isThai) "อาหารและนม" else "Food & milk", Tertiary, Modifier.weight(1f)) {
                    navController.navigate(Screen.NutritionInsights.route)
                }
                StatsBentoCard(Icons.Filled.Bedtime, if (s.isThai) "การนอนและกิจกรรม" else "Sleep & Activity", if (s.isThai) "รูปแบบการนอน" else "Sleep patterns", Tertiary, Modifier.weight(1f)) {
                    navController.navigate(Screen.SleepActivityInsights.route)
                }
            }
            // Wide alert-style card — elimination tracking reads as a health-monitoring item in the
            // mockup (error/red tint + notification dot), not just another quarter-tile.
            Card(
                onClick = { navController.navigate(Screen.HealthEliminationInsights.route) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Error.copy(0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.WaterDrop, null, tint = Error, modifier = Modifier.size(20.dp))
                        }
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Error))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "สุขภาพและการขับถ่าย" else "Health & Elimination", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(if (s.isThai) "ติดตามการขับถ่าย" else "Track elimination", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }

            // Monthly Summary Report -> a real 30-day-aggregate AI report (added per user request,
            // not part of the 24.Statics2 mockup itself).
            Card(
                onClick = { navController.navigate(Screen.MonthlyReport.route) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Summarize, null, tint = Primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "สรุปรายงานประจำเดือน" else "Monthly Summary Report", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(if (s.isThai) "ภาพรวมการนอน โภชนาการ และพัฒนาการ 30 วัน" else "30-day sleep, nutrition & development overview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }

            // Quick log -> Quick Log Hub (4 quick-access tiles + recent activity feed).
            // This is the only entry point to that screen since this screen replaced it as
            // the Stats-tab landing page — without this link it would be unreachable.
            Card(
                onClick = { navController.navigate(Screen.QuickLogHub.route) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AddCircle, null, tint = Primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "บันทึกด่วน" else "Quick log", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(if (s.isThai) "บันทึกการนอน อาหาร สุขภาพ พัฒนาการ" else "Log sleep, food, health, development", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }

            // Weekly behavior chart -> Smart Trends
            Card(
                onClick = { navController.navigate(Screen.SmartTrends.route) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.GridView, null, tint = Primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "แผนภูมิพฤติกรรมรายสัปดาห์" else "Weekly behavior chart", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(if (s.isThai) "วิเคราะห์รูปแบบการนอนและการกิน" else "Sleep + feeding pattern analysis", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }

            // Development trend
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(if (s.isThai) "แนวโน้มการพัฒนา" else "Development trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (s.isThai) "ทักษะที่ทำได้สะสมตามเวลาจริง" else "Cumulative skills achieved over time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { navController.navigate(Screen.DevelopmentHistory.route) }) {
                            Text(if (s.isThai) "ดูรายละเอียด" else "View details", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (achievedSorted.size >= 2) DevelopmentTrendChart(achievedSorted)
                    else Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ยังมีข้อมูลไม่พอสำหรับแสดงแนวโน้ม" else "Not enough data yet to show a trend", color = Outline, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Food / activity mini stats -> retrospective history tables (previously only "today"
            // was ever shown anywhere for these two, with no way to look back further).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard(
                    Icons.Filled.Restaurant, if (s.isThai) "อาหารวันนี้" else "Food today", "$todayKcal kcal", Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.FoodHistory.route) }
                )
                MiniStatCard(
                    Icons.Filled.DirectionsRun, if (s.isThai) "กิจกรรม" else "Activity", "$todayExerciseMin " + (if (s.isThai) "นาที" else "min"), Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.ExerciseHistory.route) }
                )
            }

            // Sleep quality weekly row
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (s.isThai) "คุณภาพการนอน" else "Sleep quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        last7Days.forEach { day ->
                            val minutes = sleepMinutesByDay[day] ?: 0L
                            val frac = (minutes / sleepTargetMin.toFloat()).coerceIn(0f, 1f)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.width(20.dp).height(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.width(20.dp).height((48 * frac).dp).clip(RoundedCornerShape(6.dp)).background(if (frac >= 0.8f) Color(0xFF2E7D32) else Primary))
                                }
                                Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, if (s.isThai) java.util.Locale.forLanguageTag("th") else java.util.Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = Outline)
                            }
                        }
                    }
                }
            }

            // AI recommendation (reuses trendInsights — no redundant extra AI call)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(if (s.isThai) "คำแนะนำจาก AI" else "AI Recommendation", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                    }
                    if (chatState.trendInsights != null) {
                        Text(chatState.trendInsights!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
        }
    }

    childToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { childToDelete = null },
            title = { Text(if (s.isThai) "ลบเด็กคนนี้?" else "Delete this child?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "การลบ \"${target.name}\" ไม่สามารถย้อนกลับได้" else "Deleting \"${target.name}\" cannot be undone") },
            confirmButton = { TextButton(onClick = { childViewModel.deleteChild(target.id); childToDelete = null }) { Text(if (s.isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { childToDelete = null }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

@Composable
private fun DevelopmentTrendChart(achieved: List<DevelopmentMilestone>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val points = achieved.mapIndexed { idx, _ -> idx + 1 }
        val maxY = points.last().toFloat()
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxY) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun StatsBentoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, caption: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Card(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniStatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp),
        onClick = onClick ?: {}
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SmartTrendsScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child
    val today = remember { LocalDate.now() }
    val last7Days = remember { (0..6).map { today.minusDays((6 - it).toLong()) } } // oldest..today

    var weekFoodLogs by remember { mutableStateOf<Map<LocalDate, List<FoodLog>>>(emptyMap()) }
    LaunchedEffect(child?.id) {
        child?.let { c ->
            childViewModel.loadSleep()
            val repo = NutritionRepository()
            weekFoodLogs = last7Days.associateWith { day -> repo.getFoodLogs(c.id, day.toString()) }
        }
    }

    val last7Logs = remember(state.sleepLogs) {
        state.sleepLogs.filter { log ->
            val d = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            d != null && !d.isBefore(last7Days.first()) && !d.isAfter(today)
        }
    }

    // 7 (day) x 24 (hour) activity density grid — real counts, not decorative.
    val heatmapCounts = remember(last7Logs, weekFoodLogs) {
        Array(7) { IntArray(24) }.also { grid ->
            last7Logs.forEach { log ->
                val start = TimeUtils.parseInstant(log.startTime) ?: return@forEach
                val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
                val startDate = start.atZone(ZoneId.systemDefault()).toLocalDate()
                val dayIdx = last7Days.indexOf(startDate)
                if (dayIdx >= 0) {
                    val startHour = start.atZone(ZoneId.systemDefault()).hour
                    val endHour = end.atZone(ZoneId.systemDefault()).hour.let { if (end.atZone(ZoneId.systemDefault()).toLocalDate() != startDate) 23 else it }
                    for (h in startHour..endHour.coerceIn(startHour, 23)) grid[dayIdx][h] += 1
                }
            }
            weekFoodLogs.forEach { (day, logs) ->
                val dayIdx = last7Days.indexOf(day)
                if (dayIdx >= 0) logs.forEach { food ->
                    TimeUtils.parseInstant(food.createdAt)?.let { t ->
                        val h = t.atZone(ZoneId.systemDefault()).hour
                        grid[dayIdx][h] += 1
                    }
                }
            }
        }
    }

    // Real "fussiest hour" from mood-tagged sleep logs — no AI-fabricated time like the mockup's.
    val fussyLogs = remember(state.sleepLogs) { state.sleepLogs.filter { it.mood == "fussy" } }
    val fussiestHour = remember(fussyLogs) {
        if (fussyLogs.size < 3) null
        else fussyLogs.mapNotNull { TimeUtils.parseInstant(it.startTime)?.atZone(ZoneId.systemDefault())?.hour }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    // Consistency: sleep (days with a main sleep logged) + feeding (days with any food logged).
    val sleepConsistencyPct = remember(last7Logs) {
        val daysWithMain = last7Days.count { day -> last7Logs.any { it.sleepType == "main" && TimeUtils.parseInstant(it.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate() == day } }
        daysWithMain * 100 / 7
    }
    val feedingConsistencyPct = remember(weekFoodLogs) {
        val daysWithFood = weekFoodLogs.count { (_, logs) -> logs.isNotEmpty() }
        daysWithFood * 100 / 7
    }

    LaunchedEffect(child?.id, last7Logs.size, weekFoodLogs.size) {
        child?.let {
            val summary = buildString {
                appendLine((if (s.isThai) "จำนวนครั้งนอนใน 7 วัน: " else "Sleep sessions in 7 days: ") + last7Logs.size)
                appendLine((if (s.isThai) "วันที่มีการนอนหลัก: " else "Days with main sleep logged: ") + "$sleepConsistencyPct%")
                appendLine((if (s.isThai) "วันที่มีการบันทึกอาหาร: " else "Days with food logged: ") + "$feedingConsistencyPct%")
                if (fussiestHour != null) appendLine((if (s.isThai) "ช่วงเวลาที่งอแงบ่อยที่สุด: " else "Most common fussy hour: ") + "${fussiestHour}:00")
            }
            chatViewModel.loadTrendInsights(it, summary, s.isThai)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "แนวโน้มอัจฉริยะ" else "Smart Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.STATS,
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

            // AI insights
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(if (s.isThai) "ข้อมูลเชิงลึกจาก AI" else "AI Insights", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                    }
                    if (chatState.trendInsights != null) {
                        Text(chatState.trendInsights!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                    }
                }
            }

            // Heatmap
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (s.isThai) "แผนภูมิกิจกรรม 7 วัน" else "7-day activity heatmap", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (s.isThai) "ความหนาแน่นของการนอนและการทานอาหารรายชั่วโมง" else "Sleep + feeding density by hour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ActivityHeatmap(heatmapCounts, last7Days, s.isThai)
                }
            }

            // Fussiest-time pattern (real, not fabricated)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Insights, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(if (s.isThai) "รูปแบบที่ตรวจพบ" else "Pattern detected", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (fussiestHour != null) {
                        Text(
                            (if (s.isThai) "ลูกน้อยมักงอแงช่วง " else "Your child tends to get fussy around ") + "${fussiestHour}:00" +
                                (if (s.isThai) " น. (จากบันทึกอารมณ์ที่ผ่านมา)" else " (based on logged moods)"),
                            style = MaterialTheme.typography.bodyMedium, color = Color.White
                        )
                    } else {
                        Text(
                            if (s.isThai) "ยังมีข้อมูลอารมณ์ไม่พอ ลองเช็กอารมณ์ในหน้าติดตามการนอนอีกสักสองสามครั้งเพื่อดูรูปแบบ"
                            else "Not enough mood data yet — log a few more mood check-ins on the Sleep tracker to see a pattern.",
                            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.9f)
                        )
                    }
                }
            }

            // Consistency bars
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (s.isThai) "ความสม่ำเสมอ 7 วัน" else "7-day consistency", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ConsistencyBar(if (s.isThai) "การนอนหลัก" else "Main sleep", sleepConsistencyPct, Primary)
                    ConsistencyBar(if (s.isThai) "การบันทึกอาหาร" else "Food logging", feedingConsistencyPct, Color(0xFFEF6C00))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// 24 one-hour columns squeezed into a phone-width card left almost no pixels per cell and no
// legend to read the color scale by — genuinely hard to make out, not just unfamiliar. Bucketing
// into 4-hour blocks (6 columns instead of 24) trades hour-level precision — which this view never
// needed, "when in the day" is the actual question — for cells wide enough to actually see.
private const val BUCKET_HOURS = 4
private const val BUCKET_COUNT = 24 / BUCKET_HOURS

@Composable
private fun ActivityHeatmap(counts: Array<IntArray>, days: List<LocalDate>, isThai: Boolean) {
    val palette = listOf(Color(0xFFF8FAFC), Color(0xFFFFF5E1), Color(0xFFFFD78F), Color(0xFFFFB347), Color(0xFFFF8C00), Color(0xFFE65100))
    val dayLabels = days.map { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, if (isThai) java.util.Locale.forLanguageTag("th") else java.util.Locale.ENGLISH) }
    val buckets = remember(counts) {
        Array(7) { day -> IntArray(BUCKET_COUNT) { b -> (0 until BUCKET_HOURS).sumOf { counts[day][b * BUCKET_HOURS + it] } } }
    }
    val maxCount = (buckets.maxOfOrNull { row -> row.maxOrNull() ?: 0 } ?: 0).coerceAtLeast(1)
    val rowHeightDp = 26.dp
    val rowGapDp = 4.dp

    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(rowGapDp)) {
            dayLabels.forEach { label ->
                Box(Modifier.height(rowHeightDp), contentAlignment = Alignment.CenterStart) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Outline, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Canvas(modifier = Modifier.weight(1f).height(rowHeightDp * 7 + rowGapDp * 6)) {
            val rowHeight = size.height / 7f
            val colWidth = size.width / BUCKET_COUNT
            for (day in 0..6) {
                for (b in 0 until BUCKET_COUNT) {
                    val level = (buckets[day][b] * (palette.size - 1) / maxCount).coerceIn(0, palette.size - 1)
                    val color = if (buckets[day][b] == 0) palette[0] else palette[level.coerceAtLeast(1)]
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(b * colWidth, day * rowHeight),
                        size = Size(colWidth - 4f, rowHeight - 4f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 0.dp)) {
        Spacer(Modifier.width(28.dp)) // align under the grid, past the day-label column
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            for (b in 0..BUCKET_COUNT) {
                val hour = b * BUCKET_HOURS
                Text("%02d:00".format(hour), style = MaterialTheme.typography.labelSmall, color = Outline)
            }
        }
    }
    // Legend — the color scale meant nothing without a "low -> high" key next to it.
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (isThai) "น้อย" else "Less", style = MaterialTheme.typography.labelSmall, color = Outline)
        palette.forEach { c ->
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(c))
        }
        Text(if (isThai) "มาก" else "More", style = MaterialTheme.typography.labelSmall, color = Outline)
    }
}

@Composable
private fun ConsistencyBar(label: String, pct: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$pct%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

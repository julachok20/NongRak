@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.model.MilkLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.EmptyChartState
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.WeeklyBarChart
import com.lullabyai.app.ui.components.WeeklyLineChart
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.ZoneId

private const val ML_PER_OZ = 29.5735f

private fun dailyTargetKcal(ageMonths: Int): Int = when (ageMonths) {
    in 0..5 -> 550
    in 6..11 -> 750
    in 12..23 -> 1000
    else -> 1200
}

@Composable
fun NutritionInsightsScreen(childViewModel: ChildViewModel, chatViewModel: ChatViewModel, navController: NavController) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child
    val today = remember { LocalDate.now() }
    val last7Days = remember { (0..6).map { today.minusDays((6 - it).toLong()) } }

    var foodByDay by remember { mutableStateOf<Map<LocalDate, List<FoodLog>>>(emptyMap()) }
    var milkLogs by remember { mutableStateOf<List<MilkLog>>(emptyList()) }
    LaunchedEffect(child?.id) {
        child?.let { c ->
            val repo = NutritionRepository()
            val range = repo.getFoodLogsRange(c.id, last7Days.first().toString(), today.toString())
            foodByDay = last7Days.associateWith { d -> range.filter { it.logDate == d.toString() } }
            milkLogs = repo.getMilkLogs(c.id).filter { log ->
                val d = TimeUtils.parseInstant(log.loggedAt)?.atZone(ZoneId.systemDefault())?.toLocalDate()
                d != null && !d.isBefore(last7Days.first()) && !d.isAfter(today)
            }
        }
    }

    val allFoodThisWeek = remember(foodByDay) { foodByDay.values.flatten() }
    val calorieByDay = remember(foodByDay) { last7Days.map { d -> d to (foodByDay[d]?.sumOf { it.calories ?: 0 } ?: 0) } }
    val targetKcal = dailyTargetKcal(childViewModel.getAgeMonths())
    val milkByDay = remember(milkLogs) {
        last7Days.map { d ->
            d to milkLogs.filter { TimeUtils.parseInstant(it.loggedAt)?.atZone(ZoneId.systemDefault())?.toLocalDate() == d }.sumOf { it.volumeMl }
        }
    }
    val mealTotals = remember(allFoodThisWeek) {
        val buckets = listOf("breakfast" to Primary, "lunch" to Tertiary, "dinner" to Color(0xFF3949AB), "snack" to Color(0xFFB0BEC5))
        buckets.map { (mt, color) ->
            val kcal = allFoodThisWeek.filter { it.mealType == mt || (mt == "snack" && it.mealType == "other") }.sumOf { it.calories ?: 0 }
            Triple(mt, kcal, color)
        }
    }
    val recentItems = remember(allFoodThisWeek, milkLogs) {
        (allFoodThisWeek.mapNotNull { f -> TimeUtils.parseInstant(f.createdAt)?.let { t -> Triple(t, f.itemName, "${f.calories ?: 0} kcal") } } +
            milkLogs.mapNotNull { m -> TimeUtils.parseInstant(m.loggedAt)?.let { t -> Triple(t, if (isThai) "นมแม่ / นมผง" else "Milk", "${m.volumeMl} ml") } })
            .sortedByDescending { it.first }.take(5)
    }

    LaunchedEffect(child?.id, calorieByDay, milkByDay) {
        child?.let {
            if (allFoodThisWeek.isNotEmpty() || milkLogs.isNotEmpty()) {
                val summary = buildString {
                    appendLine((if (isThai) "แคลอรี่รายวัน (7 วัน): " else "Daily calories (7d): ") + calorieByDay.joinToString(", ") { "${it.second}" })
                    appendLine((if (isThai) "เป้าหมาย WHO: " else "WHO target: ") + "$targetKcal kcal")
                    appendLine((if (isThai) "นมรวมสัปดาห์นี้: " else "Milk total this week: ") + "${milkByDay.sumOf { it.second }} ml")
                }
                chatViewModel.loadNutritionInsight(it, summary, isThai)
            }
        }
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "สถิติโภชนาการ" else "Nutrition Statistics", onBack = { navController.popBackStack() }) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(if (isThai) "บทวิเคราะห์จาก AI" else "AI Analysis", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        if (allFoodThisWeek.isEmpty() && milkLogs.isEmpty()) {
                            Text(if (isThai) "ยังไม่มีข้อมูลการกินหรือนมสัปดาห์นี้ บันทึกเพิ่มเพื่อรับคำแนะนำ" else "No food or milk logs this week yet — log some to get advice.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.9f))
                        } else if (chatState.nutritionInsight != null) {
                            Text(chatState.nutritionInsight!!, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isThai) "แนวโน้มแคลอรี่ (7 วัน)" else "Calorie trend (7d)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                Text((if (isThai) "เป้าหมาย WHO: " else "WHO target: ") + "$targetKcal kcal", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (allFoodThisWeek.isEmpty()) EmptyChartState(isThai) else WeeklyBarChart(calorieByDay, targetKcal.toFloat(), isThai)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isThai) "การดื่มนม (oz)" else "Milk intake (oz)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (milkLogs.isEmpty()) {
                            EmptyChartState(isThai)
                        } else {
                            val ozByDay = milkByDay.map { it.first to it.second / ML_PER_OZ }
                            WeeklyLineChart(ozByDay, isThai)
                            val avgOz = ozByDay.map { it.second }.average()
                            val weekOz = ozByDay.sumOf { it.second.toDouble() }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(if (isThai) "ค่าเฉลี่ย" else "Average", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("%.1f oz".format(avgOz), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (isThai) "สัปดาห์นี้" else "This week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("%.0f oz".format(weekOz), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isThai) "สัดส่วนมื้ออาหาร" else "Meal split", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (allFoodThisWeek.isEmpty()) EmptyChartState(isThai) else MealDonutChart(mealTotals, isThai)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isThai) "บันทึกล่าสุด" else "Recent logs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.FoodHistory.route) }) {
                        Text(if (isThai) "ดูทั้งหมด" else "View all", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (recentItems.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (isThai) "ยังไม่มีบันทึก" else "No logs yet", color = Outline) } }
            } else {
                items(recentItems) { (t, name, detail) ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(36.dp).background(Primary.copy(0.1f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Restaurant, null, tint = Primary, modifier = Modifier.size(18.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(TimeUtils.localTime(t.toString()) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(detail, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun MealDonutChart(slices: List<Triple<String, Int, Color>>, isThai: Boolean) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    val labels = mapOf(
        "breakfast" to (if (isThai) "เช้า" else "Breakfast"), "lunch" to (if (isThai) "กลางวัน" else "Lunch"),
        "dinner" to (if (isThai) "เย็น" else "Dinner"), "snack" to (if (isThai) "ว่าง" else "Snack")
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val stroke = Stroke(width = 20.dp.toPx())
                var startAngle = -90f
                slices.forEach { (_, kcal, color) ->
                    val sweep = 360f * kcal / total
                    if (kcal > 0) drawArc(color = color, startAngle = startAngle, sweepAngle = sweep, useCenter = false, style = stroke)
                    startAngle += sweep
                }
            }
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { (mt, kcal, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(color, CircleShape))
                    Text("${labels[mt]} (${kcal * 100 / total}%)", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

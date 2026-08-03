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
import com.lullabyai.app.data.model.WasteRecord
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.EmptyChartState
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HealthEliminationInsightsScreen(childViewModel: ChildViewModel, chatViewModel: ChatViewModel, navController: NavController) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child
    val today = remember { LocalDate.now() }
    val last7Days = remember { (0..6).map { today.minusDays((6 - it).toLong()) } }
    val monthAgo = remember { today.minusDays(29) }

    LaunchedEffect(child?.id) { childViewModel.loadWasteRecords() }

    val illnessLast30d = remember(state.healthRecords) {
        state.healthRecords.filter { r ->
            val d = (r.recordedAt ?: "").take(10).let { runCatching { LocalDate.parse(it) }.getOrNull() }
            r.eventType == "illness" && d != null && !d.isBefore(monthAgo)
        }
    }
    val symptomCounts = remember(illnessLast30d) {
        illnessLast30d.flatMap { it.additionalSymptoms ?: emptyList() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    }
    val wasteLast7d = remember(state.wasteRecords) {
        state.wasteRecords.filter { r ->
            val d = TimeUtils.parseInstant(r.recordedAt)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            d != null && !d.isBefore(last7Days.first()) && !d.isAfter(today)
        }
    }
    val stoolRecords = remember(wasteLast7d) { wasteLast7d.filter { it.wasteType == "stool" } }
    val bristolBuckets = remember(stoolRecords) {
        val normal = stoolRecords.count { it.consistency == "soft_normal" }
        val hard = stoolRecords.count { it.consistency == "hard_constipated" }
        val loose = stoolRecords.count { it.consistency == "loose_diarrhea" || it.consistency == "mucus_blood" }
        Triple(normal, hard, loose)
    }

    LaunchedEffect(child?.id, illnessLast30d, wasteLast7d) {
        child?.let {
            if (illnessLast30d.isNotEmpty() || wasteLast7d.isNotEmpty()) {
                val summary = buildString {
                    appendLine((if (isThai) "อาการป่วยใน 30 วัน: " else "Illness in 30d: ") + symptomCounts.joinToString(", ") { "${it.key} x${it.value}" })
                    appendLine((if (isThai) "จำนวนครั้งขับถ่ายใน 7 วัน: " else "Elimination count 7d: ") + "${wasteLast7d.size}")
                    val (n, h, l) = bristolBuckets
                    appendLine((if (isThai) "ลักษณะอุจจาระ ปกติ/แข็ง/เหลว: " else "Stool consistency normal/hard/loose: ") + "$n/$h/$l")
                }
                chatViewModel.loadEliminationInsight(it, summary, isThai)
            }
        }
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "สถิติสุขภาพและการขับถ่าย" else "Health & Elimination Statistics", onBack = { navController.popBackStack() }, navController = navController) },
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
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isThai) "ประวัติอาการป่วย (30 วัน)" else "Illness history (30d)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { navController.navigate(Screen.HealthHistory.route) }) {
                                Text(if (isThai) "ดูรายละเอียด" else "View details", color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (symptomCounts.isEmpty()) {
                            EmptyChartState(isThai)
                        } else {
                            val maxCount = symptomCounts.maxOf { it.value }
                            symptomCounts.forEach { (symptom, count) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(symptom, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text((if (isThai) "$count ครั้ง" else "${count}x"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                                        Box(Modifier.fillMaxWidth(count.toFloat() / maxCount).height(8.dp).background(Error, RoundedCornerShape(4.dp)))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isThai) "การขับถ่าย (7 วันล่าสุด)" else "Elimination (last 7 days)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LegendDot(Color(0xFF1E88E5), if (isThai) "ปัสสาวะ" else "Urine")
                                LegendDot(Color(0xFF3949AB), if (isThai) "อุจจาระ" else "Stool")
                            }
                        }
                        if (wasteLast7d.isEmpty()) EmptyChartState(isThai) else EliminationGroupedChart(last7Days, wasteLast7d, isThai)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isThai) "ลักษณะอุจจาระ (Bristol Scale)" else "Stool consistency (Bristol Scale)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(if (isThai) "สรุปความถี่ตามประวัติการบันทึก" else "Frequency summary from recorded history", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (stoolRecords.isEmpty()) {
                            EmptyChartState(isThai)
                        } else {
                            val (normal, hard, loose) = bristolBuckets
                            val total = stoolRecords.size
                            val rows = listOf(
                                Triple(if (isThai) "ปกติ (Type 3-4)" else "Normal (Type 3-4)", normal, Color(0xFF3949AB)),
                                Triple(if (isThai) "แข็ง (Type 1-2)" else "Hard (Type 1-2)", hard, Color(0xFFB0BEC5)),
                                Triple(if (isThai) "เหลว (Type 5-7)" else "Loose (Type 5-7)", loose, Error)
                            )
                            rows.forEach { (label, count, color) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("${count * 100 / total}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                                }
                            }
                            Row(Modifier.fillMaxWidth().height(20.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))) {
                                rows.forEach { (_, count, color) ->
                                    if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
                                }
                            }
                        }
                    }
                }
            }

            item {
                val hasData = stoolRecords.isNotEmpty() || illnessLast30d.isNotEmpty()
                AIInsightCard(
                    text = chatState.eliminationInsight.orEmpty(),
                    isLoading = hasData && chatState.eliminationInsight == null,
                    title = if (isThai) "คำแนะนำจาก NongRak AI" else "NongRak AI Insight",
                    emptyMessage = if (!hasData) {
                        if (isThai) "ยังไม่มีข้อมูลเพียงพอสำหรับคำแนะนำ" else "Not enough data yet for advice"
                    } else null
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EliminationGroupedChart(days: List<LocalDate>, records: List<WasteRecord>, isThai: Boolean) {
    val byDay = days.associateWith { d ->
        val dayRecs = records.filter { TimeUtils.parseInstant(it.recordedAt)?.atZone(ZoneId.systemDefault())?.toLocalDate() == d }
        (dayRecs.count { it.wasteType == "urine" }) to (dayRecs.count { it.wasteType == "stool" })
    }
    val maxCount = (byDay.values.flatMap { listOf(it.first, it.second) }.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                val (urine, stool) = byDay[d] ?: (0 to 0)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxHeight(fraction = (urine.toFloat() / maxCount).coerceIn(0.02f, 1f)).background(Color(0xFF1E88E5), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight(fraction = (stool.toFloat() / maxCount).coerceIn(0.02f, 1f)).background(Color(0xFF3949AB), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, if (isThai) Locale.forLanguageTag("th") else Locale.ENGLISH),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class HistoryWindow(val days: Long, val labelTh: String, val labelEn: String) {
    WEEK(7, "7 วัน", "7 days"), TWO_WEEKS(14, "14 วัน", "14 days"), MONTH(30, "30 วัน", "30 days")
}

/** Retrospective food-log table for Statistics — EditFoodEntriesScreen only ever shows "today". */
@Composable
fun FoodHistoryScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val child = state.child
    var window by remember { mutableStateOf(HistoryWindow.WEEK) }
    var logs by remember { mutableStateOf<List<FoodLog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(child?.id, window) {
        val c = child ?: return@LaunchedEffect
        loading = true
        val from = LocalDate.now().minusDays(window.days - 1)
        logs = NutritionRepository().getFoodLogsRange(c.id, from.toString())
        loading = false
    }

    val byDay = remember(logs) { logs.groupBy { it.logDate }.toSortedMap(compareByDescending { it }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ประวัติการทานอาหาร" else "Food History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryWindow.entries.forEach { w ->
                        val selected = w == window
                        Surface(onClick = { window = w }, shape = RoundedCornerShape(100.dp), color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                if (s.isThai) w.labelTh else w.labelEn, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (loading) {
                item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp) } }
            } else if (byDay.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ไม่มีข้อมูลในช่วงนี้" else "No entries in this window", color = Outline)
                    }
                }
            } else {
                byDay.forEach { (dateStr, dayLogs) ->
                    item {
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                        val dayTotal = dayLogs.sumOf { it.calories ?: 0 }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                date?.let { dayLabel(it, s.isThai) } ?: dateStr,
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("$dayTotal kcal", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(dayLogs, key = { it.id }) { f ->
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Restaurant, null, tint = Primary, modifier = Modifier.size(18.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(f.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(mealTypeLabel(f.mealType, s.isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
                                }
                                Text("${f.calories ?: "-"} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private fun dayLabel(date: LocalDate, isThai: Boolean): String {
    val today = LocalDate.now()
    return when (date) {
        today -> if (isThai) "วันนี้" else "Today"
        today.minusDays(1) -> if (isThai) "เมื่อวาน" else "Yesterday"
        else -> {
            val locale = if (isThai) Locale.forLanguageTag("th") else Locale.ENGLISH
            "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${date.dayOfMonth}/${date.monthValue}"
        }
    }
}

private fun mealTypeLabel(type: String, isThai: Boolean) = when (type) {
    "breakfast" -> if (isThai) "มื้อเช้า" else "Breakfast"
    "lunch" -> if (isThai) "มื้อกลางวัน" else "Lunch"
    "dinner" -> if (isThai) "มื้อเย็น" else "Dinner"
    "snack" -> if (isThai) "ของว่าง" else "Snack"
    else -> if (isThai) "อื่นๆ" else "Other"
}

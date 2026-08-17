@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant

private data class ActivityItem(val icon: ImageVector, val title: String, val detail: String, val timestamp: Instant, val route: String)

/** One-tap log tiles on this hub (feeding / sleep / health / development). */
private data class LogTileAction(
    val icon: ImageVector, val label: String, val color: Color, val actionLabel: String,
    val onClick: () -> Unit
)

@Composable
fun QuickLogHubScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child

    var todayFoodLogs by remember { mutableStateOf<List<FoodLog>>(emptyList()) }
    LaunchedEffect(child?.id) {
        child?.let {
            childViewModel.loadSleep()
            todayFoodLogs = NutritionRepository().getFoodLogs(it.id)
        }
    }

    // Merge sleep/health/development/food into one recency-sorted feed.
    val activity = remember(state.sleepLogs, state.healthRecords, state.milestones, todayFoodLogs) {
        buildList {
            state.sleepLogs.filter { it.endTime != null }.forEach { log: SleepLog ->
                TimeUtils.parseInstant(log.endTime)?.let { t ->
                    add(ActivityItem(
                        if (log.sleepType == "nap") Icons.Filled.WbSunny else Icons.Filled.Bedtime,
                        if (s.isThai) "ตื่นนอน" else "Woke up",
                        (if (s.isThai) "เวลานอน: " else "Slept: ") + (TimeUtils.localTime(log.startTime) ?: ""),
                        t, Screen.SleepTracker.route
                    ))
                }
            }
            state.healthRecords.forEach { rec: HealthRecord ->
                TimeUtils.parseInstant(rec.recordedAt)?.let { t ->
                    add(ActivityItem(Icons.Filled.Thermostat, rec.title.ifBlank { if (s.isThai) "บันทึกสุขภาพ" else "Health log" },
                        if (s.isThai) "บันทึกอาการแล้ว" else "Symptom logged", t, Screen.ChildHealth.route))
                }
            }
            state.milestones.filter { it.isAchieved }.forEach { m: DevelopmentMilestone ->
                TimeUtils.parseInstant(m.achievedAt)?.let { t ->
                    add(ActivityItem(Icons.Filled.Star, m.milestoneName,
                        if (s.isThai) "ทำได้แล้ว" else "Achieved", t, Screen.DevelopmentHistory.route))
                }
            }
            todayFoodLogs.forEach { food: FoodLog ->
                TimeUtils.parseInstant(food.createdAt)?.let { t ->
                    add(ActivityItem(Icons.Filled.Restaurant, food.itemName,
                        (food.calories?.let { "$it kcal" } ?: "") , t, Screen.Nutrition.route))
                }
            }
        }.sortedByDescending { it.timestamp }.take(5)
    }

    LaunchedEffect(child?.id, activity.size) {
        child?.let {
            val summary = if (activity.isEmpty()) (if (s.isThai) "ยังไม่มีการบันทึกวันนี้" else "No logs yet today")
                else activity.joinToString("\n") { a -> "${a.title} (${TimeUtils.localTime(a.timestamp.toString()) ?: ""})" }
            chatViewModel.loadQuickTip(it, summary, s.isThai)
        }
    }

    val logTiles = remember(s.isThai, state.activeSleepSession) {
        listOf(
            LogTileAction(
                Icons.Filled.Restaurant, if (s.isThai) "การกิน" else "Feeding", Primary,
                if (s.isThai) "บันทึกอาหาร" else "Log food"
            ) { navController.navigate(Screen.CalorieCalculator.route) },
            LogTileAction(
                if (state.activeSleepSession != null) Icons.Filled.Hotel else Icons.Filled.Bedtime,
                if (s.isThai) "การนอน" else "Sleep", Color(0xFF4B5B86),
                if (state.activeSleepSession != null) (if (s.isThai) "หยุดบันทึก" else "Stop") else (if (s.isThai) "เริ่มเวลานอน" else "Start sleep")
            ) { childViewModel.toggleSleepSession() },
            LogTileAction(
                Icons.Filled.Thermostat, if (s.isThai) "สุขภาพ" else "Health", Color(0xFFBA1A1A),
                if (s.isThai) "บันทึกอาการ" else "Log symptom"
            ) { navController.navigate(Screen.AddHealthRecord.route) },
            LogTileAction(
                Icons.Filled.EmojiEvents, if (s.isThai) "พัฒนาการ" else "Development", Color(0xFF526069),
                if (s.isThai) "ทักษะใหม่" else "New skill"
            ) { navController.navigate(Screen.ChildDevelopment.route) },
            LogTileAction(
                Icons.Filled.HealthAndSafety, if (s.isThai) "ช่องปากและฟัน" else "Oral Health", Color(0xFF173DB8),
                if (s.isThai) "บันทึกฟัน" else "Oral care"
            ) { navController.navigate(Screen.OralHealth.route) }
        )
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ศูนย์บันทึกด่วน" else "Quick Log Hub",
                onBack = { navController.popBackStack() },
                actions = {
                    TextButton(onClick = { navController.navigate(Screen.SmartTrends.route) }) {
                        Icon(Icons.Filled.Insights, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (s.isThai) "แนวโน้มอัจฉริยะ" else "Smart Trends", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = {},
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
                Text(
                    if (s.isThai) "แตะเพื่อบันทึกกิจวัตรประจำวันของลูกน้อย" else "Tap to log your child's daily routine",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                // Opens the real Quick Action Search screen (inline focus mode was flickering shut).
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        placeholder = {
                            Text(
                                if (s.isThai) "ค้นหาเพื่อดูสิ่งที่ทำได้เพิ่มเติม" else "Search to see more you can do",
                                color = Outline
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = Color.Transparent,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                            disabledPlaceholderColor = Outline,
                            disabledLeadingIconColor = Primary
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { navController.navigate(Screen.QuickActionSearch.route) }
                    )
                }
            }

            logTiles.chunked(2).forEach { rowActions ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowActions.forEach { a ->
                            QuickLogTile(a.icon, a.label, a.color, a.actionLabel, Modifier.weight(1f), a.onClick)
                        }
                        if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            item {
                AIInsightCard(
                    text = chatState.quickTip.orEmpty(),
                    isLoading = chatState.quickTip == null,
                    title = if (s.isThai) "คำแนะนำจาก NongRak AI" else "NongRak AI Tip"
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (s.isThai) "บันทึกล่าสุด" else "Recent logs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.SmartTrends.route) }) {
                        Text(if (s.isThai) "ดูประวัติทั้งหมด" else "View all", style = MaterialTheme.typography.labelMedium, color = Primary)
                    }
                }
            }
            if (activity.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ยังไม่มีบันทึกวันนี้" else "No logs yet today", color = Outline)
                    }
                }
            } else {
                items(activity) { a -> ActivityRow(a) { navController.navigate(a.route) } }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun QuickLogTile(icon: ImageVector, label: String, color: Color, actionLabel: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = LocalStrings.current
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (s.isThai) "แตะเพื่อบันทึก" else "ONE-TAP LOG",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(0.75f), fontWeight = FontWeight.Bold
                    )
                    Text(actionLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(item.icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
        }
    }
}

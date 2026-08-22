@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.QuickAction
import com.lullabyai.app.data.QuickActionCatalog
import com.lullabyai.app.data.QuickActionCategory
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant
import java.time.LocalTime

private data class ActivityItem(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val timestamp: Instant,
    val route: String
)

@Composable
fun QuickLogHubScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(QuickActionCategory.ALL) }
    val keyboard = LocalSoftwareKeyboardController.current

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
                    add(
                        ActivityItem(
                            if (log.sleepType == "nap") Icons.Filled.WbSunny else Icons.Filled.Bedtime,
                            if (isThai) "ตื่นนอน" else "Woke up",
                            (if (isThai) "เวลานอน: " else "Slept: ") + (TimeUtils.localTime(log.startTime) ?: ""),
                            t, Screen.SleepTracker.route
                        )
                    )
                }
            }
            state.healthRecords.forEach { rec: HealthRecord ->
                TimeUtils.parseInstant(rec.recordedAt)?.let { t ->
                    add(
                        ActivityItem(
                            Icons.Filled.Thermostat,
                            rec.title.ifBlank { if (isThai) "บันทึกสุขภาพ" else "Health log" },
                            if (isThai) "บันทึกอาการแล้ว" else "Symptom logged",
                            t, Screen.ChildHealth.route
                        )
                    )
                }
            }
            state.milestones.filter { it.isAchieved }.forEach { m: DevelopmentMilestone ->
                TimeUtils.parseInstant(m.achievedAt)?.let { t ->
                    add(
                        ActivityItem(
                            Icons.Filled.Star,
                            m.milestoneName,
                            if (isThai) "ทำได้แล้ว" else "Achieved",
                            t, Screen.DevelopmentHistory.route
                        )
                    )
                }
            }
            todayFoodLogs.forEach { food: FoodLog ->
                TimeUtils.parseInstant(food.createdAt)?.let { t ->
                    add(
                        ActivityItem(
                            Icons.Filled.Restaurant,
                            food.itemName,
                            (food.calories?.let { "$it kcal" } ?: ""),
                            t, Screen.Nutrition.route
                        )
                    )
                }
            }
        }.sortedByDescending { it.timestamp }.take(5)
    }

    LaunchedEffect(child?.id, activity.size) {
        child?.let {
            val summary = if (activity.isEmpty()) (if (isThai) "ยังไม่มีการบันทึกวันนี้" else "No logs yet today")
            else activity.joinToString("\n") { a -> "${a.title} (${TimeUtils.localTime(a.timestamp.toString()) ?: ""})" }
            chatViewModel.loadQuickTip(it, summary, isThai)
        }
    }

    // AI Smart Recommendations based on current time and child health state
    val currentHour = remember { LocalTime.now().hour }
    val isChildIll = remember(state.healthRecords) { state.healthRecords.any { it.title.contains("ไข้") || it.title.contains("ป่วย") } }
    val isSleeping = state.activeSleepSession != null

    val aiRecommendations = remember(currentHour, isChildIll, isSleeping) {
        QuickActionCatalog.getAiRecommended(
            hourOfDay = currentHour,
            isIll = isChildIll,
            activeSleepSession = isSleeping
        )
    }

    val filteredActions = remember(searchQuery, selectedCategory) {
        QuickActionCatalog.search(searchQuery, selectedCategory)
    }

    val timeGreeting = remember(currentHour, isThai) {
        when (currentHour) {
            in 5..10 -> if (isThai) "ช่วงเช้า" else "Morning"
            in 11..16 -> if (isThai) "ช่วงบ่าย" else "Afternoon"
            in 17..21 -> if (isThai) "ช่วงค่ำ" else "Evening"
            else -> if (isThai) "ช่วงกลางคืน" else "Night"
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ศูนย์บันทึกด่วน & การใช้งาน" else "Quick Actions & Hub",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.SmartTrends.route) }) {
                        Icon(Icons.Filled.Insights, if (isThai) "แนวโน้มอัจฉริยะ" else "Trends", tint = Primary)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // 1. Hero Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (isThai) "ค้นหาทุกการใช้งาน เช่น นม, นอน, วัคซีน, แคลอรี่, ฟัน..."
                            else "Search everything… milk, sleep, vaccine, calories...",
                            color = Outline,
                            fontSize = 13.5.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, if (isThai) "ล้าง" else "Clear", tint = Outline)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(100.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // 2. AI Recommended Menu Banner & Action Cards (Visible when not actively searching)
            if (searchQuery.isBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(listOf(Primary.copy(0.35f), Color(0xFF9C27B0).copy(0.35f)))
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(listOf(Primary, Color(0xFF9C27B0)))
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            if (isThai) "เมนูแนะนำจาก AI ($timeGreeting)" else "AI Recommended ($timeGreeting)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (isThai) "วิเคราะห์ตามเวลาและข้อมูลสุขภาพของน้อง${child?.name ?: ""}"
                                            else "Tailored for ${child?.name ?: "your child"} right now",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Primary.copy(0.1f)
                                ) {
                                    Text(
                                        "AI SMART",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Primary
                                    )
                                }
                            }

                            // 2x2 Bento Grid for Top AI Recommended Actions
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                aiRecommendations.chunked(2).forEach { rowActions ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowActions.forEach { action ->
                                            AiRecommendedCard(
                                                action = action,
                                                isThai = isThai,
                                                modifier = Modifier.weight(1f),
                                                onClick = { navController.navigate(action.route) }
                                            )
                                        }
                                        if (rowActions.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Fast One-Tap Logging Bar
            if (searchQuery.isBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isThai) "บันทึกด่วนใน 1 แตะ" else "One-Tap Fast Log",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSleeping) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xFF5E35B1).copy(0.12f)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF5E35B1))
                                        )
                                        Text(
                                            if (isThai) "กำลังนอนหลับ" else "Sleeping",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5E35B1)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OneTapPill(
                                icon = Icons.Filled.BabyChangingStation,
                                label = if (isThai) "ดื่มนม" else "Milk",
                                color = Color(0xFF00897B),
                                modifier = Modifier.weight(1f)
                            ) { navController.navigate(Screen.MilkRecord.route) }

                            OneTapPill(
                                icon = if (isSleeping) Icons.Filled.Hotel else Icons.Filled.Bedtime,
                                label = if (isSleeping) (if (isThai) "หยุดนอน" else "Stop Sleep") else (if (isThai) "เริ่มนอน" else "Sleep"),
                                color = Color(0xFF5E35B1),
                                modifier = Modifier.weight(1f)
                            ) { childViewModel.toggleSleepSession() }

                            OneTapPill(
                                icon = Icons.Filled.Thermostat,
                                label = if (isThai) "วัดไข้" else "Fever",
                                color = Color(0xFFE53935),
                                modifier = Modifier.weight(1f)
                            ) { navController.navigate(Screen.AddHealthRecord.route) }

                            OneTapPill(
                                icon = Icons.Filled.WaterDrop,
                                label = if (isThai) "ผ้าอ้อม" else "Diaper",
                                color = Color(0xFF0288D1),
                                modifier = Modifier.weight(1f)
                            ) { navController.navigate(Screen.WasteRecord.route) }

                            OneTapPill(
                                icon = Icons.Filled.Restaurant,
                                label = if (isThai) "อาหาร" else "Meal",
                                color = Color(0xFFFB8C00),
                                modifier = Modifier.weight(1f)
                            ) { navController.navigate(Screen.CalorieCalculator.route) }
                        }
                    }
                }
            }

            // 4. Category Selector Tabs (Universal Everything Access)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (searchQuery.isNotBlank()) {
                                if (isThai) "ผลการค้นหา (${filteredActions.size})" else "Search Results (${filteredActions.size})"
                            } else {
                                if (isThai) "เข้าถึงทุกฟังก์ชันในแอป (${filteredActions.size})" else "All App Features (${filteredActions.size})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (searchQuery.isNotBlank()) {
                            TextButton(onClick = { searchQuery = "" }) {
                                Text(if (isThai) "ดูทั้งหมด" else "View all", color = Primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(QuickActionCategory.entries.toTypedArray()) { cat ->
                            val isSelected = selectedCategory == cat
                            val count = remember(cat) {
                                if (cat == QuickActionCategory.ALL) QuickActionCatalog.all.size
                                else QuickActionCatalog.all.count { it.category == cat }
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                leadingIcon = {
                                    Icon(
                                        cat.icon,
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) Color.White else Primary
                                    )
                                },
                                label = {
                                    Text(
                                        "${if (isThai) cat.labelTh else cat.labelEn} ($count)",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(100.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) null else FilterChipDefaults.filterChipBorder(
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(0.7f),
                                    enabled = true,
                                    selected = false
                                )
                            )
                        }
                    }
                }
            }

            // 5. Universal Categorized Grid of Action Cards
            if (filteredActions.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.SearchOff, null, tint = Outline, modifier = Modifier.size(32.dp))
                            Text(
                                if (isThai) "ไม่พบฟังก์ชันที่ค้นหา ลองค้นหาด้วยคำอื่น เช่น นม, ยา, นอน"
                                else "No matches found. Try searching milk, sleep, vaccine…",
                                color = Outline,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                filteredActions.chunked(2).forEach { rowActions ->
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowActions.forEach { action ->
                                UniversalActionTile(
                                    action = action,
                                    isThai = isThai,
                                    modifier = Modifier.weight(1f),
                                    onClick = { navController.navigate(action.route) }
                                )
                            }
                            if (rowActions.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 6. AI Health Insight Card
            if (searchQuery.isBlank()) {
                item {
                    AIInsightCard(
                        text = chatState.quickTip.orEmpty(),
                        isLoading = chatState.quickTip == null,
                        title = if (isThai) "คำแนะนำจาก NongRak AI" else "NongRak AI Tip"
                    )
                }
            }

            // 7. Recent Activity Stream
            if (searchQuery.isBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isThai) "บันทึกล่าสุดวันนี้" else "Recent logs today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { navController.navigate(Screen.SmartTrends.route) }) {
                            Text(
                                if (isThai) "ดูประวัติทั้งหมด" else "View all",
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (activity.isEmpty()) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isThai) "ยังไม่มีบันทึกกิจกรรมวันนี้ เริ่มแตะบันทึกด้านบนได้เลย" else "No logs yet today. Tap above to log!",
                                    color = Outline,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    items(activity) { a ->
                        ActivityRow(a) { navController.navigate(a.route) }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * AI Recommended Highlight Card
 */
@Composable
private fun AiRecommendedCard(
    action: QuickAction,
    isThai: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = action.iconBg.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, action.iconTint.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(action.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = action.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isThai) action.labelTh else action.labelEn,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                action.subtitleTh?.let { subTh ->
                    Text(
                        text = if (isThai) subTh else (action.subtitleEn ?: subTh),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = action.iconTint.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * One-Tap Pill Fast Logging Button
 */
@Composable
private fun OneTapPill(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

/**
 * Universal In-App Action Card Tile (Grid Item)
 */
@Composable
private fun UniversalActionTile(
    action: QuickAction,
    isThai: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(action.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = action.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isThai) action.labelTh else action.labelEn,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                action.subtitleTh?.let { subTh ->
                    Text(
                        text = if (isThai) subTh else (action.subtitleEn ?: subTh),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (item.detail.isNotBlank()) {
                    Text(
                        item.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
        }
    }
}


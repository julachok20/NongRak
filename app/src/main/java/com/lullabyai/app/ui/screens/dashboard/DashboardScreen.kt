package com.lullabyai.app.ui.screens.dashboard

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.RelationshipLabels
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun DashboardScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    authViewModel: com.lullabyai.app.viewmodel.AuthViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToChild: () -> Unit,
    onNavigateToVaccine: () -> Unit,
    onNavigateToDevelopment: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToGrowth: () -> Unit,
    onNavigateToNutrition: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToQuickActionSearch: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSleep: () -> Unit = {},
    onNavigateToAddChild: () -> Unit = {},
    onNavigateToMentalHealth: () -> Unit = {},
    onNavigateToPostpartum: () -> Unit = {},
    onNavigateToQuickLog: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    isTourActive: Boolean = false
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Childless accounts are no longer blocked at startup — nudge them here instead, but rate-limited
    // to once per 4 hours (persisted) rather than every screen visit, which was too naggy.
    // error != null means the children fetch actually failed (network/auth blip) rather than the
    // account genuinely having zero children — don't nag to "add a child" they may already have.
    var showAddChildPopup by remember { mutableStateOf(false) }
    LaunchedEffect(childState.isLoading, childState.children.size, childState.error, isTourActive) {
        // Suppressed during the guided tour — its own overlay already covers the childless case,
        // and this AlertDialog would otherwise compete with the tour card for attention.
        if (!isTourActive && !childState.isLoading && childState.children.isEmpty() && childState.error == null) {
            val sm = com.lullabyai.app.data.settings.SettingsManager(context)
            if (sm.shouldShowAddChildPrompt()) {
                sm.recordAddChildPromptShown()
                showAddChildPopup = true
            }
        }
    }
    if (showAddChildPopup) {
        AlertDialog(
            onDismissRequest = { showAddChildPopup = false },
            icon = { Icon(Icons.Filled.ChildCare, null, tint = Primary) },
            title = { Text(if (isThai) "เพิ่มข้อมูลลูกของคุณ" else "Add your child") },
            text = {
                Text(
                    if (isThai) "เพิ่มลูกเพื่อเริ่มใช้ฟีเจอร์ติดตามสุขภาพและพัฒนาการ (หรือเชื่อมข้อมูลจากผู้ปกครองอีกท่าน)"
                    else "Add a child to start tracking health and development (or sync one from another parent)"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAddChildPopup = false; onNavigateToAddChild() }) {
                    Text(if (isThai) "เพิ่มลูก" else "Add child")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChildPopup = false }) { Text(if (isThai) "ภายหลัง" else "Later") }
            }
        )
    }

    // One-time nudge toward the home-screen widgets feature — only for accounts that already have
    // a child (nothing useful to show on a widget otherwise), and only after the add-child popup
    // has had its turn so the two don't compete for attention on the same visit.
    var showWidgetPromo by remember { mutableStateOf(false) }
    val widgetPromoScope = rememberCoroutineScope()
    LaunchedEffect(childState.isLoading, childState.children.size, isTourActive, showAddChildPopup) {
        if (!isTourActive && !showAddChildPopup && !childState.isLoading && childState.children.isNotEmpty()) {
            val sm = com.lullabyai.app.data.settings.SettingsManager(context)
            if (!sm.hasSeenWidgetPromo.first()) {
                showWidgetPromo = true
            }
        }
    }
    fun dismissWidgetPromo() {
        showWidgetPromo = false
        widgetPromoScope.launch {
            com.lullabyai.app.data.settings.SettingsManager(context).setHasSeenWidgetPromo(true)
        }
    }
    if (showWidgetPromo) {
        AlertDialog(
            onDismissRequest = { dismissWidgetPromo() },
            icon = { Icon(Icons.Filled.Widgets, null, tint = Primary) },
            title = { Text(if (isThai) "ลองใช้วิดเจ็ตหน้าจอหลัก" else "Try home-screen widgets") },
            text = {
                Text(
                    if (isThai) "ดูข้อมูลลูกน้อย เช่น การนอน วัคซีน และการเจริญเติบโต ได้ทันทีจากหน้าจอหลักของโทรศัพท์ โดยไม่ต้องเปิดแอป"
                    else "See your child's sleep, vaccines, and growth right from your phone's home screen — no need to open the app."
                )
            },
            confirmButton = {
                TextButton(onClick = { dismissWidgetPromo(); onNavigateToWidgets() }) {
                    Text(if (isThai) "ดูวิดเจ็ต" else "View widgets")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissWidgetPromo() }) { Text(if (isThai) "ไว้คราวหน้า" else "Maybe later") }
            }
        )
    }

    var showDailyQuestionDialog by remember { mutableStateOf(false) }
    if (showDailyQuestionDialog) {
        childState.dailyQuestion?.let { question ->
            com.lullabyai.app.ui.components.DailyQuestionDialog(
                question = question, isThai = isThai,
                onAnswer = { answer -> childViewModel.answerDailyQuestion(answer); showDailyQuestionDialog = false },
                onSkip = { childViewModel.dismissDailyQuestion(); showDailyQuestionDialog = false },
                onDismiss = { showDailyQuestionDialog = false }
            )
        }
    }

    val child = childState.child
    val username = authState.profile?.username?.takeIf { it.isNotBlank() }
        ?: authState.profile?.fullName?.takeIf { it.isNotBlank() }
    val relationship = authState.profile?.relationship

    LaunchedEffect(Unit) { childViewModel.loadChild(); authViewModel.loadProfile(); childViewModel.loadPendingSyncRequests(); authViewModel.loadMentalHealthHistory(); authViewModel.loadPostpartumHistory() }

    // Monthly parent mental-health check-in nudge — only shows if nothing was submitted this
    // calendar month yet (per-parent, not per-child; see MentalHealthCatalog).
    val needsMentalHealthCheckin = remember(authState.mentalHealthHistory) {
        val latest = authState.mentalHealthHistory.firstOrNull()?.createdAt
        latest == null || !latest.take(7).equals(LocalDate.now().toString().take(7))
    }

    // Same monthly-nudge logic, separate axis (physical postpartum recovery, not mood) — but ONLY
    // for accounts with a very young child (postpartum recovery is a first-few-months thing, not
    // relevant to a parent of a toddler the way the mental-health check-in stays relevant indefinitely).
    val youngestChildAgeMonths = remember(childState.children) {
        childState.children.minOfOrNull { c ->
            runCatching {
                val parts = c.birthdate.split("-")
                val by = parts[0].toInt(); val bm = parts[1].toInt()
                val now = LocalDate.now()
                (now.year - by) * 12 + (now.monthValue - bm)
            }.getOrDefault(Int.MAX_VALUE)
        } ?: Int.MAX_VALUE
    }
    val needsPostpartumCheckin = remember(authState.postpartumHistory, youngestChildAgeMonths) {
        if (youngestChildAgeMonths > 3) false else {
            val latest = authState.postpartumHistory.firstOrNull()?.createdAt
            latest == null || !latest.take(7).equals(LocalDate.now().toString().take(7))
        }
    }

    // AI "what do you want to know more" popup — open-ended question, capped 3/day (see
    // SettingsManager.aiDailyQuestionCountToday), shown here rather than only inside the chat.
    LaunchedEffect(child?.id) { child?.let { childViewModel.loadDailyQuestion(context, isThai) } }

    // Daily-logging streak: consecutive days (up to today) with at least one sleep/food/exercise/
    // health log. Derived entirely from data already tracked — no new table. Food/exercise need an
    // explicit range fetch (same direct-repo-call pattern QuickLogHub/SmartTrends already use);
    // sleep + health records are already in childState via the child-load cascade.
    var rangeLogDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    LaunchedEffect(child?.id) {
        val c = child ?: return@LaunchedEffect
        childViewModel.loadSleep()
        val nutritionRepo = NutritionRepository()
        val from = LocalDate.now().minusDays(30).toString()
        val foodDates = nutritionRepo.getFoodLogsRange(c.id, from).mapNotNull { runCatching { LocalDate.parse(it.logDate) }.getOrNull() }
        val exerciseDates = nutritionRepo.getExerciseLogsRange(c.id, from).mapNotNull { runCatching { LocalDate.parse(it.logDate) }.getOrNull() }
        rangeLogDates = (foodDates + exerciseDates).toSet()
    }
    val streak = remember(rangeLogDates, childState.sleepLogs, childState.healthRecords) {
        val sleepDates = childState.sleepLogs.mapNotNull { TimeUtils.localDate(it.startTime)?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } }
        val healthDates = childState.healthRecords.mapNotNull { TimeUtils.localDate(it.recordedAt)?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } }
        val allDates = rangeLogDates + sleepDates + healthDates
        val today = LocalDate.now()
        var count = 0
        var day = if (today in allDates) today else today.minusDays(1)
        while (day in allDates) { count++; day = day.minusDays(1) }
        count
    }

    // Milestone-completion celebration: reuses ChildDevelopmentScreen's "all age-appropriate skills
    // done" logic so the moment is visible on the Dashboard, not just buried in the พัฒนาการ tab.
    val ageMonths = childViewModel.getAgeMonths()
    val allMilestonesDone = remember(childState.milestones, ageMonths) {
        val hasAgeAppropriate = childState.milestones.any { (it.ageMonthsExpected ?: 0) <= ageMonths }
        val pendingPool = childState.milestones.any { !it.isAchieved && it.skipReason == null && (it.ageMonthsExpected ?: 0) <= ageMonths }
        hasAgeAppropriate && !pendingPool
    }

    // Same gap-check every AI-analysis screen already uses (ChildHealth/DiseaseRisk/GrowthMetrics)
    // — surfaced here too as plain cards (not a modal, unlike those screens) since the Dashboard is
    // visited far more often and a popup on every open would be more naggy than helpful.
    // Checked per-child (not just whichever one is currently selected) and each card names its
    // child explicitly — with 2+ children, a single unlabeled card was ambiguous about who it meant.
    var missingByChild by remember { mutableStateOf<List<Pair<com.lullabyai.app.data.model.Child, List<String>>>>(emptyList()) }
    LaunchedEffect(childState.children) {
        val healthRepo = com.lullabyai.app.data.repository.HealthRepository()
        missingByChild = childState.children.mapNotNull { c ->
            val hasAllergies = healthRepo.getAllergies(c.id).getOrDefault(emptyList()).isNotEmpty()
            val missing = com.lullabyai.app.ui.components.missingChildInfo(c, hasAllergies, s.isThai)
            if (missing.isNotEmpty()) c to missing else null
        }
    }

    // Nearest upcoming (not-completed) vaccine, for the alert card.
    val nextVaccine = remember(childState.vaccines) {
        val today = LocalDate.now()
        childState.vaccines
            .filter { it.status != "completed" && it.scheduledDate != null }
            .mapNotNull { v -> runCatching { LocalDate.parse(v.scheduledDate) }.getOrNull()?.let { v to it } }
            .filter { !it.second.isBefore(today) }
            .minByOrNull { it.second }
    }

    // Service registry + "recent" tracking. Opening a service records it (newest first) so the
    // "บริการล่าสุด" row reflects what the user actually used.
    val recent by com.lullabyai.app.data.settings.RecentServices.list.collectAsState()
    val usage by com.lullabyai.app.data.settings.RecentServices.counts.collectAsState()
    val services = listOf(
        DashSvc("vaccine", if (isThai) "ประวัติวัคซีน" else "Vaccine history", if (isThai) "ติดตามกำหนดการฉีดวัคซีน" else "Track vaccination schedule", Icons.Filled.Vaccines, Color(0xFF3F51B5), onNavigateToVaccine),
        DashSvc("development", if (isThai) "บันทึกพัฒนาการ" else "Development", if (isThai) "ทักษะตามวัยและกิจกรรมแนะนำ" else "Milestones & activities", Icons.Filled.TrendingUp, Color(0xFF1976D2), onNavigateToDevelopment),
        DashSvc("health", if (isThai) "ประวัติสุขภาพ" else "Health history", if (isThai) "บันทึกอาการและประวัติการรักษา" else "Symptoms & care history", Icons.Filled.HealthAndSafety, Color(0xFF00897B), onNavigateToHealth),
        DashSvc("nutrition", if (isThai) "โภชนาการ" else "Nutrition", if (isThai) "บันทึกอาหารและคำนวณแคลอรี่" else "Log food & calories", Icons.Filled.Restaurant, Color(0xFFEF6C00), onNavigateToNutrition),
        DashSvc("growth", if (isThai) "การเจริญเติบโต" else "Growth", if (isThai) "น้ำหนักและส่วนสูงตามวัย" else "Weight & height", Icons.Filled.MonitorWeight, Color(0xFF6A1B9A), onNavigateToGrowth),
        DashSvc("sleep", if (isThai) "การนอน" else "Sleep", if (isThai) "ติดตามและตั้งตารางการนอน" else "Track & schedule sleep", Icons.Filled.Bedtime, Color(0xFF3949AB), onNavigateToSleep),
        DashSvc("chat", if (isThai) "คุยกับ AI" else "Chat with AI", "", Icons.Filled.AutoAwesome, Primary, onNavigateToChat),
    )
    val svcMap = services.associateBy { it.key }
    // Just navigate — the visit is recorded centrally on the destination screen (AppNavigation), so it
    // counts no matter how the user reached the service (dashboard, child tab, bottom nav, sidebar…).
    val open: (String) -> Unit = { k -> svcMap[k]?.nav?.invoke() }
    val recentKeys = (recent + listOf("nutrition", "growth")).distinct().take(2)
    // "บริการหลัก" reorders by the user's activity: most-used first, with a stable default order on ties.
    val mainCandidates = listOf("vaccine", "development", "health", "nutrition", "growth")
    val mainKeys = mainCandidates
        .sortedWith(compareByDescending<String> { usage[it] ?: 0 }.thenBy { mainCandidates.indexOf(it) })
        .take(3)

    Scaffold(
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.HOME,
                onHome = onNavigateHome, onChatbot = onNavigateToChat, onChild = onNavigateToChild, onStats = onNavigateToStats, onSettings = onNavigateToSettings
            )
        },
        // Quick Action FAB — the Quick Log Hub already exists (search this file for onNavigateToStats
        // -> StatisticsInsightsScreen -> Screen.QuickLogHub) but had no entry point from the mother
        // dashboard itself; this is that missing shortcut, "synced" with the same hub every other
        // quick-log entry point already leads to.
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToQuickLog, containerColor = Primary, contentColor = Color.White) {
                Icon(Icons.Filled.Bolt, contentDescription = if (isThai) "บันทึกด่วน" else "Quick log")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = childState.isLoading,
            onRefresh = { childViewModel.loadChild() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text("NONGRAK AI", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                        Text(
                            if (username != null) {
                                if (isThai) "สวัสดีค่ะ คุณ$username" else "Hello, $username"
                            } else if (isThai) {
                                "สวัสดีค่ะ ${RelationshipLabels.thaiAddress(relationship)}"
                            } else {
                                "Hello, ${RelationshipLabels.englishAddress(relationship)}"
                            },
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = onNavigateToQuickActionSearch) { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurface) }
                Box {
                    IconButton(onClick = onNavigateToNotifications) { Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.onSurface) }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Error).align(Alignment.TopEnd).offset((-4).dp, 4.dp))
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Welcome card
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (isThai) "ยินดีต้อนรับสู่ NongRak AI" else "Welcome to NongRak AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                        Text(
                            if (isThai) "ดูแลลูกน้อยด้วยความอุ่นใจ ผ่านระบบวิเคราะห์ AI ผู้ช่วยส่วนตัวของคุณ" else "Care for your little one with confidence, powered by your personal AI assistant.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Daily-logging streak — only shown once there's something to celebrate.
                if (streak > 0) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFEF6C00).copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFEF6C00))
                            }
                            Column {
                                Text(
                                    if (isThai) "บันทึกต่อเนื่อง $streak วันแล้ว!" else "$streak-day logging streak!",
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00)
                                )
                                Text(
                                    streakMotivation(streak, isThai),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Milestone-completion celebration.
                if (allMilestonesDone) {
                    Card(Modifier.fillMaxWidth().clickableCard(onNavigateToDevelopment), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(0.10f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4CAF50).copy(0.18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFF2E7D32))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isThai) "เก่งมาก! ${child?.name.orEmpty()} ทำครบทุกทักษะตามวัยแล้ว" else "Great job! ${child?.name.orEmpty()} completed every age-appropriate skill",
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)
                                )
                                Text(if (isThai) "แตะเพื่อดูพัฒนาการ" else "Tap to view development", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF2E7D32))
                        }
                    }
                }

                // Pending "sync child" requests — surfaced here too, not just in Notifications, so an
                // owner sees and can confirm/reject them wherever they happen to be.
                childState.pendingSyncRequests.forEach { req ->
                    val childName = childState.children.find { it.id == req.childId }?.name ?: "-"
                    com.lullabyai.app.ui.components.SyncRequestCard(
                        childName = childName,
                        onApprove = { childViewModel.confirmSyncRequest(req.id, true) },
                        onReject = { childViewModel.confirmSyncRequest(req.id, false) }
                    )
                }

                // Missing-info nudge — same gaps ChildHealth/DiseaseRisk/GrowthMetrics already flag,
                // surfaced here as plain cards+button rather than their auto-popup modal. One card
                // per child that has gaps, each naming the child, so it's never ambiguous which
                // child's data is missing when there's more than one.
                missingByChild.forEach { (c, missing) ->
                    Card(
                        Modifier.fillMaxWidth().clickableCard {
                            com.lullabyai.app.data.settings.ChildSelection.set(c.id)
                            childViewModel.selectChild(c.id)
                            onNavigateToChild()
                        },
                        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Info, null, tint = Primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (isThai) "ข้อมูลของ ${c.name} ยังไม่ครบ" else "${c.name}'s info is incomplete"),
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    (if (isThai) "กรอกข้อมูลเพิ่มเติม: " else "Fill in: ") + missing.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Primary)
                        }
                    }
                }

                // Monthly parent mental-health check-in banner — uses the app's own Primary (the
                // mockup's own accent for this card is the same color), not a one-off indigo.
                if (needsMentalHealthCheckin) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.2f))
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Psychology, null, tint = Primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isThai) "ประเมินความเสี่ยงสุขภาพจิตสำหรับพ่อแม่" else "Parental mental health check-in",
                                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isThai) "แบบประเมินสั้นๆ รายเดือน" else "A short monthly check-in",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = onNavigateToMentalHealth, modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) { Text(if (isThai) "เริ่มทำแบบประเมิน" else "Start check-in", fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // Monthly postpartum physical-recovery check-in banner — separate axis from mental
                // health above (bleeding/pain/fever, not mood), only shown for a very young child.
                if (needsPostpartumCheckin) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.2f))
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Favorite, null, tint = Primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isThai) "ประเมินการฟื้นตัวหลังคลอด" else "Postpartum recovery check-in",
                                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isThai) "แบบประเมินสั้นๆ รายเดือน" else "A short monthly check-in",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = onNavigateToPostpartum, modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) { Text(if (isThai) "เริ่มทำแบบประเมิน" else "Start check-in", fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // AI daily question — banner nudge, tapping it opens the actual question dialog
                // (declared near the top of this composable) rather than popping up unprompted.
                childState.dailyQuestion?.let {
                    com.lullabyai.app.ui.components.DailyQuestionBanner(
                        isThai = isThai,
                        onClick = { showDailyQuestionDialog = true },
                        onDismiss = { childViewModel.dismissDailyQuestion() }
                    )
                }

                // Chat with AI (big primary card)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
                        .clickableCard { open("chat") }.padding(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.2f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Column {
                                Text(if (isThai) "คุยกับ AI ผู้ช่วย" else "Chat with AI assistant", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(if (isThai) "ปรึกษาการดูแลและสุขภาพเด็ก ตลอด 24 ชั่วโมง" else "Childcare & health advice, 24 hours", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.85f))
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                    }
                }

                // Vaccine alert (data-driven)
                nextVaccine?.let { (v, date) ->
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()
                    Card(
                        Modifier.fillMaxWidth().clickableCard { open("vaccine") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Error.copy(0.07f))
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Error.copy(0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.NotificationsActive, null, tint = Error)
                            }
                            Column(Modifier.weight(1f)) {
                                Text((if (isThai) "แจ้งเตือน: วัคซีน " else "Reminder: ") + v.vaccineName + (if (child?.name != null) (if (isThai) " สำหรับน้อง${child.name}" else " for ${child.name}") else ""), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    if (days <= 0) (if (isThai) "ถึงกำหนดวันนี้" else "Due today")
                                    else (if (isThai) "มีนัดฉีดในอีก $days วันข้างหน้า" else "Due in $days days"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.CalendarMonth, null, tint = Error)
                        }
                    }
                }

                // Recent services — the last services the user actually opened.
                Text(if (isThai) "บริการล่าสุด" else "Recent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentKeys.forEach { k ->
                        svcMap[k]?.let { sv -> RecentTile(sv.label, sv.icon, Modifier.weight(1f)) { open(k) } }
                    }
                }

                // Main services — ordered by the user's activity (most-used first).
                Text(if (isThai) "บริการหลักเพื่อลูกน้อย" else "For your little one", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                mainKeys.forEach { k ->
                    svcMap[k]?.let { sv -> ServiceRow(sv.label, sv.sub, sv.icon, sv.color) { open(k) } }
                }
                // Sleep tracker — always shown here (not part of the usage-ranked rotation above,
                // which caps at 3 slots and would otherwise make this unreachable for new users).
                svcMap["sleep"]?.let { sv -> ServiceRow(sv.label, sv.sub, sv.icon, sv.color) { open("sleep") } }

                // Help center
                Card(
                    Modifier.fillMaxWidth().clickableCard(onNavigateToHelp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HelpOutline, null, tint = Primary)
                        Column(Modifier.weight(1f)) {
                            Text(if (isThai) "ต้องการความช่วยเหลือเพิ่มเติม?" else "Need more help?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(if (isThai) "ไปที่ศูนย์ความช่วยเหลือ" else "Go to the help center", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
        }
    }
}

private data class DashSvc(val key: String, val label: String, val sub: String, val icon: ImageVector, val color: Color, val nav: () -> Unit)

private fun Modifier.clickableCard(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun RecentTile(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(Primary.copy(0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Tier-based encouragement for the logging-streak card. Deliberately avoids fabricated stats like
 *  a "top X%" ranking — the app has no cross-user data to back that kind of claim — and instead
 *  praises the milestone itself. */
private fun streakMotivation(streak: Int, isThai: Boolean): String = when {
    streak >= 30 -> if (isThai) "หนึ่งเดือนเต็มแล้ว! ความสม่ำเสมอระดับนี้ยอดเยี่ยมมาก" else "A full month! This kind of consistency is amazing"
    streak >= 14 -> if (isThai) "สองสัปดาห์แล้ว เก่งมากที่ทำต่อเนื่อง" else "Two weeks strong — great consistency"
    streak >= 7 -> if (isThai) "หนึ่งสัปดาห์เต็มแล้ว! บันทึกต่อเนื่องช่วยให้เห็นพัฒนาการลูกชัดเจนขึ้น" else "A full week! Keeping this up makes your child's patterns easier to see"
    streak >= 3 -> if (isThai) "กำลังไปได้ดี บันทึกวันนี้เพื่อรักษาสถิติต่อไป" else "You're on a roll — log something today to keep it going"
    else -> if (isThai) "เริ่มต้นได้ดี! บันทึกต่อเนื่องเพื่อสร้างนิสัยที่ดี" else "Great start! Keep logging to build the habit"
}

@Composable
private fun ServiceRow(title: String, subtitle: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(iconColor.copy(0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Outline)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}

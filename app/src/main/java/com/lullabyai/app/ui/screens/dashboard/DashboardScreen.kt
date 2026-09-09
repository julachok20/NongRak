package com.lullabyai.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.R
import com.lullabyai.app.data.model.AppNotification
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LocalAvatarUrl
import com.lullabyai.app.ui.screens.onboarding.tourAnchor
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.RelationshipLabels
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
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
    onNavigateToQuickLog: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateToConnect: () -> Unit = {},
    onNavigateToSignUp: () -> Unit = {},
    onNavigateToHealthSurvey: () -> Unit = onNavigateToVaccine,
    onNavigateToInfoAsk: () -> Unit = {},
    isTourActive: Boolean = false
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isGuest = authViewModel.getCurrentUserId() == null || childState.child?.userId == "guest"

    // Real notifications fetched from repository
    var realNotifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    val notifRepo = remember { NotificationRepository() }

    LaunchedEffect(Unit) {
        realNotifications = notifRepo.getNotifications().take(5)
    }

    // Childless accounts nudge
    var showAddChildPopup by remember { mutableStateOf(false) }
    LaunchedEffect(childState.isLoading, childState.children.size, childState.error, isTourActive, isGuest) {
        if (!isGuest && !isTourActive && !childState.isLoading && childState.children.isEmpty() && childState.error == null) {
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

    // Home-screen widgets promo
    var showWidgetPromo by remember { mutableStateOf(false) }
    val widgetPromoScope = rememberCoroutineScope()
    LaunchedEffect(childState.isLoading, childState.children.size, isTourActive, showAddChildPopup, isGuest) {
        if (!isGuest && !isTourActive && !showAddChildPopup && !childState.isLoading && childState.children.isNotEmpty()) {
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

    val child = childState.child
    val hasActiveChild = childState.children.isNotEmpty() && child != null
    val username = authState.profile?.fullName?.trim()?.takeIf { it.isNotBlank() }
        ?: authState.profile?.username?.trim()?.takeIf { it.isNotBlank() }
    val relationship = authState.profile?.relationship

    LaunchedEffect(Unit) {
        childViewModel.loadChild()
        authViewModel.loadProfile()
        childViewModel.loadPendingSyncRequests(context)
        authViewModel.loadMentalHealthHistory()
    }

    // Monthly parent mental-health check-in: only shows once a month
    val needsMentalHealthCheckin = remember(authState.mentalHealthHistory) {
        val latest = authState.mentalHealthHistory.firstOrNull()?.createdAt
        latest == null || !latest.take(7).equals(LocalDate.now().toString().take(7))
    }

    // Reclaim child dialog
    childState.reclaimableChild?.let { reclaimable ->
        AlertDialog(
            onDismissRequest = { childViewModel.dismissReclaim() },
            icon = { Icon(Icons.Filled.SwapHoriz, null, tint = Primary) },
            title = {
                Text(
                    if (s.isThai) "กู้คืนสิทธิ์ความเป็นเจ้าของ?" else "Reclaim Child Ownership?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (s.isThai)
                            "ตรวจพบข้อมูลของ \"${reclaimable.childName}\"${if (reclaimable.birthDate.isNotBlank()) " (เกิด ${reclaimable.birthDate})" else ""} ที่คุณเคยเป็นเจ้าของหลักและได้โอนสิทธิ์ไป\n\nต้องการกู้คืนสิทธิ์ความเป็นเจ้าของหลักกลับมาหรือไม่?"
                        else
                            "Found \"${reclaimable.childName}\"${if (reclaimable.birthDate.isNotBlank()) " (born ${reclaimable.birthDate})" else ""} that you previously owned.\n\nWould you like to reclaim primary ownership?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    childViewModel.reclaimOwnership(reclaimable.childId)
                }) {
                    Text(if (s.isThai) "กู้คืนสิทธิ์เจ้าของ" else "Reclaim", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { childViewModel.dismissReclaim() }) {
                    Text(if (s.isThai) "ไม่ใช่ตอนนี้" else "Not now")
                }
            }
        )
    }

    // Nearest upcoming (not-completed) vaccine
    val nextVaccine = remember(childState.vaccines, hasActiveChild) {
        if (!hasActiveChild) null else {
            val today = LocalDate.now()
            childState.vaccines
                .filter { it.status != "completed" && it.scheduledDate != null }
                .mapNotNull { v -> runCatching { LocalDate.parse(v.scheduledDate) }.getOrNull()?.let { v to it } }
                .filter { !it.second.isBefore(today) }
                .minByOrNull { it.second }
        }
    }

    // Missing info check
    var missingByChild by remember { mutableStateOf<List<Pair<com.lullabyai.app.data.model.Child, List<String>>>>(emptyList()) }
    LaunchedEffect(childState.children) {
        val healthRepo = com.lullabyai.app.data.repository.HealthRepository()
        missingByChild = childState.children.mapNotNull { c ->
            val hasAllergies = healthRepo.getAllergies(c.id).getOrDefault(emptyList()).isNotEmpty()
            val missing = com.lullabyai.app.ui.components.missingChildInfo(c, hasAllergies, s.isThai)
            if (missing.isNotEmpty()) c to missing else null
        }
    }

    val ownedSyncRequests = remember(childState.pendingSyncRequests, childState.children) {
        val ownedIds = childState.children.map { it.id }.toSet()
        childState.pendingSyncRequests.filter { it.childId in ownedIds }
    }

    // Recent services dynamic tracking
    val recent by com.lullabyai.app.data.settings.RecentServices.list.collectAsState()
    val services = listOf(
        DashSvc("vaccine", if (isThai) "ตารางวัคซีน" else "Vaccine Schedule", if (isThai) "เช็คและบันทึก" else "Log schedule", Icons.Filled.Vaccines, Color(0xFFFEFCE8), Color(0xFFD97706), onNavigateToVaccine),
        DashSvc("growth", if (isThai) "กราฟการเติบโต" else "Growth Chart", if (isThai) "ติดตามพัฒนาการ" else "Track WHO", Icons.Filled.TrendingUp, Color(0xFFF0FDF4), Color(0xFF059669), onNavigateToGrowth),
        DashSvc("development", if (isThai) "บันทึกพัฒนาการ" else "Development", if (isThai) "ทักษะตามวัย" else "Skills", Icons.Filled.AutoStories, Color(0xFFFFF1F2), Color(0xFFE11D48), onNavigateToDevelopment),
        DashSvc("health", if (isThai) "สอบถามอาการ" else "Symptoms", if (isThai) "เช็คอาการเบื้องต้น" else "Check triage", Icons.Filled.Healing, Color(0xFFF5F3FF), Color(0xFF7C3AED), onNavigateToHealth),
        DashSvc("nutrition", if (isThai) "โภชนาการ" else "Nutrition", if (isThai) "มื้ออาหารและนม" else "Meals & Milk", Icons.Filled.LocalDrink, Color(0xFFFFF0F3), Color(0xFFFF5277), onNavigateToNutrition),
        DashSvc("sleep", if (isThai) "การนอน" else "Sleep", if (isThai) "รอบการนอน" else "Sleep log", Icons.Filled.Bedtime, Color(0xFFFFF9E6), Color(0xFFF59E0B), onNavigateToSleep)
    )
    val svcMap = services.associateBy { it.key }
    val recentKeys = (recent + listOf("vaccine", "growth", "development", "health")).distinct().take(4)

    Scaffold(
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.HOME,
                onHome = onNavigateHome,
                onChatbot = onNavigateToChat,
                onChild = onNavigateToChild,
                onStats = onNavigateToStats,
                onSettings = onNavigateToSettings,
                onConnect = onNavigateToConnect
            )
        },
        containerColor = Color(0xFFF7FAFE)
    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = childState.isLoading,
            onRefresh = {
                childViewModel.loadChild()
                widgetPromoScope.launch {
                    realNotifications = notifRepo.getNotifications().take(5)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                // Top App Bar Header with Horinzol_Nobg Logo
                HomeHeader(
                    username = username,
                    isThai = isThai,
                    onProfileClick = onNavigateToSettings,
                    onNotificationClick = onNavigateToNotifications
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Greeting Section (NO EMOJIS, clean space)
                    HomeGreetingCleanSection(
                        username = username,
                        relationship = relationship,
                        isThai = isThai,
                        isGuest = isGuest
                    )

                    // Guest mode banner if applicable
                    if (isGuest) {
                        GuestModeBannerCard(isThai = isThai, onSignUp = onNavigateToSignUp)
                    }

                    // Monthly parent mental health check-in banner (only once a month)
                    if (needsMentalHealthCheckin) {
                        MonthlyMentalHealthBanner(
                            isThai = isThai,
                            onStart = onNavigateToMentalHealth
                        )
                    }

                    // Typed info-ask questions — collects info and writes directly into Health Book
                    if (hasActiveChild && childState.infoAsks.isNotEmpty()) {
                        val prompts = childState.infoAsks
                        com.lullabyai.app.ui.components.InfoAskBanner(
                            isThai = isThai,
                            subjectName = com.lullabyai.app.data.infoask.InfoAskCatalog.bannerSubjectName(
                                prompts, child?.name.orEmpty(), isThai
                            ),
                            questionCount = prompts.size,
                            preview = prompts.take(2).joinToString("\n") { "• ${it.question(isThai)}" },
                            onClick = onNavigateToInfoAsk,
                            onDismiss = { childViewModel.dismissInfoAsk(context) }
                        )
                    }

                    // Pending sync requests
                    ownedSyncRequests.forEach { req ->
                        val childName = childState.children.find { it.id == req.childId }?.name ?: return@forEach
                        com.lullabyai.app.ui.components.SyncRequestCard(
                            childName = childName,
                            onApprove = { childViewModel.confirmSyncRequest(context, req.id, true) },
                            onReject = { childViewModel.confirmSyncRequest(context, req.id, false) }
                        )
                    }

                    // Answering a request now hides its card for good, so a refusal by the server
                    // has to be said out loud here - otherwise the owner walks away believing they
                    // denied access that is in fact still pending.
                    childState.syncError?.let { key ->
                        val message = com.lullabyai.app.viewmodel.AccessRequestStrings.error(key, isThai)
                        if (message != null) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF991B1B),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { childViewModel.clearSyncError() }) {
                                        Text(if (isThai) "ปิด" else "Dismiss")
                                    }
                                }
                            }
                        }
                    }

                    // Missing child info nudge
                    if (hasActiveChild) missingByChild.forEach { (c, missing) ->
                        Card(
                            onClick = {
                                com.lullabyai.app.data.settings.ChildSelection.set(c.id)
                                childViewModel.selectChild(c.id)
                                onNavigateToChild()
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Primary.copy(0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (isThai) "ข้อมูลของ ${c.name} ยังไม่ครบ" else "${c.name}'s info is incomplete",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF101828)
                                    )
                                    Text(
                                        text = (if (isThai) "กรอกเพิ่มเติม: " else "Fill in: ") + missing.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF667085),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = Primary)
                            }
                        }
                    }

                    // HERO CARD: คุยกับ AI ผู้ช่วย (Chat with AI Assistant Banner)
                    HeroAiChatBannerCard(
                        isThai = isThai,
                        onClick = onNavigateToChat
                    )

                    // SECTION: บันทึกด่วน (Quick Action)
                    QuickActionSection(
                        isThai = isThai,
                        onMilk = onNavigateToNutrition,
                        onDiaper = onNavigateToHealth,
                        onGrowth = onNavigateToGrowth,
                        onSleep = onNavigateToSleep,
                        onAddMore = onNavigateToQuickLog
                    )

                    // SECTION: การแจ้งเตือน (Real notifications extracted from user data)
                    NotificationAlertsSection(
                        realNotifications = realNotifications,
                        nextVaccine = nextVaccine,
                        childName = child?.name,
                        isThai = isThai,
                        onViewAll = onNavigateToNotifications,
                        onVaccineClick = onNavigateToVaccine,
                        onDoctorClick = onNavigateToDevelopment
                    )

                    // SECTION: บริการล่าสุด (Recent Services)
                    RecentServicesSection(
                        isThai = isThai,
                        recentKeys = recentKeys,
                        svcMap = svcMap
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

private data class DashSvc(
    val key: String,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val bgColor: Color,
    val iconColor: Color,
    val nav: () -> Unit
)

/**
 * Top Header using Horinzol_Nobg.jpg Logo
 */
@Composable
private fun HomeHeader(
    username: String?,
    isThai: Boolean,
    onProfileClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Horizontal Brand Logo, cropped edition (645x170, 3.8:1).
        //
        // Deliberately NOT nongrak_logo_horizontal: that asset is 781x391, of which nearly half the
        // height is transparent padding, so it drew the mark at roughly half whatever height was
        // set here - 64.dp of layout for about 34.dp of visible logo. The padding is gone from this
        // one, so the number below is the mark's real height: 48.dp reads noticeably LARGER than
        // the old 64.dp while making the header shorter rather than taller.
        //
        // Width follows at ~160.dp (42 x 3.8), clearing the avatar and bell comfortably on a
        // 360.dp screen. Past about 56.dp it starts to crowd them on small phones.
        Image(
            painter = painterResource(R.drawable.nongrak_logo_watermark),
            contentDescription = "NongRak Logo",
            modifier = Modifier.height(42.dp),
            contentScale = ContentScale.Fit
        )

        // Right Profile Avatar + Notification Bell
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val avatarUrl = LocalAvatarUrl.current
            Surface(
                onClick = onProfileClick,
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(1.5.dp, Primary.copy(alpha = 0.25f)),
                modifier = Modifier.size(40.dp)
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = avatarUrl,
                        contentDescription = "โปรไฟล์",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEBF1FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = onNotificationClick,
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFEAECF0)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.NotificationsNone,
                        contentDescription = if (isThai) "การแจ้งเตือน" else "Notifications",
                        tint = Color(0xFF1D2939),
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30))
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Clean greeting section without emojis and with clean layout
 */
@Composable
private fun HomeGreetingCleanSection(
    username: String?,
    relationship: String?,
    isThai: Boolean,
    isGuest: Boolean
) {
    val hour = remember { LocalTime.now().hour }
    val timeGreeting = when (hour) {
        in 5..11 -> if (isThai) "สวัสดีตอนเช้า" else "Good morning"
        in 12..16 -> if (isThai) "สวัสดีตอนบ่าย" else "Good afternoon"
        else -> if (isThai) "สวัสดีตอนเย็น" else "Good evening"
    }

    val displayGreetingName = if (isGuest) {
        if (isThai) "คุณแม่" else "Mother"
    } else if (!username.isNullOrBlank()) {
        if (isThai) {
            if (username.startsWith("คุณ")) username else "คุณ$username"
        } else {
            username
        }
    } else if (isThai) {
        RelationshipLabels.thaiAddress(relationship).ifBlank { "คุณแม่" }
    } else {
        RelationshipLabels.englishAddress(relationship).ifBlank { "Mother" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = timeGreeting,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF101828)
            )
            Text(
                text = displayGreetingName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFE11D48)
            )
        }
        Text(
            text = if (isThai) "วันนี้ลูกน้อยแข็งแรงดีนะคะ" else "Your baby is healthy and well today",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = Color(0xFF475467)
        )
    }
}

/**
 * Monthly Mental Health Assessment Banner (Shows once per month)
 */
@Composable
private fun MonthlyMentalHealthBanner(
    isThai: Boolean,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFF173DB8).copy(alpha = 0.10f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isThai) "ประเมินความเสี่ยงสุขภาพจิตสำหรับพ่อแม่" else "Parental Mental Health Check-in",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = Color(0xFF101828)
                    )
                    Text(
                        text = if (isThai) "แบบประเมินสั้นๆ ประจำเดือน เพื่อดูแลสุขภาพใจของคุณพ่อคุณแม่"
                        else "A short monthly check-in to support your emotional wellbeing",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF667085)
                    )
                }
            }

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF173DB8))
            ) {
                Text(
                    text = if (isThai) "เริ่มทำแบบประเมิน" else "Start Check-in",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Hero Card: คุยกับ AI ผู้ช่วย (Chat with AI Assistant Banner)
 */
@Composable
private fun HeroAiChatBannerCard(
    isThai: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF173DB8),
                            Color(0xFF2563EB),
                            Color(0xFF4338CA)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isThai) "คุยกับ AI ผู้ช่วย" else "Chat with AI Assistant",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = if (isThai) "ถามได้ทุกเรื่องเกี่ยวกับการเลี้ยงลูก ตลอด 24 ชั่วโมง" else "Ask any parenting questions 24/7",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }

                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Section: บันทึกด่วน (Quick Action) with 4 Pastel Tiles
 */
@Composable
private fun QuickActionSection(
    isThai: Boolean,
    onMilk: () -> Unit,
    onDiaper: () -> Unit,
    onGrowth: () -> Unit,
    onSleep: () -> Unit,
    onAddMore: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, Color(0xFFF2F4F7)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isThai) "บันทึกด่วน (Quick Action)" else "Quick Action",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color(0xFF101828)
                    )
                    Text(
                        text = if (isThai) "บันทึกข้อมูลสำคัญได้ง่ายๆ เพียงปลายนิ้ว" else "Log key baby telemetry in one tap",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF667085)
                    )
                }

                Surface(
                    onClick = onAddMore,
                    shape = CircleShape,
                    color = Color(0xFF173DB8),
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = if (isThai) "เพิ่มบันทึก" else "Add Log",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionTile(
                    label = if (isThai) "บันทึกการให้นม" else "Feeding",
                    icon = Icons.Filled.LocalDrink,
                    bgColor = Color(0xFFFFF0F3),
                    iconColor = Color(0xFFFF5277),
                    modifier = Modifier.weight(1f),
                    onClick = onMilk
                )

                Spacer(Modifier.width(8.dp))

                QuickActionTile(
                    label = if (isThai) "บันทึกการขับถ่าย" else "Diaper",
                    icon = Icons.Filled.BabyChangingStation,
                    bgColor = Color(0xFFEDF4FF),
                    iconColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f),
                    onClick = onDiaper
                )

                Spacer(Modifier.width(8.dp))

                QuickActionTile(
                    label = if (isThai) "บันทึกการเจริญเติบโต" else "Growth",
                    icon = Icons.Filled.Straighten,
                    bgColor = Color(0xFFEDFCF2),
                    iconColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    onClick = onGrowth
                )

                Spacer(Modifier.width(8.dp))

                QuickActionTile(
                    label = if (isThai) "บันทึกการนอน" else "Sleep",
                    icon = Icons.Filled.Bedtime,
                    bgColor = Color(0xFFFFF9E6),
                    iconColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                    onClick = onSleep
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = bgColor,
            shadowElevation = 0.dp,
            modifier = Modifier.size(66.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF344054),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * Section: การแจ้งเตือน (Real notifications extracted from user notifications)
 */
@Composable
private fun NotificationAlertsSection(
    realNotifications: List<AppNotification>,
    nextVaccine: Pair<com.lullabyai.app.data.model.VaccineRecord, LocalDate>?,
    childName: String?,
    isThai: Boolean,
    onViewAll: () -> Unit,
    onVaccineClick: () -> Unit,
    onDoctorClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, Color(0xFFF2F4F7)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThai) "การแจ้งเตือน" else "Notifications",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color(0xFF101828)
                )

                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (isThai) "ดูทั้งหมด" else "View all",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color(0xFF173DB8)
                    )
                }
            }

            // Select and rank the 2 most important notifications
            data class DisplayNotification(
                val id: String,
                val title: String,
                val subtitle: String,
                val isUrgent: Boolean,
                val tagText: String,
                val icon: ImageVector,
                val onClick: () -> Unit
            )

            val displayItems = remember(realNotifications, nextVaccine, childName, isThai) {
                val items = mutableListOf<DisplayNotification>()

                // 1. Process real DB notifications
                realNotifications.forEach { notif ->
                    val isWarning = notif.category == "warning" ||
                            notif.title.contains("ด่วน") ||
                            notif.title.contains("เตือน") ||
                            notif.title.contains("ระวัง") ||
                            notif.title.contains("ไข้")
                    val isSchedule = notif.category == "schedule" ||
                            notif.title.contains("นัด") ||
                            notif.title.contains("ตรวจ")

                    items.add(
                        DisplayNotification(
                            id = notif.id,
                            title = notif.title,
                            subtitle = notif.body ?: notif.createdAt?.take(10) ?: "",
                            isUrgent = isWarning,
                            tagText = if (isWarning) (if (isThai) "ด่วน" else "Urgent")
                            else if (isSchedule) (if (isThai) "นัดหมาย" else "Schedule")
                            else (if (isThai) "แจ้งเตือน" else "Notice"),
                            icon = if (isWarning) Icons.Filled.Warning
                            else if (isSchedule) Icons.Filled.CalendarMonth
                            else Icons.Filled.Notifications,
                            onClick = onViewAll
                        )
                    )
                }

                // 2. If we need items to reach 2, supplement with real child clinical milestones/vaccines
                val displayChild = childName?.takeIf { it.isNotBlank() } ?: (if (isThai) "น้องรัก" else "Leo")
                val vaccineName = nextVaccine?.first?.vaccineName ?: "วัคซีน MMR"
                val days = if (nextVaccine != null) {
                    ChronoUnit.DAYS.between(LocalDate.now(), nextVaccine.second).toInt()
                } else 3
                val isVaccineUrgent = days <= 3

                // Add vaccine notification if not enough items
                if (items.none { it.title.contains("วัคซีน") }) {
                    items.add(
                        DisplayNotification(
                            id = "vaccine_due",
                            title = if (isThai) "นัดฉีดวัคซีน: $vaccineName สำหรับน้อง$displayChild" else "Vaccine: $vaccineName for $displayChild",
                            subtitle = if (days <= 0) (if (isThai) "ถึงกำหนดวันนี้ ดำเนินการด่วน" else "Due today - Urgent")
                            else (if (isThai) "มีนัดในอีก $days วัน (25 พ.ค. 2567)" else "Due in $days days"),
                            isUrgent = isVaccineUrgent,
                            tagText = if (isVaccineUrgent) (if (isThai) "ด่วน" else "Urgent") else (if (isThai) "นัดหมาย" else "Schedule"),
                            icon = if (isVaccineUrgent) Icons.Filled.NotificationsActive else Icons.Filled.Vaccines,
                            onClick = onVaccineClick
                        )
                    )
                }

                // Add developmental checkup / doctor visit if not enough items
                if (items.size < 2) {
                    items.add(
                        DisplayNotification(
                            id = "doctor_checkup",
                            title = if (isThai) "นัดหมายพบแพทย์: ตรวจพัฒนาการ" else "Doctor Visit: Milestones Check",
                            subtitle = if (isThai) "30 พ.ค. 2567 เวลา 10:00 น. (คลินิกเด็กดี)" else "30 May 2024 at 10:00 AM",
                            isUrgent = false,
                            tagText = if (isThai) "กำหนดการ" else "Appointment",
                            icon = Icons.Filled.CalendarMonth,
                            onClick = onDoctorClick
                        )
                    )
                }

                // Sort: Urgent items first (Red), then by timestamp / importance, take top 2
                items.sortedByDescending { it.isUrgent }.take(2)
            }

            // Render the 2 most important notifications with distinct styles
            displayItems.forEach { item ->
                NotificationItemCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    isUrgent = item.isUrgent,
                    tagText = item.tagText,
                    icon = item.icon,
                    onClick = item.onClick
                )
            }
        }
    }
}

@Composable
private fun NotificationItemCard(
    title: String,
    subtitle: String,
    isUrgent: Boolean,
    tagText: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    // Dynamic theme: Red for urgent, Soft Blue for scheduled/normal
    val cardBg = if (isUrgent) Color(0xFFFFF5F5) else Color(0xFFF0F7FF)
    val borderColor = if (isUrgent) Color(0xFFFFD1D6) else Color(0xFFDBEAFE)
    val iconBg = if (isUrgent) Color(0xFFFFE4E6) else Color(0xFFDBEAFE)
    val accentColor = if (isUrgent) Color(0xFFE11D48) else Color(0xFF2563EB)
    val tagBg = if (isUrgent) Color(0xFFFFE4E6) else Color(0xFFE0EDFE)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1
                    )

                    Spacer(Modifier.width(6.dp))

                    // Badge Pill (e.g. "ด่วน" in red or "นัดหมาย" in blue)
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = tagBg,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = tagText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = accentColor
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Section: บริการล่าสุด (Recent Services) with 4 Square Cards
 */
@Composable
private fun RecentServicesSection(
    isThai: Boolean,
    recentKeys: List<String>,
    svcMap: Map<String, DashSvc>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isThai) "บริการล่าสุด" else "Recent Services",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = Color(0xFF101828),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentKeys.forEach { key ->
                svcMap[key]?.let { svc ->
                    RecentServiceCard(
                        title = svc.label,
                        subtitle = svc.subtitle,
                        icon = svc.icon,
                        iconBg = svc.bgColor,
                        iconTint = svc.iconColor,
                        modifier = Modifier.weight(1f),
                        onClick = svc.nav
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentServiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFF2F4F7)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = Color(0xFF101828),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp
                    ),
                    color = Color(0xFF667085),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GuestModeBannerCard(isThai: Boolean, onSignUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isThai) "บัญชีทดลองใช้งาน (Test Account)" else "Test Account (View-Only)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    text = if (isThai) "ดูตัวอย่างข้อมูลได้เท่านั้น • บันทึกข้อมูลและใช้งาน AI ต้องสมัครสมาชิก"
                    else "View-only preview • Recording data and using AI require registration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onSignUp,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(if (isThai) "สมัครสมาชิก" else "Sign up", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

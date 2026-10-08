package com.lullabyai.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    /**
     * The four บันทึกด่วน tiles used to open HUBS rather than the log each one is named after:
     * "บันทึกการให้นม" opened the nutrition hub, "บันทึกการขับถ่าย" opened the health screen, and
     * "บันทึกการเจริญเติบโต" opened growth HISTORY. A tile that says บันทึก has to open the thing
     * you write in, or the one row on this screen a parent could learn is a row of near-misses.
     *
     * Defaulted to the old targets so no caller breaks; the real caller passes the log routes.
     */
    onNavigateToMilkLog: () -> Unit = onNavigateToNutrition,
    onNavigateToWasteLog: () -> Unit = onNavigateToHealth,
    onNavigateToAddGrowth: () -> Unit = onNavigateToGrowth,
    /** การทำงานด่วน, as the owner specified them. Every one is an existing screen. */
    onNavigateToAppointments: () -> Unit = onNavigateToVaccine,
    onNavigateToHealthBook: () -> Unit = onNavigateToHealth,
    onNavigateToMedicationLog: () -> Unit = onNavigateToHealth,
    /** The three buttons on ครอบครัวของเรา. */
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToTimetable: () -> Unit = onNavigateToCalendar,
    onNavigateToFamilySummary: () -> Unit = onNavigateToChild,
    onNavigateToTips: () -> Unit = {},
    /**
     * ลูกไม่สบาย. Opens AddHealthRecord, which CREATES the illness record.
     *
     * Not LogSicknessSymptoms: that screen reads `state.openHealthRecordId` and its save button is
     * `enabled = record != null`, so opening it without an illness already on file shows a form
     * that can never be submitted and never says why.
     */
    onNavigateToSickness: () -> Unit = onNavigateToHealth,
    /** Opens any QuickActionCatalog destination by route - used by บริการแนะนำ. */
    onNavigateToRoute: (String) -> Unit = {},
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
            icon = { Icon(Icons.Filled.ChildCare, null, tint = MaterialTheme.colorScheme.primary) },
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
            icon = { Icon(Icons.Filled.Widgets, null, tint = MaterialTheme.colorScheme.primary) },
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
    // Username ONLY, as the owner asked - never the full name. A legal name is not what a parent
    // wants to be greeted by at 6am, and it is the most identifying string on the account.
    // A blank username falls through to the relationship address ("คุณแม่") below, never to a blank.
    val username = authState.profile?.username?.trim()?.takeIf { it.isNotBlank() }
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
            icon = { Icon(Icons.Filled.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) },
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

    // The lines on the ครอบครัวของเรา card. TodayPlan is pure, so recomputing when the rows change
    // is cheap and there is nothing to cache. Every child, not only the selected one - an overdue
    // dose for the second child must not hide behind the profile switcher.
    val todayItems = remember(
        childState.children,
        childState.medicationDoses,
        childState.vaccines,
        childState.healthRecords,
        childState.symptomActivityByRecord
    ) {
        com.lullabyai.app.util.TodayPlan.forFamily(
            children = childState.children,
            doses = childState.medicationDoses,
            vaccines = childState.vaccines,
            healthRecords = childState.healthRecords,
            symptomActivityByRecord = childState.symptomActivityByRecord
        )
    }

    // บริการล่าสุด, ตารางกิจกรรม and บริการแนะนำ have all been removed from this screen. The last
    // one re-rolled its picks on every launch, so the home screen was never twice the same, and a
    // parent cannot build muscle memory against a shuffle. RecentServices is no longer read here;
    // its pushes in AppNavigation now only feed the quick-action search.

    // Only unread warnings reach the card. "schedule" and "normal" are not emergencies and belong
    // behind the bell - putting all five on the home screen is how the one that matters gets lost.
    val urgentNotifications = remember(realNotifications) {
        realNotifications.filter { it.category == "warning" && !it.isRead }.take(2)
    }

    // ครอบครัว. list_child_access_members is per child, so the family is the union across every
    // child, deduped by user. Only is_family rows reach it: a nanny is a real caregiver and belongs
    // in the handover and chat screens, but she is not family on somebody home screen.
    var familyMembers by remember {
        mutableStateOf<List<com.lullabyai.app.data.model.ChildAccessMember>>(emptyList())
    }
    LaunchedEffect(childState.children.map { it.id }) {
        val repo = com.lullabyai.app.data.repository.ChildRepository()
        // A failed read for one child must not blank the whole card, so each is its own getOrNull
        // and FamilyRoster folds whatever came back.
        familyMembers = com.lullabyai.app.util.FamilyRoster.of(
            childState.children.map { c -> repo.listAccessMembers(c.id).getOrNull().orEmpty() }
        )
    }

    // The heading of that card. A family that has not named itself gets a default built from the
    // username rather than a blank heading or an empty card.
    val resolvedFamilyName = com.lullabyai.app.util.FamilyRoster.name(authState.profile, isThai)

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
        containerColor = DashBackground
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

                    // Home only says that answers are owed and where to give them; the questions
                    // themselves are asked on Child Info, so there is one place to answer rather
                    // than two forms that can disagree.
                    if (hasActiveChild && childState.infoAsks.isNotEmpty()) {
                        HomeQuestionNoticeBanner(
                            isThai = isThai,
                            questionCount = childState.infoAsks.size,
                            subjectName = com.lullabyai.app.data.infoask.InfoAskCatalog.bannerSubjectName(
                                childState.infoAsks, child?.name.orEmpty(), isThai
                            ),
                            onOpenChildInfo = onNavigateToChild,
                            onRecordNow = childState.infoAsks.firstNotNullOfOrNull {
                                com.lullabyai.app.data.infoask.InfoAskCatalog.recordRoute(it.fieldId)
                            }?.let { route -> { onNavigateToRoute(route) } }
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
                                colors = CardDefaults.cardColors(containerColor = DashErrBg)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = DashErrIcon,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DashErrText,
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
                            colors = CardDefaults.cardColors(containerColor = DashCard),
                            border = BorderStroke(1.dp, DashBorder),
                            elevation = CardDefaults.cardElevation(0.dp)
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
                                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (isThai) "ข้อมูลของ ${c.name} ยังไม่ครบ" else "${c.name}'s info is incomplete",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = DashTitle
                                    )
                                    Text(
                                        text = (if (isThai) "กรอกเพิ่มเติม: " else "Fill in: ") + missing.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DashSubtle,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // SECTION: ลูกของคุณ — the children, doubling as the switcher.
                    if (childState.children.isNotEmpty()) {
                        YourChildrenCard(
                            isThai = isThai,
                            children = childState.children,
                            activeChildId = child?.id,
                            onSelect = { id ->
                                // Select first, then open: Child Info reads the ACTIVE child, so
                                // navigating before the switch would show the previous child for a
                                // frame and then swap under her.
                                childViewModel.selectChild(id)
                                onNavigateToChild()
                            },
                            onAdd = onNavigateToAddChild
                        )
                    }

                    // SECTION: ครอบครัวของเรา — today, as facts, before anything else on the page.
                    FamilyTodayCard(
                        isThai = isThai,
                        items = todayItems,
                        warnings = urgentNotifications,
                        onWarning = onNavigateToNotifications,
                        onCalendar = onNavigateToCalendar,
                        onTimetable = onNavigateToTimetable,
                        onSummary = onNavigateToFamilySummary,
                        onItem = { item ->
                            when (item.kind) {
                                com.lullabyai.app.util.TodayKind.MEDICINE_OVERDUE,
                                com.lullabyai.app.util.TodayKind.MEDICINE_DUE,
                                com.lullabyai.app.util.TodayKind.DONE_MEDICINE -> onNavigateToMedicationLog()
                                com.lullabyai.app.util.TodayKind.SYMPTOM_LOG -> onNavigateToSickness()
                                com.lullabyai.app.util.TodayKind.VACCINE -> onNavigateToVaccine()
                            }
                        }
                    )

                    // SECTION: ครอบครัว — who else is in this family.
                    if (familyMembers.isNotEmpty()) {
                        FamilyMembersCard(
                            isThai = isThai,
                            familyName = resolvedFamilyName,
                            members = familyMembers,
                            onManage = onNavigateToConnect
                        )
                    }

                    // HERO CARD: คุยกับ AI ผู้ช่วย (Chat with AI Assistant Banner)
                    HeroAiChatBannerCard(
                        isThai = isThai,
                        onClick = onNavigateToChat
                    )

                    // SECTION: การทำงานด่วน (Quick Action)
                    QuickActionSection(
                        isThai = isThai,
                        onAppointments = onNavigateToAppointments,
                        onVaccineBook = onNavigateToVaccine,
                        onHealthBook = onNavigateToHealthBook,
                        onSickness = onNavigateToSickness,
                        onNutrition = onNavigateToNutrition,
                        onMedication = onNavigateToMedicationLog,
                        onAddMore = onNavigateToQuickLog
                    )

                    // SECTION: เคล็ดลับ — one image-led card going to the full list.
                    ParentTipsCard(isThai = isThai, onClick = onNavigateToTips)

                    // SECTION: what a caregiver has to do today. CareRoleViewModel excludes people
                    // with full access, so a parent never sees their own briefing as a duty.
                    if (hasActiveChild && !isGuest) {
                        val careRoleVm: com.lullabyai.app.viewmodel.CareRoleViewModel =
                            androidx.lifecycle.viewmodel.compose.viewModel()
                        LaunchedEffect(child?.id) { careRoleVm.load(child?.id) }
                        val amCaregiver by careRoleVm.amCaregiver.collectAsState()
                        val caregiverChildId = child?.id
                        if (amCaregiver && caregiverChildId != null) {
                            com.lullabyai.app.ui.screens.access.CaregiverDutyBanner(
                                childId = caregiverChildId,
                                thai = isThai,
                                onOpenChecklist = {
                                    onNavigateToRoute(
                                        com.lullabyai.app.ui.navigation.Screen.CareHandoverChecklist.route(caregiverChildId)
                                    )
                                },
                                onQuickRecord = {
                                    onNavigateToRoute(com.lullabyai.app.ui.navigation.Screen.CaregiverUpdate.route)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}


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
                color = DashCard,
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
                            .background(DashAvatarBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = onNotificationClick,
                shape = CircleShape,
                color = DashCard,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, DashBorderAlt),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.NotificationsNone,
                        contentDescription = if (isThai) "การแจ้งเตือน" else "Notifications",
                        tint = DashIcon,
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

// Home colour tokens. Light values are the original design hex (lowercase literals), so light mode
// is pixel-identical; dark reads the app theme, with cards on surfaceContainerHigh like the Recent
// Services cards.
private val dashDark: Boolean
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = com.lullabyai.app.ui.theme.LocalDarkTheme.current

private val DashBackground: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.background else Color(0xfff7fafe)
private val DashCard: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
private val DashTitle: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.onSurface else Color(0xff101828)
private val DashNotifTitle: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.onSurface else Color(0xff1e293b)
private val DashIcon: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.onSurface else Color(0xff1d2939)
private val DashBody: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xff475467)
private val DashSubtle: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xff667085)
private val DashLabel: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xff344054)
private val DashBorder: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.outlineVariant else Color(0xffe5e7eb)
private val DashOverdue: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) Color(0xFFFF6B8A) else Color(0xFFE11D48)
private val DashDone: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) Color(0xFF4ADECB) else Color(0xFF16A394)
private val DashBorderSoft: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.outlineVariant else Color(0xfff2f4f7)
private val DashBorderAlt: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.outlineVariant else Color(0xffeaecf0)
private val DashPrimaryText: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.primary else Color(0xff173db8)
private val DashAvatarBg: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xffebf1ff)
private val DashErrBg: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.errorContainer else Color(0xfffef2f2)
private val DashErrIcon: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.error else Color(0xffdc2626)
private val DashErrText: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (dashDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xff991b1b)

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
                color = DashTitle
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
            color = DashBody
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
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DashCard),
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
                        tint = MaterialTheme.colorScheme.primary,
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
                        color = DashTitle
                    )
                    Text(
                        text = if (isThai) "แบบประเมินสั้นๆ ประจำเดือน เพื่อดูแลสุขภาพใจของคุณพ่อคุณแม่"
                        else "A short monthly check-in to support your emotional wellbeing",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = DashSubtle
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
 * Home's notice that questions are waiting — the form lives on Child Info, this only points there.
 */
@Composable
private fun HomeQuestionNoticeBanner(
    isThai: Boolean,
    questionCount: Int,
    subjectName: String,
    onOpenChildInfo: () -> Unit,
    onRecordNow: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DashCard),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.clickable(onClick = onOpenChildInfo),
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
                    Icons.Filled.QuestionAnswer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isThai) "มีคำถาม $questionCount ข้อเกี่ยวกับ$subjectName รอคุณตอบ"
                    else "$questionCount question${if (questionCount == 1) "" else "s"} about $subjectName waiting",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = DashTitle
                )
                Text(
                    text = if (isThai) "แตะเพื่อไปตอบในหน้าข้อมูลลูก"
                    else "Tap to answer them on the Child Info page",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = DashSubtle
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE11D48))
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = DashSubtle,
                modifier = Modifier.size(20.dp)
            )
        }
        if (onRecordNow != null) {
            Button(
                onClick = onRecordNow,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(
                    if (isThai) "บันทึกเลย" else "Record now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp),
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
/**
 * ครอบครัวของเรา - today, stated rather than hinted at.
 *
 * The first draft of this card read "มีข้อมูลสำคัญที่ต้องดูแลวันนี้" and made a parent tap to find
 * out whether she should be worried. That is the anxious way of saying nothing. Every line here is
 * a fact with a time on it, straight from TodayPlan, and when nothing is outstanding the card says
 * so plainly instead of implying something looms.
 */
/**
 * เคล็ดลับดูแลลูกน้อยในแต่ละช่วงวัย.
 *
 * Image-led, because that is what makes a parent stop on it - and the mascot is the app own art
 * rather than a stock photograph of somebody else family.
 */
/**
 * ลูกของคุณ — the children, as a row you scroll rather than a list you scroll past.
 *
 * Horizontal because this is a chooser, not content: three children should cost a glance, not a
 * third of the screen. Tapping one makes it the active child AND opens Child Info on it, so
 * choosing and going are a single gesture.
 */
@Composable
private fun YourChildrenCard(
    isThai: Boolean,
    children: List<com.lullabyai.app.data.model.Child>,
    activeChildId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DashCard),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, DashBorderSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (isThai) "ลูกของคุณ" else "Your children",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DashTitle,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                children.forEach { c ->
                    PersonChip(
                        label = c.name,
                        caption = ageCaption(c.birthdate, isThai),
                        avatarUrl = c.avatarUrl,
                        fallbackIcon = Icons.Filled.ChildCare,
                        selected = c.id == activeChildId,
                        onClick = { onSelect(c.id) }
                    )
                }
                // Adding a child is the same shape as choosing one, so the row reads as one set of
                // options rather than a list plus a stray button.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAdd() }
                        .padding(4.dp)
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(1.dp, DashBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, null, tint = DashSubtle, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        if (isThai) "เพิ่มลูก" else "Add",
                        style = MaterialTheme.typography.labelMedium,
                        color = DashSubtle,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * ครอบครัว — who else is in this family, as a row of faces.
 *
 * Only members flagged is_family appear. That flag is set deliberately when a membership is
 * approved and is never derived from the role name, so a nanny stays out of this row however her
 * role happens to be spelled - including under a custom role the family invented.
 */
@Composable
private fun FamilyMembersCard(
    isThai: Boolean,
    familyName: String,
    members: List<com.lullabyai.app.data.model.ChildAccessMember>,
    onManage: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DashCard),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, DashBorderSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    familyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DashTitle,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    if (isThai) "จัดการ" else "Manage",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable { onManage() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                members.forEach { m ->
                    PersonChip(
                        label = m.displayName,
                        // A family that renamed a role gets its own wording; everyone else gets the
                        // standard label rather than a bare role key.
                        caption = (if (isThai) m.roleNameTh else m.roleNameEn)
                            ?.takeIf { it.isNotBlank() }
                            ?: com.lullabyai.app.viewmodel.AccessRoleLabels.title(m.role, isThai),
                        avatarUrl = null,
                        fallbackIcon = Icons.Filled.Person,
                        initials = m.displayName.trim().take(2).ifBlank { "?" },
                        selected = false,
                        onClick = onManage
                    )
                }
            }
        }
    }
}

/**
 * One face in a horizontal people row, shared by ลูกของคุณ and ครอบครัว so the two cannot drift.
 *
 * [initials] is the fallback when there is no picture. Family members use it rather than an avatar
 * because list_child_access_members returns profiles.avatar_url raw, and in this app that column
 * holds a private-bucket PATH, not a URL - handing it to Coil shows nothing at all.
 */
@Composable
private fun PersonChip(
    label: String,
    caption: String?,
    avatarUrl: String?,
    fallbackIcon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    initials: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = if (selected) 0.18f else 0.10f))
                .then(if (selected) Modifier.border(2.dp, Primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                !avatarUrl.isNullOrBlank() -> coil.compose.AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
                initials != null -> Text(
                    initials,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                else -> Icon(fallbackIcon, null, tint = Primary, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Primary else DashTitle,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        caption?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = DashSubtle,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** "7 เดือน" under a face. Null when the birthdate is missing, rather than guessing an age. */
private fun ageCaption(birthdate: String, isThai: Boolean): String? {
    val born = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull() ?: return null
    val now = java.time.LocalDate.now()
    if (born.isAfter(now)) return null
    val p = java.time.Period.between(born, now)
    return when {
        p.years >= 1 -> if (isThai) "${p.years} ขวบ" else "${p.years}y"
        p.months >= 1 -> if (isThai) "${p.months} เดือน" else "${p.months}m"
        else -> if (isThai) "${p.days} วัน" else "${p.days}d"
    }
}

@Composable
private fun ParentTipsCard(isThai: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DashCard)
            .border(1.dp, DashBorderSoft, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    com.lullabyai.app.R.drawable.tip_bonding
                ),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (isThai) "เคล็ดลับดูแลลูกน้อยในแต่ละช่วงวัย"
                else "Caring for your child, age by age",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = DashTitle
            )
            Text(
                if (isThai) "ข้อมูลที่คุณแม่ควรรู้ เพื่อสุขภาพที่ดี"
                else "Worth knowing, in a couple of minutes",
                style = MaterialTheme.typography.labelMedium,
                color = DashBody
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = DashSubtle,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FamilyTodayCard(
    isThai: Boolean,
    items: List<com.lullabyai.app.util.TodayItem>,
    warnings: List<AppNotification>,
    onWarning: () -> Unit,
    onCalendar: () -> Unit,
    onTimetable: () -> Unit,
    onSummary: () -> Unit,
    onItem: (com.lullabyai.app.util.TodayItem) -> Unit
) {
    // The child name is only worth the space when more than one child has something on the card.
    val showNames = items.map { it.childId }.distinct().size > 1
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DashCard),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, DashBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = Primary, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isThai) "ครอบครัวของเรา" else "Our family",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DashTitle
                )
            }

            // A warning outranks anything the rows can say: it is the app reporting that something
            // went wrong, not a scheduled task. Big and red, above every dose and vaccine.
            warnings.forEach { warning ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DashOverdue.copy(alpha = 0.12f))
                        .clickable { onWarning() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = DashOverdue,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            warning.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = DashOverdue
                        )
                        warning.body?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = DashOverdue,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            if (items.isEmpty() && warnings.isEmpty()) {
                Text(
                    if (isThai) "วันนี้ยังไม่มีอะไรต้องทำค่ะ" else "Nothing needs doing today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashBody
                )
            } else {
                items.forEach { item -> TodayRow(item, isThai, showNames) { onItem(item) } }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayCardButton(
                    if (isThai) "ปฏิทิน" else "Calendar",
                    Icons.Filled.CalendarMonth, Modifier.weight(1f), onCalendar
                )
                TodayCardButton(
                    if (isThai) "ตารางเวลา" else "Timetable",
                    Icons.Filled.ViewAgenda, Modifier.weight(1f), onTimetable
                )
                TodayCardButton(
                    if (isThai) "สรุป" else "Summary",
                    Icons.Filled.Description, Modifier.weight(1f), onSummary
                )
            }
        }
    }
}

/**
 * One line of today.
 *
 * Only an overdue dose is tinted. Everything else is ordinary text: when three lines shout, the one
 * that matters stops being heard.
 */
@Composable
private fun TodayRow(
    item: com.lullabyai.app.util.TodayItem,
    isThai: Boolean,
    showName: Boolean,
    onClick: () -> Unit
) {
    val overdue = item.kind == com.lullabyai.app.util.TodayKind.MEDICINE_OVERDUE
    val done = item.kind == com.lullabyai.app.util.TodayKind.DONE_MEDICINE
    val accent = when {
        overdue -> DashOverdue
        done -> DashDone
        else -> Primary
    }
    val icon = when (item.kind) {
        com.lullabyai.app.util.TodayKind.MEDICINE_OVERDUE,
        com.lullabyai.app.util.TodayKind.MEDICINE_DUE -> Icons.Filled.Medication
        com.lullabyai.app.util.TodayKind.DONE_MEDICINE -> Icons.Filled.CheckCircle
        com.lullabyai.app.util.TodayKind.SYMPTOM_LOG -> Icons.Filled.MonitorHeart
        com.lullabyai.app.util.TodayKind.VACCINE -> Icons.Filled.Vaccines
    }
    val text = when (item.kind) {
        com.lullabyai.app.util.TodayKind.MEDICINE_OVERDUE ->
            if (isThai) "ยามื้อ " + item.time + " ยังไม่ได้ให้" else "Dose at " + item.time + " not given"
        com.lullabyai.app.util.TodayKind.MEDICINE_DUE ->
            if (isThai) "ยามื้อถัดไป " + item.time else "Next dose at " + item.time
        com.lullabyai.app.util.TodayKind.DONE_MEDICINE ->
            if (isThai) "ให้ยามื้อ " + item.time + " แล้ว" else "Dose at " + item.time + " given"
        com.lullabyai.app.util.TodayKind.SYMPTOM_LOG -> {
            val day = item.dayNumber
            if (isThai) {
                if (day != null) "บันทึกอาการวันนี้ · ป่วยวันที่ " + day else "บันทึกอาการวันนี้"
            } else {
                if (day != null) "Log symptoms today · day " + day else "Log symptoms today"
            }
        }
        com.lullabyai.app.util.TodayKind.VACCINE -> {
            val name = item.label
            val on = com.lullabyai.app.util.TimeUtils.localDayMonth(item.date) ?: item.date.orEmpty()
            if (isThai) {
                if (name != null) "วัคซีน " + name + " · " + on else "วัคซีนเข็มถัดไป " + on
            } else {
                if (name != null) name + " vaccine · " + on else "Next vaccine " + on
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (overdue) Modifier.background(accent.copy(alpha = 0.10f)) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = if (overdue) 10.dp else 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
        Text(
            text = if (showName) item.childName + " · " + text else text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
            color = if (overdue) accent else if (done) DashSubtle else DashLabel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TodayCardButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, DashBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = DashLabel, maxLines = 1)
    }
}

@Composable
private fun QuickActionSection(
    isThai: Boolean,
    onAppointments: () -> Unit,
    onVaccineBook: () -> Unit,
    onHealthBook: () -> Unit,
    onSickness: () -> Unit,
    onNutrition: () -> Unit,
    onMedication: () -> Unit,
    onAddMore: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DashCard),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, DashBorderSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThai) "การทำงานด่วน" else "Quick actions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = DashTitle
                )
                Text(
                    text = if (isThai) "ดูทั้งหมด" else "See all",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable { onAddMore() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // The six the owner specified. Half of them WRITE a record rather than open a hub,
            // which is the point: a parent logs a feed a dozen times a day and reads a benefits
            // page once, so this row is ordered by how often a hand reaches for each thing.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionTile(
                    label = if (isThai) "นัดหมาย" else "Appointments",
                    icon = Icons.Filled.EventNote,
                    bgColor = Color(0xFFEDF4FF),
                    iconColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f),
                    onClick = onAppointments
                )
                QuickActionTile(
                    label = if (isThai) "สมุดวัคซีน" else "Vaccines",
                    icon = Icons.Filled.Vaccines,
                    bgColor = Color(0xFFEDFCF2),
                    iconColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    onClick = onVaccineBook
                )
                QuickActionTile(
                    label = if (isThai) "สมุดสุขภาพ" else "Health book",
                    icon = Icons.Filled.MenuBook,
                    bgColor = Color(0xFFF5F3FF),
                    iconColor = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f),
                    onClick = onHealthBook
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionTile(
                    label = if (isThai) "บันทึกการป่วย" else "Log illness",
                    icon = Icons.Filled.MonitorHeart,
                    bgColor = Color(0xFFFFF1F3),
                    iconColor = DashOverdue,
                    modifier = Modifier.weight(1f),
                    onClick = onSickness
                )
                QuickActionTile(
                    label = if (isThai) "โภชนาการ" else "Nutrition",
                    icon = Icons.Filled.Restaurant,
                    bgColor = Color(0xFFFFF9E6),
                    iconColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                    onClick = onNutrition
                )
                QuickActionTile(
                    label = if (isThai) "บันทึกการทานยา" else "Medication",
                    icon = Icons.Filled.Medication,
                    bgColor = Color(0xFFEDFCF2),
                    iconColor = Color(0xFF16A394),
                    modifier = Modifier.weight(1f),
                    onClick = onMedication
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
            // Dark: the pastel tile would glare, so it becomes an 18% wash of its own icon colour.
            color = if (com.lullabyai.app.ui.theme.LocalDarkTheme.current) iconColor.copy(alpha = 0.18f) else bgColor,
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
            color = DashLabel,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
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
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.R
import com.lullabyai.app.data.ChildHubSuggestions
import com.lullabyai.app.data.ChildHubSuggestions.Kind as k
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.ui.components.LocalAvatarUrl
import com.lullabyai.app.ui.components.LocalImportantNotificationCount
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.onboarding.TourSampleData
import com.lullabyai.app.ui.screens.onboarding.tourAnchor
import com.lullabyai.app.ui.theme.LocalDarkTheme
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

// =================================================================================================
// ข้อมูลของเด็ก (Child Hub) — built from the Stitch screen "NongRak Child Hub - Pro UX Redesign"
// (project "03.NongRak Main Screen"). A new screen, not a restyle of ChildSummaryScreen: every
// section below follows that design's HTML in order, sizes and colours.
// =================================================================================================

/** Colours lifted from the design's Tailwind classes. Dark mode swaps the light tints for theme tones. */
private object HubPalette {
    val page = Color(0xFFF4F8FD)
    val blue = Color(0xFF2563EB)
    val blueDeep = Color(0xFF1D4ED8)
    val blueNight = Color(0xFF1E40AF)
    val navy = Color(0xFF1E3A8A)
    val blue50 = Color(0xFFEFF6FF)
    val blueTint = Color(0xFFEBF3FE)
    val blue100 = Color(0xFFDBEAFE)
    val slate800 = Color(0xFF1E293B)
    val slate700 = Color(0xFF334155)
    val slate500 = Color(0xFF64748B)
    val slate400 = Color(0xFF94A3B8)
    val slate100 = Color(0xFFF1F5F9)
    val rose400 = Color(0xFFFB7185)
    val rose500 = Color(0xFFF43F5E)
    val pink500 = Color(0xFFEC4899)
    val amberBg = Color(0xFFFEF3C7)
    val amber = Color(0xFFD97706)
    val peach = Color(0xFFFFE4D6)
}

@Composable private fun isDark() = LocalDarkTheme.current
@Composable private fun ink() = if (isDark()) MaterialTheme.colorScheme.onSurface else HubPalette.slate800
@Composable private fun inkSoft() = if (isDark()) MaterialTheme.colorScheme.onSurfaceVariant else HubPalette.slate500
@Composable private fun inkFaint() = if (isDark()) MaterialTheme.colorScheme.outline else HubPalette.slate400
@Composable private fun cardColor() = if (isDark()) MaterialTheme.colorScheme.surface else Color.White
@Composable private fun hairline() = if (isDark()) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f) else HubPalette.slate100

/** The design's `soft-shadow`: a faint blue-tinted lift. */
private fun Modifier.hubSoftShadow(shape: Shape) = this.shadow(
    elevation = 6.dp, shape = shape,
    ambientColor = HubPalette.blue.copy(alpha = 0.10f), spotColor = HubPalette.blue.copy(alpha = 0.10f)
)

@Composable
fun ChildHubScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    isTourMode: Boolean = false
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val th = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val tourPreview = isTourMode && !childState.isLoading && childState.children.isEmpty()
    val child: Child? = if (tourPreview) TourSampleData.child else childState.child
    val milestones = if (tourPreview) TourSampleData.sampleMilestones else childState.milestones
    val devPercent = if (milestones.isNotEmpty()) milestones.count { it.isAchieved } * 100 / milestones.size else 0
    val displayName = if (tourPreview) TourSampleData.displayName(th) else child?.name ?: (if (th) "ลูกของคุณ" else "Your child")
    val context = LocalContext.current

    var showSwitcher by remember { mutableStateOf(false) }
    var childToDelete by remember { mutableStateOf<Child?>(null) }

    LaunchedEffect(child?.id, tourPreview) {
        if (!tourPreview) child?.let { chatViewModel.loadChildInfoCardSummary(it, th) }
    }
    // Reached directly (not via Dashboard) the shared ViewModel may be empty.
    LaunchedEffect(Unit) {
        if (!tourPreview && childState.children.isEmpty() && childState.child == null) childViewModel.loadChild()
    }
    LaunchedEffect(child?.id, tourPreview) {
        if (!tourPreview && child != null) childViewModel.loadInfoAsk(context, th)
    }
    // Counted for every child, not just the active one: the switcher flags who else is waiting.
    LaunchedEffect(childState.children.size, tourPreview) {
        if (!tourPreview) childViewModel.loadChildQuestionBadges(context)
    }
    val selectedId by com.lullabyai.app.data.settings.ChildSelection.id.collectAsState()
    LaunchedEffect(selectedId) {
        val current = childState.child?.id
        if (selectedId != null && current != null && selectedId != current) childViewModel.selectChild(selectedId!!)
    }
    val careRoleVm: com.lullabyai.app.viewmodel.CareRoleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    LaunchedEffect(child?.id) { careRoleVm.load(child?.id) }
    val amCaregiver by careRoleVm.amCaregiver.collectAsState()

    Scaffold(
        containerColor = if (isDark()) MaterialTheme.colorScheme.background else HubPalette.page,
        topBar = {
            HubTopBar(
                isThai = th,
                showPill = child != null || tourPreview,
                name = displayName,
                ageMonths = if (tourPreview) TourSampleData.ageLabel(th) else hubAgeMonthsOnly(child?.birthdate, th),
                avatarUrl = childState.childAvatarSignedUrl,
                otherChildWaiting = !tourPreview && childState.childQuestionCounts.any { (id, n) ->
                    n > 0 && id != child?.id
                },
                onSwitch = { showSwitcher = true },
                onNotifications = { navController.navigate(Screen.Notifications.route) },
                onProfile = { navController.navigate(Screen.Settings.route) }
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") }
    ) { padding ->
        when {
            childState.isLoading && childState.children.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = HubPalette.blue) }

            childState.children.isEmpty() && !childState.isLoading && childState.error != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentAlignment = Alignment.Center
            ) { com.lullabyai.app.ui.components.ChildLoadErrorCard(th) { childViewModel.loadChild() } }

            !tourPreview && childState.children.isEmpty() && !childState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding).padding(20.dp)
            ) {
                com.lullabyai.app.ui.components.NoChildDataCard(th) {
                    if (childViewModel.isGuest()) {
                        childViewModel.promptRegistration(if (th) "ต้องสมัครสมาชิกก่อนเพิ่มข้อมูลลูก" else "Registration required to add a child")
                    } else navController.navigate(Screen.AddChildChoice.route)
                }
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(2.dp))

                HubHeroSection(
                    isThai = th,
                    name = displayName,
                    ageLabel = if (tourPreview) TourSampleData.ageLabel(th) else hubAgeLabel(child?.birthdate, th),
                    avatarUrl = childState.childAvatarSignedUrl,
                    onAvatar = { navController.navigate(Screen.EditChild.route) },
                    onGreeting = { navController.navigate(Screen.QuickLogHub.route) },
                    onHealthBook = { navController.navigate(Screen.HealthBook.route) }
                )

                if (!tourPreview) ChildHubQuestionCard(childViewModel, displayName, th)

                // Parent: history + handover. Caregiver: quick log + care schedule.
                HubActivityCard(
                    isThai = th,
                    leftLabel = if (amCaregiver) (if (th) "บันทึกกิจกรรมด่วน" else "Quick activity log") else (if (th) "ประวัติกิจกรรม" else "Activity history"),
                    rightLabel = if (amCaregiver) (if (th) "บันทึกการส่งต่อ" else "Handover log") else (if (th) "ส่งต่อการดูแล" else "Handover"),
                    onCard = { navController.navigate(Screen.ActivityHistory.route) },
                    onLeft = { navController.navigate(if (amCaregiver) Screen.CaregiverUpdate.route else Screen.ActivityHistory.route) },
                    onRight = {
                        child?.let {
                            navController.navigate(
                                if (amCaregiver) Screen.CareHandoverChecklist.route(it.id)
                                else Screen.CareHandoff.route(it.id)
                            )
                        }
                    }
                )

                // WHO status by sex, at the age each reading was taken (the tour sample has a fixed age).
                val (hubWeightStatus, hubHeightStatus) = com.lullabyai.app.util.GrowthReference.current(
                    child, childState.growthRecords,
                    if (tourPreview) com.lullabyai.app.util.GrowthReference.monthsToDays(TourSampleData.ageMonths()) else null
                )
                HubGrowthSection(
                    isThai = th,
                    weightKg = child?.weightKg,
                    heightCm = child?.heightCm,
                    weightStatus = hubWeightStatus,
                    heightStatus = hubHeightStatus,
                    onHistory = { navController.navigate(Screen.ProfileHistory.route) },
                    onCard = { navController.navigate(Screen.GrowthHistory.route) }
                )

                HubAiCard(
                    isThai = th,
                    percent = devPercent,
                    summary = chatState.childInfoCardSummary,
                    onRelearn = { if (!tourPreview) child?.let { chatViewModel.refreshChildInfoCardSummary(it, th) } },
                    onFull = { navController.navigate(AI_GUIDANCE_ROUTE) }
                )

                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubHealthQuickCard(
                        emoji = "⚠️",
                        title = if (th) "ประวัติการแพ้" else "Allergies",
                        chips = childState.allergies.map { it.name },
                        emptyText = if (th) "ไม่มีประวัติแพ้" else "No known allergies",
                        bg = Color(0xFFFFF5F5), border = Color(0xFFFEE2E2), iconBg = Color(0xFFFEE2E2),
                        titleColor = Color(0xFFDC2626), chevron = Color(0xFFF87171),
                        chipBg = Color(0xFFFECACA).copy(alpha = 0.6f), chipText = Color(0xFFB91C1C),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { navController.navigate(Screen.AllergyHistory.route) }
                    )
                    HubHealthQuickCard(
                        emoji = "💼",
                        title = if (th) "โรคประจำตัว" else "Chronic conditions",
                        chips = childState.chronicConditions.map { it.name },
                        emptyText = if (th) "ไม่มีข้อมูล" else "None recorded",
                        bg = HubPalette.blue50, border = HubPalette.blue100, iconBg = HubPalette.blue100,
                        titleColor = Color(0xFF1D4ED8), chevron = Color(0xFF60A5FA),
                        chipBg = Color(0xFFBFDBFE).copy(alpha = 0.6f), chipText = Color(0xFF1D4ED8),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { navController.navigate(Screen.ChildHealth.route) }
                    )
                }

                val suggestion = remember(childState, tourPreview) {
                    if (tourPreview) null else ChildHubSuggestions.suggest(hubSignals(childState, childViewModel.getAgeMonths()))
                }
                if (suggestion != null) {
                    HubUpdateCta(suggestion, th) { navController.navigate(hubSuggestionRoute(suggestion.kind)) }
                }

                HubServicesSection(
                    isThai = th,
                    routineRoute = child?.id?.let { Screen.CareRoutine.route(it) } ?: "",
                    onNavigate = { route -> if (route.isNotBlank()) navController.navigate(route) }
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showSwitcher) {
        HubChildSwitcherSheet(
            isThai = th,
            children = if (tourPreview) listOf(TourSampleData.child) else childState.children,
            activeChildId = child?.id,
            avatarUrl = childState.childAvatarSignedUrl,
            ownedChildIds = childState.ownedChildIds,
            // The active child's list is the fuller one (it includes the sleep and feeding
            // questions), so it wins over the row-only count for that child.
            questionCounts = child?.id?.let { id ->
                childState.childQuestionCounts + (id to maxOf(
                    childState.childQuestionCounts[id] ?: 0,
                    childState.infoAsks.size
                ))
            } ?: childState.childQuestionCounts,
            onSelect = { c -> showSwitcher = false; childViewModel.selectChild(c.id) },
            onDelete = { c ->
                showSwitcher = false
                if (childViewModel.isGuest()) {
                    childViewModel.promptRegistration(if (th) "บัญชีทดลองไม่สามารถลบโปรไฟล์ได้" else "Cannot delete profile in demo mode")
                } else childToDelete = c
            },
            onAddChild = {
                showSwitcher = false
                if (childViewModel.isGuest()) {
                    childViewModel.promptRegistration(if (th) "ต้องสมัครสมาชิกก่อนเพิ่มข้อมูลลูก" else "Registration required to add a child")
                } else navController.navigate(Screen.AddChildChoice.route)
            },
            onEdit = { showSwitcher = false; navController.navigate(Screen.EditChild.route) },
            onDismiss = { showSwitcher = false }
        )
    }

    childToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { childToDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (th) "ลบข้อมูลเด็ก?" else "Delete child?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (th) "ต้องการลบ \"${target.name}\" หรือไม่? ข้อมูลทั้งหมด (วัคซีน พัฒนาการ สุขภาพ เอกสาร) จะถูกลบถาวรและไม่สามารถย้อนกลับได้"
                    else "Permanently delete \"${target.name}\"? All their data (vaccines, milestones, health, documents) will be removed and cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { childViewModel.deleteChild(target.id); childToDelete = null }) {
                    Text(if (th) "ลบ" else "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { childToDelete = null }) { Text(if (th) "ยกเลิก" else "Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar: switcher pill (left), bell + profile in white circles (right)
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubTopBar(
    isThai: Boolean,
    showPill: Boolean,
    name: String,
    ageMonths: String,
    avatarUrl: String?,
    /** Another child (not the one on screen) has questions waiting — this child's own are already
     *  asked by the card below, so a dot for them would say nothing new. */
    otherChildWaiting: Boolean,
    onSwitch: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isDark()) MaterialTheme.colorScheme.background else HubPalette.page)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showPill) {
            Row(
                Modifier
                    .tourAnchor("parent_child_switch")
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(cardColor().copy(alpha = 0.95f))
                    .border(1.dp, if (isDark()) hairline() else HubPalette.blue100, CircleShape)
                    .clickable(onClick = onSwitch)
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    HubChildAvatar(avatarUrl = avatarUrl, size = 32.dp)
                    if (otherChildWaiting) {
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(cardColor())
                                .padding(1.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE11D48))
                        )
                    }
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (isThai) "น้อง $name" else name,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (isDark()) MaterialTheme.colorScheme.onSurface else HubPalette.navy,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (ageMonths.isNotBlank()) {
                            Text(
                                ageMonths,
                                fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = HubPalette.blue,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(HubPalette.blue50)
                                    .border(1.dp, HubPalette.blue100, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isThai) "สลับโปรไฟล์ลูก" else "Switch child", fontSize = 10.sp, color = inkFaint())
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = HubPalette.blue, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val important = LocalImportantNotificationCount.current
            HubCircleButton(onClick = onNotifications) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(Icons.Filled.Notifications, if (isThai) "การแจ้งเตือน" else "Notifications", tint = ink(), modifier = Modifier.size(20.dp))
                    if (important > 0) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444))
                                .border(1.5.dp, cardColor(), CircleShape)
                        )
                    }
                }
            }
            val profileUrl = LocalAvatarUrl.current
            HubCircleButton(onClick = onProfile) {
                if (!profileUrl.isNullOrBlank()) {
                    AsyncImage(model = profileUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Person, if (isThai) "โปรไฟล์ผู้ใช้งาน" else "Profile", tint = inkSoft(), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun HubCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .shadow(1.dp, CircleShape)
            .clip(CircleShape)
            .background(cardColor())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * The child's picture. Without an uploaded photo it is the design's cartoon baby — peach face in a
 * warm gradient ring — instead of a generic icon.
 */
@Composable
internal fun HubChildAvatar(avatarUrl: String?, size: Dp, ring: Boolean = false, onClick: (() -> Unit)? = null) {
    val ringPad = size * 0.06f
    Box(
        Modifier
            .size(size)
            .then(if (ring) Modifier.shadow(4.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFED7AA), Color(0xFFFDE68A))))
            .then(if (ring) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(ringPad),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            HubBabyFace(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HubBabyFace(modifier: Modifier) {
    BoxWithConstraints(modifier.clip(CircleShape).background(HubPalette.peach), contentAlignment = Alignment.Center) {
        val u = maxWidth / 12
        // hair tuft
        Box(Modifier.align(Alignment.TopCenter).padding(top = u * 1.6f).size(width = u * 1.8f, height = u * 0.8f).clip(CircleShape).background(Color(0xFF92400E).copy(alpha = 0.4f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(u * 1.2f))
            Row(horizontalArrangement = Arrangement.spacedBy(u * 1.6f)) {
                Box(Modifier.size(u * 0.95f).clip(CircleShape).background(HubPalette.slate800))
                Box(Modifier.size(u * 0.95f).clip(CircleShape).background(HubPalette.slate800))
            }
            Spacer(Modifier.height(u * 0.8f))
            Box(Modifier.size(width = u * 1.5f, height = u * 0.55f).clip(CircleShape).background(HubPalette.rose400))
        }
        // rosy cheeks
        Row(Modifier.fillMaxWidth().padding(top = u * 3.2f, start = u * 1.6f, end = u * 1.6f), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(width = u * 1.4f, height = u * 0.8f).clip(CircleShape).background(Color(0xFFFDA4AF).copy(alpha = 0.7f)))
            Box(Modifier.size(width = u * 1.4f, height = u * 0.8f).clip(CircleShape).background(Color(0xFFFDA4AF).copy(alpha = 0.7f)))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero: child + greeting bubble + mascot, with the pink health book link inside
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubHeroSection(
    isThai: Boolean,
    name: String,
    ageLabel: String,
    avatarUrl: String?,
    onAvatar: () -> Unit,
    onGreeting: () -> Unit,
    onHealthBook: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val bg = if (isDark()) {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFE1EDFB), Color(0xFFE8F1FC), Color(0xFFD9EAFB)))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .clip(shape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = if (isDark()) 0.08f else 0.6f), shape)
    ) {
        // soft sky decorations
        Box(Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = (-24).dp).size(144.dp).clip(CircleShape).background(Color.White.copy(alpha = if (isDark()) 0.04f else 0.4f)))
        Box(Modifier.align(Alignment.BottomStart).offset(x = 40.dp).size(96.dp).clip(CircleShape).background(HubPalette.blue100.copy(alpha = if (isDark()) 0.05f else 0.4f)))

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HubChildAvatar(avatarUrl = avatarUrl, size = 56.dp, ring = true, onClick = onAvatar)
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (isDark()) MaterialTheme.colorScheme.onSurface else Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ageLabel.isNotBlank()) {
                                Text((if (isThai) "อายุ " else "Age ") + ageLabel, fontSize = 12.sp, color = inkSoft(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text(
                        if (isThai) "วันนี้ น้อง $name สบายดีไหม? 💙" else "How is $name today? 💙",
                        fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink(),
                        modifier = Modifier
                            .shadow(1.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardColor())
                            .border(1.dp, if (isDark()) hairline() else HubPalette.blue100, RoundedCornerShape(16.dp))
                            .clickable(onClick = onGreeting)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Image(
                    painter = painterResource(R.drawable.nongrak_mascot_greeting),
                    contentDescription = if (isThai) "มาสคอต NongRak" else "NongRak mascot",
                    modifier = Modifier.width(96.dp).height(112.dp).offset(y = (-4).dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(14.dp))
            HubHealthBookLink(isThai, onHealthBook)
        }
    }
}

@Composable
private fun HubHealthBookLink(isThai: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .clip(shape)
            .background(cardColor().copy(alpha = 0.95f))
            .border(1.dp, Color(0xFFFECDD3).copy(alpha = if (isDark()) 0.3f else 0.8f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(HubPalette.rose400, HubPalette.pink500))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Book, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isThai) "สมุดสุขภาพและบัตรดิจิทัล" else "Health book & digital card",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark()) MaterialTheme.colorScheme.onSurface else Color(0xFF0F172A),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    if (isThai) "บันทึกทางการ" else "Official",
                    fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = HubPalette.rose500, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(Color(0xFFFFE4E6).copy(alpha = if (isDark()) 0.15f else 0.7f)).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Text(if (isThai) "วัคซีน, พัฒนาการ & ประวัติพบแพทย์" else "Vaccines, development & doctor visits", fontSize = 10.sp, color = inkSoft(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = HubPalette.rose400, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// ประวัติกิจกรรม & ส่งต่อการดูแล — deep blue banner
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubActivityCard(
    isThai: Boolean,
    leftLabel: String,
    rightLabel: String,
    onCard: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = HubPalette.blue.copy(alpha = 0.35f), spotColor = HubPalette.blue.copy(alpha = 0.35f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(HubPalette.blueDeep, HubPalette.blue, HubPalette.blueNight)))
            .clickable(onClick = onCard)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.TrendingUp, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(if (isThai) "ประวัติกิจกรรม & ส่งต่อการดูแล" else "Activity history & handover", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(if (isThai) "ดูบันทึกเชิงลึกและส่งคำแนะนำให้ผู้ดูแล" else "See every record and brief the caregiver", fontSize = 11.sp, color = Color(0xFFDBEAFE).copy(alpha = 0.9f))
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), color = Color.White.copy(alpha = 0.2f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubBannerAction(label = leftLabel, filled = false, onClick = onLeft, modifier = Modifier.weight(1f))
            HubBannerAction(label = rightLabel, filled = true, onClick = onRight, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HubBannerAction(label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val fg = if (filled) HubPalette.blueDeep else Color.White
    Row(
        modifier
            .clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.1f))
            .then(if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Filled.ChevronRight, null, tint = fg.copy(alpha = if (filled) 1f else 0.8f), modifier = Modifier.size(14.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// ตัวชี้วัดการเติบโต
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubSectionTitle(title: String, leading: (@Composable () -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink())
        }
        trailing()
    }
}

@Composable
private fun HubGrowthSection(
    isThai: Boolean,
    weightKg: Double?,
    heightCm: Double?,
    weightStatus: com.lullabyai.app.util.GrowthReference.GrowthStatus?,
    heightStatus: com.lullabyai.app.util.GrowthReference.GrowthStatus?,
    onHistory: () -> Unit,
    onCard: () -> Unit
) {
    HubSectionTitle(
        title = if (isThai) "ตัวชี้วัดการเติบโต" else "Growth indicators",
        leading = {
            Row(Modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(8.dp, 12.dp, 16.dp).forEach { h -> Box(Modifier.width(4.dp).height(h).clip(RoundedCornerShape(1.dp)).background(HubPalette.blue)) }
            }
        },
        trailing = {
            Row(Modifier.clip(CircleShape).clickable(onClick = onHistory).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isThai) "ประวัติการบันทึก" else "History", fontSize = 12.sp, color = inkSoft())
                Icon(Icons.Filled.ChevronRight, null, tint = inkSoft(), modifier = Modifier.size(14.dp))
            }
        }
    )
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HubMetricCard(
            icon = Icons.Filled.MonitorWeight,
            label = if (isThai) "น้ำหนัก" else "Weight",
            value = weightKg?.let { "%.1f".format(it) } ?: "-",
            unit = if (isThai) "กก." else "kg",
            status = weightStatus, isThai = isThai,
            modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onCard
        )
        HubMetricCard(
            icon = Icons.Filled.Straighten,
            label = if (isThai) "ส่วนสูง" else "Height",
            value = heightCm?.let { "%.1f".format(it) } ?: "-",
            unit = if (isThai) "ซม." else "cm",
            status = heightStatus, isThai = isThai,
            modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onCard
        )
    }
}

@Composable
private fun HubMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    status: com.lullabyai.app.util.GrowthReference.GrowthStatus?,
    isThai: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .hubSoftShadow(shape)
            .clip(shape)
            .background(cardColor())
            .border(1.dp, hairline(), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (isDark()) HubPalette.blue.copy(alpha = 0.18f) else HubPalette.blueTint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = HubPalette.blue, modifier = Modifier.size(16.dp))
            }
            if (status != null) {
                // GrowthReference marks the normal band green; anything else is the design's amber warning chip.
                val normal = status.color == Color(0xFF2E7D32)
                Text(
                    (if (normal) "✓ " else "! ") + (if (isThai) status.labelTh else status.labelEn),
                    fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    color = if (normal) Color(0xFF15803D) else HubPalette.amber,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (normal) Color(0xFFDCFCE7) else HubPalette.amberBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Column {
            Text(label, fontSize = 12.sp, color = inkFaint())
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink())
                Text(unit, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = inkSoft(), modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// การวิเคราะห์จาก AI
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubAiCard(isThai: Boolean, percent: Int, summary: String?, onRelearn: () -> Unit, onFull: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().hubSoftShadow(shape).clip(shape).background(cardColor()).border(1.dp, hairline(), shape).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✨", fontSize = 18.sp)
                Text(if (isThai) "การวิเคราะห์จาก AI" else "AI analysis", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink())
            }
            Text(
                if (isThai) "เรียนรู้ใหม่" else "Re-analyse",
                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = HubPalette.blue,
                modifier = Modifier.clip(CircleShape).background(if (isDark()) HubPalette.blue.copy(alpha = 0.18f) else HubPalette.blue50)
                    .clickable(onClick = onRelearn).padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = hairline())
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = HubPalette.blue,
                    trackColor = if (isDark()) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE2E8F0),
                    strokeWidth = 8.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink())
                    Text(if (isThai) "คะแนน" else "score", fontSize = 9.sp, color = inkFaint())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(devHeadline(percent, isThai), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink())
                if (summary != null) {
                    Text(summary, fontSize = 11.sp, lineHeight = 17.sp, color = inkSoft())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = HubPalette.blue)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = hairline())
        val linkShape = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(linkShape)
                .background(
                    if (isDark()) Brush.horizontalGradient(listOf(HubPalette.blue.copy(alpha = 0.16f), HubPalette.blue.copy(alpha = 0.10f)))
                    else Brush.horizontalGradient(listOf(HubPalette.blue50, HubPalette.blueTint))
                )
                .border(1.dp, Color(0xFFBFDBFE).copy(alpha = 0.6f), linkShape)
                .clickable(onClick = onFull)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(cardColor()), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Article, null, tint = HubPalette.blue, modifier = Modifier.size(14.dp))
            }
            Text(
                if (isThai) "ดูผลการวิเคราะห์ฉบับเต็มและคำแนะนำ" else "See the full analysis and advice",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HubPalette.blue, modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ChevronRight, null, tint = HubPalette.blue, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ประวัติการแพ้ / โรคประจำตัว
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubHealthQuickCard(
    emoji: String,
    title: String,
    chips: List<String>,
    emptyText: String,
    bg: Color,
    border: Color,
    iconBg: Color,
    titleColor: Color,
    chevron: Color,
    chipBg: Color,
    chipText: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val dark = isDark()
    Column(
        modifier
            .clip(shape)
            .background(if (dark) titleColor.copy(alpha = 0.12f) else bg)
            .border(1.dp, if (dark) titleColor.copy(alpha = 0.25f) else border, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(if (dark) titleColor.copy(alpha = 0.2f) else iconBg), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 12.sp)
                }
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = chevron, modifier = Modifier.size(14.dp))
        }
        if (chips.isEmpty()) {
            Text(emptyText, fontSize = 10.sp, color = inkSoft())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                chips.take(2).forEach { chip ->
                    Text(
                        chip, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = chipText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clip(CircleShape).background(if (dark) titleColor.copy(alpha = 0.2f) else chipBg).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (chips.size > 2) Text("+${chips.size - 2}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chipText)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// แนะนำ — the daily update CTA, chosen from this child's own records
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubUpdateCta(suggestion: ChildHubSuggestions.Suggestion, isThai: Boolean, onAct: () -> Unit) {
    val n = suggestion.days
    val icon = when (suggestion.kind) {
        k.VACCINE_OVERDUE, k.VACCINE_SOON -> Icons.Filled.Vaccines
        k.ILLNESS_FOLLOW_UP -> Icons.Filled.MonitorHeart
        k.GROWTH_NEVER, k.GROWTH_STALE -> Icons.Filled.TrendingUp
        k.MILESTONE -> Icons.Filled.EmojiEvents
        k.SLEEP_TODAY -> Icons.Filled.Bedtime
    }
    val title = when (suggestion.kind) {
        k.VACCINE_OVERDUE -> if (isThai) "แนะนำ: วัคซีนเลยกำหนดมาแล้ว $n วัน" else "Tip: a vaccine is $n days overdue"
        k.VACCINE_SOON -> if (isThai) "แนะนำ: วัคซีนครบกำหนดในอีก $n วัน" else "Tip: a vaccine is due in $n days"
        k.ILLNESS_FOLLOW_UP -> if (isThai) "แนะนำ: ติดตามอาการของลูกวันนี้" else "Tip: follow up on today's symptoms"
        k.GROWTH_NEVER -> if (isThai) "แนะนำ: บันทึกน้ำหนักและส่วนสูงครั้งแรก" else "Tip: record the first weight & height"
        k.GROWTH_STALE -> if (isThai) "แนะนำ: อัปเดตน้ำหนักและส่วนสูงวันนี้" else "Tip: update weight & height today"
        k.MILESTONE -> if (isThai) "แนะนำ: เช็กพัฒนาการที่ยังไม่ผ่าน $n ข้อ" else "Tip: $n milestones still to tick"
        k.SLEEP_TODAY -> if (isThai) "แนะนำ: บันทึกการนอนของวันนี้" else "Tip: log today's sleep"
    }
    val detail = when (suggestion.kind) {
        k.VACCINE_OVERDUE -> if (isThai) "เปิดสมุดวัคซีนเพื่อนัดหมายให้เรียบร้อย" else "Open the vaccine book and book it"
        k.VACCINE_SOON -> if (isThai) "นัดล่วงหน้าไว้ จะได้ไม่พลาดเข็มนี้" else "Book ahead so this dose is not missed"
        k.ILLNESS_FOLLOW_UP -> if (isThai) "บันทึกอาการล่าสุดเมื่อ $n วันก่อน" else "Last symptom entry was $n days ago"
        k.GROWTH_NEVER -> if (isThai) "เพื่อเริ่มกราฟการเติบโตของลูก" else "To start this child's growth chart"
        k.GROWTH_STALE -> if (isThai) "เพื่อติดตามพัฒนาการที่แม่นยำ" else "For accurate development tracking"
        k.MILESTONE -> if (isThai) "ติ๊กเมื่อลูกทำได้ เพื่อให้คำแนะนำแม่นขึ้น" else "Tick them off for sharper advice"
        k.SLEEP_TODAY -> if (isThai) "ช่วยให้คำแนะนำการนอนแม่นยำขึ้น" else "It makes the sleep advice more accurate"
    }
    val action = when (suggestion.kind) {
        k.VACCINE_OVERDUE, k.VACCINE_SOON, k.MILESTONE -> if (isThai) "เปิดดู" else "Open"
        else -> if (isThai) "+ บันทึก" else "+ Record"
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (isDark()) HubPalette.blue.copy(alpha = 0.14f) else HubPalette.blueTint)
            .border(1.dp, if (isDark()) HubPalette.blue.copy(alpha = 0.25f) else HubPalette.blue100, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).shadow(1.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(cardColor()), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = HubPalette.blue, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink())
            Text(detail, fontSize = 10.sp, color = inkSoft())
        }
        Text(
            action, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1,
            modifier = Modifier.shadow(1.dp, CircleShape).clip(CircleShape).background(HubPalette.blue).clickable(onClick = onAct)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// บริการและฟังก์ชันทั้งหมด — one white container, three groups
// ---------------------------------------------------------------------------------------------

private data class HubService(
    val label: String,
    val sub: String?,
    val route: String,
    val icon: ImageVector?,
    val iconColor: Color,
    val tileBg: Color,
    val tileBorder: Color,
    val iconGradient: List<Color>? = null,
    val badge: String? = null,
    val pdfMark: Boolean = false
)

@Composable
private fun HubServicesSection(isThai: Boolean, routineRoute: String, onNavigate: (String) -> Unit) {
    val logging = listOf(
        HubService(if (isThai) "บันทึกด่วน" else "Quick log", if (isThai) "ลงบันทึกทันที" else "Log it now", Screen.QuickLogHub.route,
            Icons.Filled.Bolt, Color.White, Color(0xFFFDF2F8).copy(alpha = 0.7f), Color(0xFFFCE7F3),
            iconGradient = listOf(HubPalette.rose400, HubPalette.pink500), badge = if (isThai) "ด่วน" else "Fast"),
        HubService(if (isThai) "ไดอารี่ลูก" else "Diary", if (isThai) "เรื่องราวความทรงจำ" else "Memories", Screen.ChildDiary.route,
            Icons.Filled.Bookmark, HubPalette.blue, HubPalette.blue50.copy(alpha = 0.6f), HubPalette.blue100.copy(alpha = 0.8f)),
        HubService(if (isThai) "ตารางกิจกรรม" else "Activities", if (isThai) "ตารางและการเล่น" else "Schedule & play", Screen.ActivitySchedule.route,
            Icons.Filled.CalendarMonth, Color(0xFF06B6D4), Color(0xFFECFEFF).copy(alpha = 0.6f), Color(0xFFCFFAFE).copy(alpha = 0.8f)),
        HubService(if (isThai) "กิจวัตรการดูแล" else "Care routine", if (isThai) "ตารางและการดูแล" else "Routine & care", routineRoute,
            Icons.Filled.Schedule, HubPalette.amber, Color(0xFFFFFBEB).copy(alpha = 0.7f), HubPalette.amberBg.copy(alpha = 0.8f))
    )
    val medical = listOf(
        HubService(if (isThai) "ศูนย์สุขภาพ" else "Health hub", if (isThai) "วัคซีนและหมอนัด" else "Vaccines & visits", Screen.ChildHealth.route,
            Icons.Filled.MedicalServices, Color(0xFF34D399), Color(0xFFECFDF5).copy(alpha = 0.7f), Color(0xFFD1FAE5).copy(alpha = 0.8f)),
        HubService(if (isThai) "ดูสถิติ" else "Stats", if (isThai) "กราฟพัฒนาการ" else "Growth charts", Screen.StatisticsInsights.route,
            Icons.Filled.BarChart, HubPalette.blue, HubPalette.blue50.copy(alpha = 0.6f), HubPalette.blue100.copy(alpha = 0.8f)),
        HubService(if (isThai) "บันทึกสุขภาพ" else "Health log", if (isThai) "อาการ & ยาที่ใช้" else "Symptoms & meds", Screen.HealthLog.route,
            Icons.Filled.Add, Color(0xFF16A34A), Color(0xFFDCFCE7).copy(alpha = 0.7f), Color(0xFFD1FAE5).copy(alpha = 0.8f))
    )
    val familyWide = listOf(
        HubService(if (isThai) "แชทผู้ดูแล" else "Care chat", if (isThai) "ติดต่อพี่เลี้ยงทันที" else "Reach the caregiver", Screen.CareCircle.route,
            Icons.Filled.ChatBubbleOutline, Color(0xFF38BDF8), Color(0xFFF0F9FF).copy(alpha = 0.7f), Color(0xFFE0F2FE).copy(alpha = 0.8f)),
        HubService(if (isThai) "ค่าใช้จ่าย" else "Expenses", if (isThai) "บันทึกงบประมาณ" else "Budget log", Screen.Expenses.route,
            Icons.Filled.AccountBalanceWallet, Color(0xFF059669), Color(0xFFECFDF5), Color(0xFFD1FAE5).copy(alpha = 0.8f))
    )
    val familyCompact = listOf(
        HubService(if (isThai) "คลังเอกสาร" else "Documents", null, Screen.MedicalDataVault.route,
            Icons.Filled.Folder, Color(0xFF0284C7), Color(0xFFE0F2FE), HubPalette.blue100),
        HubService(if (isThai) "สิทธิ์เข้าถึง" else "Access", null, Screen.PermissionAccess.route,
            Icons.Filled.Lock, Color(0xFF4F46E5), Color(0xFFDDD6FE).copy(alpha = 0.6f), Color(0xFFC7D2FE)),
        HubService(if (isThai) "ส่งออก PDF" else "Export PDF", null, Screen.PdfExport.route,
            null, Color(0xFFEF4444), Color(0xFFFFE4E6), Color(0xFFFECDD3), pdfMark = true)
    )
    val total = logging.size + medical.size + familyWide.size + familyCompact.size

    Column(Modifier.fillMaxWidth().padding(top = 8.dp).tourAnchor("parent_child_actions"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isThai) "บริการและฟังก์ชันทั้งหมด" else "All services", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(
                    if (isThai) "$total รายการ" else "$total items",
                    fontSize = 10.sp, fontWeight = FontWeight.Medium, color = HubPalette.blue, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(if (isDark()) HubPalette.blue.copy(alpha = 0.18f) else HubPalette.blue50)
                        .border(1.dp, HubPalette.blue100.copy(alpha = if (isDark()) 0.2f else 1f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(if (isThai) "ครบทุกการดูแล" else "Everything in one place", fontSize = 11.sp, color = inkFaint())
        }

        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.fillMaxWidth().hubSoftShadow(shape).clip(shape).background(cardColor()).border(1.dp, hairline(), shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HubServiceGroup(if (isThai) "บันทึกและกิจวัตร" else "Logs & routine", if (isThai) "อัปเดตล่าสุดวันนี้" else "Updated today", HubPalette.blue) {
                HubTileRow { logging.take(3).forEach { HubStackTile(it, onNavigate, Modifier.weight(1f).fillMaxHeight()) } }
                HubStackTile(logging[3], onNavigate, Modifier.fillMaxWidth())
            }
            HorizontalDivider(color = hairline())
            HubServiceGroup(if (isThai) "การแพทย์และพัฒนาการ" else "Medical & development", if (isThai) "สุขภาพ & ตัวชี้วัด" else "Health & metrics", Color(0xFF34D399)) {
                HubTileRow { medical.forEach { HubStackTile(it, onNavigate, Modifier.weight(1f).fillMaxHeight()) } }
            }
            HorizontalDivider(color = hairline())
            HubServiceGroup(if (isThai) "การจัดการและเอกสาร" else "Management & documents", if (isThai) "ครอบครัว & บัญชี" else "Family & account", Color(0xFFA855F7)) {
                HubTileRow { familyWide.forEach { HubWideTile(it, onNavigate, Modifier.weight(1f).fillMaxHeight()) } }
                HubTileRow { familyCompact.forEach { HubCompactTile(it, onNavigate, Modifier.weight(1f).fillMaxHeight()) } }
            }
        }
    }
}

@Composable
private fun HubServiceGroup(title: String, subtitle: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(6.dp).height(12.dp).clip(CircleShape).background(accent))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isDark()) MaterialTheme.colorScheme.onSurface else HubPalette.slate700, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, fontSize = 10.sp, color = inkFaint(), maxLines = 1)
        }
        content()
    }
}

@Composable
private fun HubTileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun hubTileColors(item: HubService): Pair<Color, Color> =
    if (isDark()) item.iconColor.copy(alpha = 0.12f) to item.iconColor.copy(alpha = 0.25f) else item.tileBg to item.tileBorder

@Composable
private fun HubIconSquare(item: HubService, size: Dp, iconSize: Dp) {
    Box(
        Modifier.size(size).shadow(1.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(item.iconGradient ?: listOf(item.iconColor, item.iconColor))),
        contentAlignment = Alignment.Center
    ) { item.icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(iconSize)) } }
}

@Composable
private fun HubStackTile(item: HubService, onNavigate: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val (bg, border) = hubTileColors(item)
    Box(modifier.clip(shape).background(bg).border(1.dp, border, shape).clickable { onNavigate(item.route) }) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HubIconSquare(item, 40.dp, 20.dp)
            Spacer(Modifier.height(6.dp))
            Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            item.sub?.let { Text(it, fontSize = 9.sp, color = inkFaint(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp)) }
        }
        item.badge?.let {
            Text(
                it, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(HubPalette.rose500).padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun HubWideTile(item: HubService, onNavigate: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val (bg, border) = hubTileColors(item)
    Row(
        modifier.clip(shape).background(bg).border(1.dp, border, shape).clickable { onNavigate(item.route) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HubIconSquare(item, 40.dp, 20.dp)
        Column(Modifier.weight(1f)) {
            Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.sub?.let { Text(it, fontSize = 9.sp, color = inkFaint(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun HubCompactTile(item: HubService, onNavigate: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val dark = isDark()
    Column(
        modifier.clip(shape)
            .background(if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else Color(0xFFF8FAFC))
            .border(1.dp, if (dark) hairline() else Color(0xFFE2E8F0).copy(alpha = 0.8f), shape)
            .clickable { onNavigate(item.route) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                .background(if (dark) item.iconColor.copy(alpha = 0.18f) else item.tileBg)
                .border(1.dp, if (dark) item.iconColor.copy(alpha = 0.3f) else item.tileBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.pdfMark) {
                Text("PDF", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(item.iconColor).padding(horizontal = 3.dp))
            } else item.icon?.let { Icon(it, null, tint = item.iconColor, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (dark) MaterialTheme.colorScheme.onSurface else HubPalette.slate700, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------------------------
// Child switcher sheet (the design's "เลือกบัญชีลูก" popover, as a bottom sheet)
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubChildSwitcherSheet(
    isThai: Boolean,
    children: List<Child>,
    activeChildId: String?,
    avatarUrl: String?,
    ownedChildIds: Set<String>,
    /** Open questions per child id — the red badge that says who is still waiting on an answer. */
    questionCounts: Map<String, Int>,
    onSelect: (Child) -> Unit,
    onDelete: (Child) -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cardColor()) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isThai) "เลือกบัญชีลูก" else "Choose child", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink())
                    Text(if (isThai) "(${children.size} คน)" else "(${children.size})", fontSize = 12.sp, color = inkFaint())
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = inkSoft()) }
            }
            children.forEach { c ->
                val active = c.id == activeChildId
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(if (active) HubPalette.blue.copy(alpha = if (isDark()) 0.16f else 0.07f) else Color.Transparent)
                        .border(1.dp, if (active) HubPalette.blue.copy(alpha = 0.3f) else hairline(), shape)
                        .clickable { onSelect(c) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val openQuestions = questionCounts[c.id] ?: 0
                    Box {
                        HubChildAvatar(avatarUrl = if (active) avatarUrl else null, size = 40.dp)
                        if (openQuestions > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(cardColor())
                                    .padding(1.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE11D48)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (openQuestions > 9) "9+" else "$openQuestions",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isThai) "น้อง ${c.name}" else c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (active && !isDark()) HubPalette.navy else ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (active) {
                                Text(if (isThai) "ใช้งานอยู่" else "Active", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1D4ED8),
                                    modifier = Modifier.clip(CircleShape).background(HubPalette.blue100).padding(horizontal = 6.dp))
                            }
                        }
                        Text(
                            listOfNotNull(
                                hubAgeLabel(c.birthdate, isThai).takeIf { it.isNotBlank() }?.let { (if (isThai) "อายุ " else "Age ") + it },
                                genderLabel(c.gender, isThai)
                            ).joinToString(" • "),
                            fontSize = 11.sp, color = inkSoft()
                        )
                        // Says out loud what the red badge means, so the parent knows there is
                        // something to do here rather than just that something is unread.
                        if (openQuestions > 0) {
                            Text(
                                if (isThai) "มีคำถาม $openQuestions ข้อรอคุณตอบ"
                                else "$openQuestions question${if (openQuestions == 1) "" else "s"} waiting for you",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE11D48)
                            )
                        }
                    }
                    if (c.id in ownedChildIds) {
                        IconButton(onClick = { onDelete(c) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.DeleteOutline, if (isThai) "ลบเด็ก" else "Delete child", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (active) Icon(Icons.Filled.CheckCircle, null, tint = HubPalette.blue, modifier = Modifier.size(20.dp))
                }
            }
            val editShape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().clip(editShape).border(1.dp, hairline(), editShape).clickable(onClick = onEdit).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Edit, null, tint = HubPalette.blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isThai) "แก้ไขข้อมูลลูก / เปลี่ยนรูป" else "Edit child / change photo", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HubPalette.blue)
            }
            Row(
                Modifier.fillMaxWidth().clip(editShape)
                    .background(HubPalette.blue.copy(alpha = if (isDark()) 0.10f else 0.04f))
                    .border(BorderStroke(1.dp, HubPalette.blue.copy(alpha = 0.3f)), editShape)
                    .clickable(onClick = onAddChild).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Add, null, tint = HubPalette.blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isThai) "เพิ่มโปรไฟล์ลูกคนใหม่" else "Add another child", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HubPalette.blue)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Suggestion signals (what the แนะนำ strip reads)
// ---------------------------------------------------------------------------------------------

/** Dates come as full timestamps or plain days; an unparseable one is ignored rather than invented. */
private fun hubSignals(state: com.lullabyai.app.viewmodel.ChildUiState, ageMonths: Int?): ChildHubSuggestions.Signals {
    val zone = java.time.ZoneId.systemDefault()
    fun day(iso: String?): java.time.LocalDate? {
        val raw = iso?.trim().orEmpty()
        if (raw.isBlank()) return null
        runCatching { return java.time.OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDate() }
        return runCatching { java.time.LocalDate.parse(raw.take(10)) }.getOrNull()
    }
    return ChildHubSuggestions.Signals(
        today = java.time.LocalDate.now(zone),
        lastGrowthOn = state.growthRecords.mapNotNull { day(it.recordedAt) }.maxOrNull(),
        nextVaccineDueOn = state.vaccines.filter { it.status != "completed" && it.status != "skipped" }.mapNotNull { day(it.scheduledDate) }.minOrNull(),
        lastIllnessOn = state.healthRecords.mapNotNull { day(it.recordedAt) }.maxOrNull(),
        lastSleepOn = state.sleepLogs.mapNotNull { day(it.startTime) }.maxOrNull(),
        pendingMilestones = state.milestones.count { !it.isAchieved },
        ageMonths = ageMonths
    )
}

private fun hubSuggestionRoute(kind: ChildHubSuggestions.Kind): String = when (kind) {
    ChildHubSuggestions.Kind.VACCINE_OVERDUE, ChildHubSuggestions.Kind.VACCINE_SOON -> Screen.VaccineBook.route
    ChildHubSuggestions.Kind.ILLNESS_FOLLOW_UP -> Screen.HealthLog.route
    ChildHubSuggestions.Kind.GROWTH_NEVER, ChildHubSuggestions.Kind.GROWTH_STALE -> Screen.AddGrowthRecord.route
    ChildHubSuggestions.Kind.MILESTONE -> Screen.ChildDevelopment.route
    ChildHubSuggestions.Kind.SLEEP_TODAY -> Screen.SleepTracker.route
}

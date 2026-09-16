@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.lullabyai.app.R
import androidx.core.content.FileProvider
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.NoChildDataCard
import com.lullabyai.app.data.ChildHubSuggestions.Kind
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.onboarding.tourAnchor
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.OnboardingViewModel

private val THAI_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
private val ENG_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
internal fun formatDate(iso: String?, isThai: Boolean): String = try {
    val p = iso!!.split("-")
    val day = p[2].toInt(); val month = p[1].toInt() - 1; val year = p[0].toInt()
    if (isThai) "$day ${THAI_MONTHS[month]} ${year + 543}" else "$day ${ENG_MONTHS[month]} $year"
} catch (e: Exception) { iso ?: "" }

/** Short bold headline for the AI Analysis card, synthesized from the milestone-achieved percentage
 *  (there's no separate AI-generated headline field — the mockup pairs the score ring with a title
 *  line, so this derives one deterministically rather than adding another AI call). */
internal fun devHeadline(devPercent: Int, isThai: Boolean): String = when {
    devPercent >= 70 -> if (isThai) "พัฒนาการโดยรวมดีมาก" else "Overall development is excellent"
    devPercent >= 40 -> if (isThai) "พัฒนาการโดยรวมอยู่ในเกณฑ์ปกติ" else "Overall development is on track"
    else -> if (isThai) "ควรติดตามพัฒนาการเพิ่มเติม" else "Development needs closer tracking"
}

/** Human label for a child's sex. Anything that isn't explicitly male/female is shown as "not told"
 *  rather than guessing a gender. Shared across the child screens. */
internal fun genderLabel(gender: String?, isThai: Boolean): String = when (gender) {
    "male" -> if (isThai) "ชาย" else "Male"
    "female" -> if (isThai) "หญิง" else "Female"
    else -> if (isThai) "ไม่ได้ระบุ" else "Not specified"
}

/** Hero line from the Stitch hub: "อายุ 7 เดือน 1 สัปดาห์". */
internal fun hubAgeLabel(birthdate: String?, isThai: Boolean): String {
    val dob = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull() ?: return ""
    val p = java.time.Period.between(dob, java.time.LocalDate.now())
    val weeks = p.days / 7
    return if (isThai) {
        when {
            p.years > 0 && p.months > 0 -> "${p.years} ปี ${p.months} เดือน"
            p.years > 0 -> "${p.years} ปี"
            weeks > 0 -> "${p.months} เดือน $weeks สัปดาห์"
            else -> "${p.months} เดือน"
        }
    } else {
        when {
            p.years > 0 && p.months > 0 -> "${p.years}y ${p.months}mo"
            p.years > 0 -> "${p.years}y"
            weeks > 0 -> "${p.months} mo $weeks wk"
            else -> "${p.months} mo"
        }
    }
}

internal fun hubAgeMonthsOnly(birthdate: String?, isThai: Boolean): String {
    val dob = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull() ?: return ""
    val months = java.time.Period.between(dob, java.time.LocalDate.now()).toTotalMonths()
    return if (isThai) "$months เดือน" else "$months mo"
}

/** One tile in a Child-info Bento action group. */
data class ChildTile(
    val icon: ImageVector,
    val label: String,
    val route: String,
    val iconTint: Color = Primary,
    val iconBg: Color = PrimaryContainer.copy(0.45f),
    val badge: String? = null,
    /** Second line under the label, as in the Stitch hub design. */
    val sub: String? = null
)

/**
 * Hub service tiles. [rowSizes] is the Stitch column pattern (3 then 2 then 3, etc.).
 * [featured] is the full-width cream row used for กิจวัตรการดูแล.
 */
@Composable
fun ChildActionGroup(
    title: String,
    tiles: List<ChildTile>,
    onNavigate: (String) -> Unit,
    subtitle: String? = null,
    rowSizes: List<Int> = listOf(2),
    featured: ChildTile? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title.isNotBlank() || subtitle != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        var index = 0
        rowSizes.forEach { size ->
            val slice = tiles.drop(index).take(size)
            index += slice.size
            if (slice.isEmpty()) return@forEach
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                slice.forEach { t ->
                    HubServiceTile(
                        tile = t,
                        onClick = { if (t.route.isNotBlank()) onNavigate(t.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(size - slice.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (featured != null) {
            HubFeaturedTile(tile = featured, onClick = {
                if (featured.route.isNotBlank()) onNavigate(featured.route)
            })
        }
    }
}

@Composable
private fun HubServiceTile(tile: ChildTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (com.lullabyai.app.ui.theme.LocalDarkTheme.current) tile.iconTint.copy(alpha = 0.18f)
                            else tile.iconBg
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tile.icon, null, tint = tile.iconTint, modifier = Modifier.size(18.dp))
                }
                if (tile.badge != null) {
                    Surface(shape = RoundedCornerShape(100.dp), color = tile.iconTint.copy(alpha = 0.12f)) {
                        Text(
                            tile.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = tile.iconTint,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tile.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (tile.sub != null) {
                    Text(
                        tile.sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HubFeaturedTile(tile: ChildTile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = tile.iconBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(tile.icon, null, tint = tile.iconTint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(tile.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (tile.sub != null) {
                    Text(tile.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ChildSummaryScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onboardingViewModel: OnboardingViewModel,
    onBack: () -> Unit,
    isTourMode: Boolean = false
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val tourPreview = isTourMode && !childState.isLoading && childState.children.isEmpty()
    val child = if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.child else childState.child
    var childToDelete by remember { mutableStateOf<com.lullabyai.app.data.model.Child?>(null) }
    val milestones = if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.sampleMilestones else childState.milestones
    val devPercent = if (milestones.isNotEmpty()) milestones.count { it.isAchieved } * 100 / milestones.size else 0
    val categories = remember(milestones) { milestones.map { it.category }.distinct().take(2) }

    LaunchedEffect(child, tourPreview) {
        if (tourPreview) return@LaunchedEffect
        child?.let {
            chatViewModel.loadChildInfoSummary(it, s.isThai)
            chatViewModel.loadChildInfoCardSummary(it, s.isThai)
        }
    }

    // This screen never triggered its own load before (it relied on Dashboard having already
    // populated the shared ChildViewModel) — fine when Dashboard is always visited first, but leaves
    // isLoading stuck at its true default forever if this screen is ever reached directly. Guarded
    // so it doesn't re-fetch on every tab switch once data is already loaded.
    LaunchedEffect(Unit) {
        if (!tourPreview && childState.children.isEmpty() && childState.child == null) childViewModel.loadChild()
    }

    // Typed info-ask — skip during tour preview (no real child yet).
    val context = LocalContext.current
    LaunchedEffect(child?.id, tourPreview) {
        if (tourPreview) return@LaunchedEffect
        child?.let { childViewModel.loadInfoAsk(context, s.isThai) }
    }

    // If the active child changed elsewhere (added a child, or switched on another screen), reload.
    val selectedId by com.lullabyai.app.data.settings.ChildSelection.id.collectAsState()
    LaunchedEffect(selectedId) {
        val current = childState.child?.id
        if (selectedId != null && current != null && selectedId != current) {
            childViewModel.selectChild(selectedId!!)
        }
    }

    // showBack = false: this is the CHILD tab ROOT, so the footer is the way out (#6).
    var showSwitcherSheet by remember { mutableStateOf(false) }
    val displayName = if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.displayName(s.isThai)
        else child?.name ?: (if (s.isThai) "ลูกของคุณ" else "Your child")
    ChildScreenScaffold(
        title = if (s.isThai) "ข้อมูลของเด็ก" else "Child Info",
        currentTab = "overview",
        navController = navController,
        showBack = false,
        titleContent = if (child != null || tourPreview) {
            {
                ChildHubSwitcherPill(
                    isThai = s.isThai,
                    name = displayName,
                    ageMonths = if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.ageLabel(s.isThai)
                    else hubAgeMonthsOnly(child?.birthdate, s.isThai),
                    avatarUrl = childState.childAvatarSignedUrl,
                    onClick = { showSwitcherSheet = true }
                )
            }
        } else null
    ) { padding ->
        if (childState.isLoading && childState.children.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        } else if (childState.children.isEmpty() && !childState.isLoading && childState.error != null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            com.lullabyai.app.ui.components.ChildLoadErrorCard(s.isThai) { childViewModel.loadChild() }
        }
        } else if (!tourPreview && childState.children.isEmpty() && !childState.isLoading) {
            NoChildDataCard(s.isThai) {
                if (childViewModel.isGuest()) {
                    childViewModel.promptRegistration(if (s.isThai) "ต้องสมัครสมาชิกก่อนเพิ่มข้อมูลลูก" else "Registration required to add a child")
                } else {
                    navController.navigate(Screen.AddChildChoice.route)
                }
            }
        } else {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Same button, destination by role: someone whose job here is the daily log lands on
            // the update form, everyone else on the history. Full width and high on the page —
            // for a nanny this is the screen's primary action, not one tile among fourteen.
            val careRoleVm: com.lullabyai.app.viewmodel.CareRoleViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()
            LaunchedEffect(child?.id) { careRoleVm.load(child?.id) }
            val amCaregiver by careRoleVm.amCaregiver.collectAsState()

            if (childState.infoAsks.isNotEmpty() && !tourPreview) {
                val prompts = childState.infoAsks
                com.lullabyai.app.ui.components.InfoAskBanner(
                    isThai = s.isThai,
                    subjectName = com.lullabyai.app.data.infoask.InfoAskCatalog.bannerSubjectName(
                        prompts, child?.name.orEmpty(), s.isThai
                    ),
                    questionCount = prompts.size,
                    preview = prompts.take(2).joinToString("\n") { "• ${it.question(s.isThai)}" },
                    onClick = { navController.navigate(Screen.InfoAsk.route) },
                    onDismiss = { childViewModel.dismissInfoAsk(context) }
                )
            }

            if (showSwitcherSheet) {
                ChildSwitcherSheet(
                    isThai = s.isThai,
                    children = if (tourPreview) listOf(com.lullabyai.app.ui.screens.onboarding.TourSampleData.child) else childState.children,
                    activeChildId = child?.id,
                    childAvatarSignedUrl = childState.childAvatarSignedUrl,
                    ownedChildIds = childState.ownedChildIds,
                    onSelect = { c -> showSwitcherSheet = false; childViewModel.selectChild(c.id) },
                    onDelete = { c ->
                        showSwitcherSheet = false
                        if (childViewModel.isGuest()) {
                            childViewModel.promptRegistration(if (s.isThai) "บัญชีทดลองไม่สามารถลบโปรไฟล์ได้" else "Cannot delete profile in demo mode")
                        } else {
                            childToDelete = c
                        }
                    },
                    onAddChild = {
                        showSwitcherSheet = false
                        if (childViewModel.isGuest()) {
                            childViewModel.promptRegistration(if (s.isThai) "ต้องสมัครสมาชิกก่อนเพิ่มข้อมูลลูก" else "Registration required to add a child")
                        } else {
                            navController.navigate(Screen.AddChildChoice.route)
                        }
                    },
                    onEdit = {
                        showSwitcherSheet = false
                        navController.navigate(Screen.EditChild.route)
                    },
                    onDismiss = { showSwitcherSheet = false }
                )
            }

            ChildHubHeroCard(
                isThai = s.isThai,
                name = displayName,
                ageLabel = if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.ageLabel(s.isThai)
                else hubAgeLabel(child?.birthdate, s.isThai),
                avatarUrl = childState.childAvatarSignedUrl,
                onAvatarClick = { navController.navigate(Screen.EditChild.route) },
                onMoodClick = { navController.navigate(Screen.QuickLogHub.route) }
            )

            // SECTION: สมุดสุขภาพและบัตรดิจิทัล - the design's official-records row.
            ChildHealthBookCard(
                isThai = s.isThai,
                onOpen = { navController.navigate(Screen.HealthBook.route) }
            )

            // SECTION: ประวัติกิจกรรม & ส่งต่อการดูแล - one card, two actions, as in the design.
            ActivityHandoverCard(
                isThai = s.isThai,
                amCaregiver = amCaregiver,
                onHistory = { navController.navigate(Screen.ActivityHistory.route) },
                onQuickRecord = {
                    navController.navigate(
                        if (amCaregiver) Screen.CaregiverUpdate.route else Screen.QuickLogHub.route
                    )
                },
                onHandover = {
                    child?.let {
                        navController.navigate(
                            if (amCaregiver) Screen.CareHandoverChecklist.route(it.id)
                            else Screen.CareHandoff.route(it.id)
                        )
                    }
                }
            )
            if (!tourPreview && child != null && !amCaregiver) {
                com.lullabyai.app.ui.screens.access.HandoffHomeCard(child.id,s.isThai,
                    onOpen = { navController.navigate(Screen.CareHandoff.route(child.id)) })
            }

            // Growth indicators — moved here (right after the profile card, before AI Analysis) to
            // match the 30.new_dash mockup's order; previously sat lower down the page.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.isThai) "ตัวชี้วัดการเติบโต" else "Growth indicators", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(onClick = { navController.navigate(Screen.ProfileHistory.route) }, shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (s.isThai) "ประวัติการบันทึก" else "History", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // WHO status by sex, at the age each reading was taken (the tour sample has a fixed age).
                val (weightStatus, heightStatus) = com.lullabyai.app.util.GrowthReference.current(
                    child, childState.growthRecords,
                    if (tourPreview) com.lullabyai.app.util.GrowthReference.monthsToDays(com.lullabyai.app.ui.screens.onboarding.TourSampleData.ageMonths()) else null
                )
                GrowthStatCard(
                    if (s.isThai) "น้ำหนัก" else "Weight", "${child?.weightKg ?: "-"} " + (if (s.isThai) "กก." else "kg"),
                    (if (s.isThai) weightStatus?.labelTh else weightStatus?.labelEn) ?: (if (s.isThai) "ยังไม่มีข้อมูล" else "No data"),
                    Modifier.weight(1f), statusColor = weightStatus?.color ?: Outline, icon = Icons.Filled.MonitorWeight
                ) { navController.navigate(Screen.GrowthHistory.route) }
                GrowthStatCard(
                    if (s.isThai) "ส่วนสูง" else "Height", "${child?.heightCm ?: "-"} " + (if (s.isThai) "ซม." else "cm"),
                    (if (s.isThai) heightStatus?.labelTh else heightStatus?.labelEn) ?: (if (s.isThai) "ยังไม่มีข้อมูล" else "No data"),
                    Modifier.weight(1f), statusColor = heightStatus?.color ?: Outline, icon = Icons.Filled.Straighten
                ) { navController.navigate(Screen.GrowthHistory.route) }
            }

            // AI Analysis card — headline synthesized from the development percentage (no separate
            // AI field for it), short summary from generateChildInfoCardSummary (distinct from the
            // fuller text used on the health-check/monthly-summary pages).
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(if (s.isThai) "การวิเคราะห์จาก AI" else "AI Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.primary.copy(0.08f)) {
                            Text(if (s.isThai) "เรียลไทม์" else "Real-time", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                                CircularProgressIndicator(progress = { devPercent / 100f }, modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 7.dp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$devPercent%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(if (s.isThai) "คะแนน" else "Score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 8.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    devHeadline(devPercent, s.isThai),
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                                )
                                if (chatState.childInfoCardSummary != null) {
                                    Text(chatState.childInfoCardSummary!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (categories.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                categories.forEach { cat ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(Primary))
                                            Text(catLabel(cat, s.isThai), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                onClick = { navController.navigate(AI_GUIDANCE_ROUTE) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        if (s.isThai) "ดูผลการวิเคราะห์ฉบับเต็มและคำแนะนำ" else "See the full analysis and advice",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }

            // Medical Info 2-Column Bento Grid (Allergies & Chronic Conditions side-by-side matching Stitch)
            // Medical Info 2-Column Bento Grid (Allergies & Chronic Conditions side-by-side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Allergies Card
                Card(
                    onClick = { navController.navigate(Screen.AllergyHistory.route) },
                    modifier = Modifier
                        .weight(1f)
                        .height(135.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (childState.allergies.isNotEmpty()) Color(0xFFFFF1F2)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (childState.allergies.isNotEmpty()) Error.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = if (childState.allergies.isNotEmpty()) Error else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                if (s.isThai) "ประวัติการแพ้" else "Allergies",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (childState.allergies.isNotEmpty()) Error else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }

                        if (childState.allergies.isEmpty()) {
                            Text(
                                if (s.isThai) "ไม่มีประวัติแพ้" else "No known allergies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                childState.allergies.take(2).forEach { a ->
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = Error.copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Error.copy(alpha = 0.25f))
                                    ) {
                                        Text(
                                            a.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Error,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (childState.allergies.size > 2) {
                                    Text(
                                        "+${childState.allergies.size - 2}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Error,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Chronic Conditions Card
                Card(
                    onClick = { navController.navigate(Screen.ChildHealth.route) },
                    modifier = Modifier
                        .weight(1f)
                        .height(135.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (childState.chronicConditions.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (childState.chronicConditions.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.MedicalServices,
                                    contentDescription = null,
                                    tint = if (childState.chronicConditions.isNotEmpty()) Primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                if (s.isThai) "โรคประจำตัว" else "Chronic info",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (childState.chronicConditions.isNotEmpty()) Primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }

                        if (childState.chronicConditions.isEmpty()) {
                            Text(
                                if (s.isThai) "ไม่มีข้อมูล" else "None recorded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                childState.chronicConditions.take(2).forEach { cc ->
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    ) {
                                        Text(
                                            cc.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (childState.chronicConditions.size > 2) {
                                    Text(
                                        "+${childState.chronicConditions.size - 2}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: แนะนำ - one nudge chosen from what this child actually has recorded
            // (a due vaccine, an illness still running, stale growth...). Hidden when nothing is due.
            val hubSuggestion = remember(childState, tourPreview) {
                if (tourPreview) null
                else com.lullabyai.app.data.ChildHubSuggestions.suggest(
                    childHubSignals(childState, childViewModel.getAgeMonths())
                )
            }
            if (hubSuggestion != null) {
                ChildSuggestionCard(
                    suggestion = hubSuggestion,
                    isThai = s.isThai,
                    onAct = { navController.navigate(suggestionRoute(hubSuggestion.kind)) }
                )
            }

            // SECTION: บริการและฟังก์ชันทั้งหมด - grouped exactly as the hub design groups them.
            Column(
                Modifier.fillMaxWidth().tourAnchor("parent_child_actions"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (s.isThai) "บริการและฟังก์ชันทั้งหมด" else "All services",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (s.isThai) "ครบทุกการดูแล" else "Everything for this child",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            if (s.isThai) "13 รายการ" else "13 items",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                ChildActionGroup(
                    title = if (s.isThai) "บันทึกและกิจวัตร" else "Logging & routine",
                    subtitle = if (s.isThai) "อัปเดตล่าสุดวันนี้" else "Updated today",
                    rowSizes = listOf(3),
                    tiles = listOf(
                        ChildTile(
                            icon = Icons.Filled.Bolt,
                            label = if (s.isThai) "บันทึกด่วน" else "Quick log",
                            sub = if (s.isThai) "ลงบันทึกทันที" else "Log it now",
                            route = Screen.QuickLogHub.route,
                            badge = if (s.isThai) "ด่วน" else "Fast",
                            iconTint = Color(0xFFC2185B),
                            iconBg = Color(0xFFFCE4EC)
                        ),
                        ChildTile(
                            icon = Icons.Filled.AutoStories,
                            label = if (s.isThai) "ไดอารี่ลูก" else "Diary",
                            sub = if (s.isThai) "เรื่องราวความทรงจำ" else "Memories",
                            route = Screen.ChildDiary.route,
                            iconTint = Color(0xFF1565C0),
                            iconBg = Color(0xFFE3F2FD)
                        ),
                        ChildTile(
                            icon = Icons.Filled.EventRepeat,
                            label = if (s.isThai) "ตารางกิจกรรม" else "Activities",
                            sub = if (s.isThai) "ตารางและการเล่น" else "Schedule & play",
                            route = Screen.ActivitySchedule.route,
                            iconTint = Color(0xFF0288D1),
                            iconBg = Color(0xFFE1F5FE)
                        )
                    ),
                    featured = ChildTile(
                        icon = Icons.Filled.Schedule,
                        label = if (s.isThai) "กิจวัตรการดูแล" else "Care routine",
                        sub = if (s.isThai) "ตารางและการดูแล" else "Routine & handover",
                        route = child?.id?.let { Screen.CareRoutine.route(it) } ?: "",
                        iconTint = Color(0xFFEF6C00),
                        iconBg = Color(0xFFFFF6E5)
                    ),
                    onNavigate = { navController.navigate(it) }
                )

                ChildActionGroup(
                    title = if (s.isThai) "การแพทย์และพัฒนาการ" else "Medical & development",
                    subtitle = if (s.isThai) "สุขภาพ & ตัวชี้วัด" else "Health & metrics",
                    rowSizes = listOf(3),
                    tiles = listOf(
                        ChildTile(
                            icon = Icons.Filled.MedicalServices,
                            label = if (s.isThai) "ศูนย์สุขภาพ" else "Health hub",
                            sub = if (s.isThai) "วัคซีนและหมอนัด" else "Vaccines & visits",
                            route = Screen.ChildHealth.route,
                            iconTint = Color(0xFF00897B),
                            iconBg = Color(0xFFE0F2F1)
                        ),
                        ChildTile(
                            icon = Icons.Filled.Analytics,
                            label = if (s.isThai) "ดูสถิติ" else "Stats",
                            sub = if (s.isThai) "กราฟพัฒนาการ" else "Growth charts",
                            route = Screen.StatisticsInsights.route,
                            iconTint = Color(0xFF3949AB),
                            iconBg = Color(0xFFE8EAF6)
                        ),
                        ChildTile(
                            icon = Icons.Filled.AddCircle,
                            label = if (s.isThai) "บันทึกสุขภาพ" else "Health log",
                            sub = if (s.isThai) "อาการ & ยาที่ใช้" else "Symptoms & meds",
                            route = Screen.HealthLog.route,
                            iconTint = Color(0xFF0288D1),
                            iconBg = Color(0xFFE1F5FE)
                        )
                    ),
                    onNavigate = { navController.navigate(it) }
                )

                ChildActionGroup(
                    title = if (s.isThai) "การจัดการและเอกสาร" else "Family & documents",
                    subtitle = if (s.isThai) "ครอบครัว & บัญชี" else "Family & account",
                    rowSizes = listOf(2, 3),
                    tiles = listOf(
                        ChildTile(
                            icon = Icons.Filled.Chat,
                            label = if (s.isThai) "แชทผู้ดูแล" else "Care chat",
                            sub = if (s.isThai) "ติดต่อพี่เลี้ยงทันที" else "Reach the caregiver",
                            route = Screen.CareCircle.route,
                            iconTint = Color(0xFF1565C0),
                            iconBg = Color(0xFFE3F2FD)
                        ),
                        ChildTile(
                            icon = Icons.Filled.Payments,
                            label = if (s.isThai) "ค่าใช้จ่าย" else "Expenses",
                            sub = if (s.isThai) "บันทึกงบประมาณ" else "Budget log",
                            route = Screen.Expenses.route,
                            iconTint = Color(0xFF2E7D32),
                            iconBg = Color(0xFFE8F5E9)
                        ),
                        ChildTile(
                            icon = Icons.Filled.FolderOpen,
                            label = if (s.isThai) "คลังเอกสาร" else "Document vault",
                            sub = if (s.isThai) "เอกสารการแพทย์" else "Medical files",
                            route = Screen.MedicalDataVault.route,
                            iconTint = Color(0xFF0097A7),
                            iconBg = Color(0xFFE0F7FA)
                        ),
                        ChildTile(
                            icon = Icons.Filled.LockPerson,
                            label = if (s.isThai) "สิทธิ์เข้าถึง" else "Access & roles",
                            sub = if (s.isThai) "ใครดูข้อมูลได้บ้าง" else "Who can see this",
                            route = Screen.PermissionAccess.route,
                            iconTint = Color(0xFF5E35B1),
                            iconBg = Color(0xFFEDE7F6)
                        ),
                        ChildTile(
                            icon = Icons.Filled.PictureAsPdf,
                            label = if (s.isThai) "ส่งออก PDF" else "Export PDF",
                            sub = if (s.isThai) "สรุปให้คุณหมอ" else "Summary for the doctor",
                            route = Screen.PdfExport.route,
                            iconTint = Color(0xFFC62828),
                            iconBg = Color(0xFFFFEBEE)
                        )
                    ),
                    onNavigate = { navController.navigate(it) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
        }
    }


    // Delete-child confirmation
    childToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { childToDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = { Text(if (s.isThai) "ลบข้อมูลเด็ก?" else "Delete child?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "ต้องการลบ \"${target.name}\" หรือไม่? ข้อมูลทั้งหมด (วัคซีน พัฒนาการ สุขภาพ เอกสาร) จะถูกลบถาวรและไม่สามารถย้อนกลับได้" else "Permanently delete \"${target.name}\"? All their data (vaccines, milestones, health, documents) will be removed and cannot be undone.") },
            confirmButton = { TextButton(onClick = { childViewModel.deleteChild(target.id); childToDelete = null }) { Text(if (s.isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { childToDelete = null }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

/** Bottom-sheet child switcher (mockup: replaces the old dropdown). Keeps the same actions the
 *  dropdown had — select, delete, invite another parent, add child — just in sheet form. */
@Composable
private fun ChildSwitcherSheet(
    isThai: Boolean,
    children: List<com.lullabyai.app.data.model.Child>,
    activeChildId: String?,
    childAvatarSignedUrl: String?,
    ownedChildIds: Set<String>,
    onSelect: (com.lullabyai.app.data.model.Child) -> Unit,
    onDelete: (com.lullabyai.app.data.model.Child) -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 36.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isThai) "เลือกบัญชีลูก" else "Select child", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                children.forEach { c ->
                    val isActive = c.id == activeChildId
                    Card(
                        onClick = { onSelect(c) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(0.06f) else MaterialTheme.colorScheme.surface),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(0.25f)) else null
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                                if (isActive && !childAvatarSignedUrl.isNullOrBlank()) {
                                    AsyncImage(model = childAvatarSignedUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else Icon(Icons.Filled.ChildCare, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (isActive) (if (isThai) "กำลังใช้งาน" else "Active") else genderLabel(c.gender, isThai),
                                    style = MaterialTheme.typography.labelSmall, color = if (isActive) Primary else Outline, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (c.id in ownedChildIds) {
                                IconButton(onClick = { onDelete(c) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, if (isThai) "ลบเด็ก" else "Delete child", tint = Error, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (isActive) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "แก้ไขข้อมูลลูก" else "Edit child profile", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onAddChild,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "เพิ่มบัญชีลูก" else "Add child", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
internal fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CardHeaderInline(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, status: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (status != null) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (com.lullabyai.app.ui.theme.LocalDarkTheme.current) Color(0xFF81C784) else Color(0xFF2E7D32)
                        )
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun GrowthStatCard(
    label: String, value: String, status: String, modifier: Modifier = Modifier,
    statusColor: Color = Color(0xFF2E7D32), icon: ImageVector = Icons.Filled.MonitorWeight, onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(0.08f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Surface(shape = RoundedCornerShape(100.dp), color = statusColor.copy(0.1f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Only claim "normal" with a checkmark when the status color is actually the
                        // "normal range" green (see GrowthReference) — otherwise it's a warning, not a pass.
                        val isNormal = statusColor == Color(0xFF2E7D32)
                        Icon(if (isNormal) Icons.Filled.Check else Icons.Filled.PriorityHigh, null, tint = statusColor, modifier = Modifier.size(10.dp))
                        Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
            Column {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Child Hub pieces from the Stitch redesign.
// ---------------------------------------------------------------------------------------------

/**
 * Reduces whatever the child screens already loaded into the few dates the แนะนำ strip needs.
 *
 * Dates arrive in two shapes across these tables — full timestamps ("2026-09-12T08:00:00+07:00")
 * and plain days ("2026-09-12") — so both are accepted and an unparseable one is simply ignored
 * rather than pinning the signal to the epoch and inventing a nudge.
 */
private fun childHubSignals(
    state: com.lullabyai.app.viewmodel.ChildUiState,
    ageMonths: Int?
): com.lullabyai.app.data.ChildHubSuggestions.Signals {
    val zone = java.time.ZoneId.systemDefault()
    fun day(iso: String?): java.time.LocalDate? {
        val raw = iso?.trim().orEmpty()
        if (raw.isBlank()) return null
        runCatching { return java.time.OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDate() }
        return runCatching { java.time.LocalDate.parse(raw.take(10)) }.getOrNull()
    }
    return com.lullabyai.app.data.ChildHubSuggestions.Signals(
        today = java.time.LocalDate.now(zone),
        lastGrowthOn = state.growthRecords.mapNotNull { day(it.recordedAt) }.maxOrNull(),
        nextVaccineDueOn = state.vaccines
            .filter { it.status != "completed" && it.status != "skipped" }
            .mapNotNull { day(it.scheduledDate) }
            .minOrNull(),
        lastIllnessOn = state.healthRecords.mapNotNull { day(it.recordedAt) }.maxOrNull(),
        lastSleepOn = state.sleepLogs.mapNotNull { day(it.startTime) }.maxOrNull(),
        pendingMilestones = state.milestones.count { !it.isAchieved },
        ageMonths = ageMonths
    )
}

/** Where each nudge sends the parent. */
private fun suggestionRoute(kind: com.lullabyai.app.data.ChildHubSuggestions.Kind): String = when (kind) {
    com.lullabyai.app.data.ChildHubSuggestions.Kind.VACCINE_OVERDUE,
    com.lullabyai.app.data.ChildHubSuggestions.Kind.VACCINE_SOON -> Screen.VaccineBook.route
    com.lullabyai.app.data.ChildHubSuggestions.Kind.ILLNESS_FOLLOW_UP -> Screen.HealthLog.route
    com.lullabyai.app.data.ChildHubSuggestions.Kind.GROWTH_NEVER,
    com.lullabyai.app.data.ChildHubSuggestions.Kind.GROWTH_STALE -> Screen.AddGrowthRecord.route
    com.lullabyai.app.data.ChildHubSuggestions.Kind.MILESTONE -> Screen.ChildDevelopment.route
    com.lullabyai.app.data.ChildHubSuggestions.Kind.SLEEP_TODAY -> Screen.SleepTracker.route
}

/**
 * The design's แนะนำ strip, with the sentence chosen from this child's own records.
 */
@Composable
private fun ChildSuggestionCard(
    suggestion: com.lullabyai.app.data.ChildHubSuggestions.Suggestion,
    isThai: Boolean,
    onAct: () -> Unit
) {
    val n = suggestion.days
    val icon: ImageVector = when (suggestion.kind) {
        Kind.VACCINE_OVERDUE, Kind.VACCINE_SOON -> Icons.Filled.Vaccines
        Kind.ILLNESS_FOLLOW_UP -> Icons.Filled.MonitorHeart
        Kind.GROWTH_NEVER, Kind.GROWTH_STALE -> Icons.Filled.TrendingUp
        Kind.MILESTONE -> Icons.Filled.EmojiEvents
        Kind.SLEEP_TODAY -> Icons.Filled.Bedtime
    }
    val title = when (suggestion.kind) {
        Kind.VACCINE_OVERDUE ->
            if (isThai) "แนะนำ: วัคซีนเลยกำหนดมาแล้ว $n วัน" else "Tip: a vaccine is $n days overdue"
        Kind.VACCINE_SOON ->
            if (isThai) "แนะนำ: วัคซีนครบกำหนดในอีก $n วัน" else "Tip: a vaccine is due in $n days"
        Kind.ILLNESS_FOLLOW_UP ->
            if (isThai) "แนะนำ: ติดตามอาการของลูกวันนี้" else "Tip: follow up on today's symptoms"
        Kind.GROWTH_NEVER ->
            if (isThai) "แนะนำ: บันทึกน้ำหนักและส่วนสูงครั้งแรก" else "Tip: record the first weight & height"
        Kind.GROWTH_STALE ->
            if (isThai) "แนะนำ: อัปเดตน้ำหนักและส่วนสูงวันนี้" else "Tip: update weight & height today"
        Kind.MILESTONE ->
            if (isThai) "แนะนำ: เช็กพัฒนาการที่ยังไม่ผ่าน $n ข้อ" else "Tip: $n milestones still to tick"
        Kind.SLEEP_TODAY ->
            if (isThai) "แนะนำ: บันทึกการนอนของวันนี้" else "Tip: log today's sleep"
    }
    val detail = when (suggestion.kind) {
        Kind.VACCINE_OVERDUE ->
            if (isThai) "เปิดสมุดวัคซีนเพื่อนัดหมายให้เรียบร้อย" else "Open the vaccine book and book it"
        Kind.VACCINE_SOON ->
            if (isThai) "นัดล่วงหน้าไว้ จะได้ไม่พลาดเข็มนี้" else "Book ahead so this dose is not missed"
        Kind.ILLNESS_FOLLOW_UP ->
            if (isThai) "บันทึกอาการล่าสุดเมื่อ $n วันก่อน" else "Last symptom entry was $n days ago"
        Kind.GROWTH_NEVER ->
            if (isThai) "เพื่อเริ่มกราฟการเติบโตของลูก" else "To start this child's growth chart"
        Kind.GROWTH_STALE ->
            if (isThai) "ไม่ได้อัปเดตมา $n วันแล้ว" else "Nothing recorded for $n days"
        Kind.MILESTONE ->
            if (isThai) "ติ๊กเมื่อลูกทำได้ เพื่อให้คำแนะนำแม่นขึ้น" else "Tick them off for sharper advice"
        Kind.SLEEP_TODAY ->
            if (isThai) "ช่วยให้คำแนะนำการนอนแม่นยำขึ้น" else "It makes the sleep advice more accurate"
    }
    val action = when (suggestion.kind) {
        Kind.VACCINE_OVERDUE, Kind.VACCINE_SOON, Kind.MILESTONE ->
            if (isThai) "เปิดดู" else "Open"
        else -> if (isThai) "บันทึก" else "Record"
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onAct,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ChildHubSwitcherPill(
    isThai: Boolean,
    name: String,
    ageMonths: String,
    avatarUrl: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.tourAnchor("parent_child_switch")
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(PrimaryContainer.copy(0.35f)),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(16.dp))
                }
            }
            Column {
                Text(
                    if (isThai) "น้อง $name $ageMonths" else "$name · $ageMonths",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    if (isThai) "สลับโปรไฟล์ลูก" else "Switch child",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ChildHubHeroCard(
    isThai: Boolean,
    name: String,
    ageLabel: String,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    onMoodClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (com.lullabyai.app.ui.theme.LocalDarkTheme.current)
                MaterialTheme.colorScheme.primary.copy(0.16f)
            else Color(0xFFEAF2FF)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(32.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        (if (isThai) "อายุ " else "Age ") + ageLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.nongrak_mascot_greeting),
                    contentDescription = null,
                    modifier = Modifier.height(88.dp).width(72.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Surface(
                onClick = onMoodClick,
                shape = RoundedCornerShape(100.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (isThai) "วันนี้ น้อง $name สบายดีไหม?" else "How is $name today?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("💙", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** "สมุดสุขภาพและบัตรดิจิทัล" row — the official-records entry from the design. */
@Composable
private fun ChildHealthBookCard(isThai: Boolean, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFCE4EC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Description, null, tint = Color(0xFFC2185B), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isThai) "สมุดสุขภาพและบัตรดิจิทัล" else "Health book & digital card",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (isThai) "วัคซีน, พัฒนาการ & ประวัติพบแพทย์" else "Vaccines, development & doctor visits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * "ประวัติกิจกรรม & ส่งต่อการดูแล" — the card body opens the full history; the two buttons are the
 * design's quick record and handover actions. A caregiver's quick record is their own update form.
 */
@Composable
private fun ActivityHandoverCard(
    isThai: Boolean,
    amCaregiver: Boolean,
    onHistory: () -> Unit,
    onQuickRecord: () -> Unit,
    onHandover: () -> Unit
) {
    Card(
        onClick = onHistory,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(48.dp).background(Color.White.copy(0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isThai) "ประวัติกิจกรรม & ส่งต่อการดูแล" else "Activity history & handover",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        if (isThai) "ดูบันทึกเชิงลึกและส่งต่อให้ผู้ดูแล"
                        else "See every record and brief the caregiver",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.85f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onQuickRecord,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1A237E)
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        if (isThai) {
                            if (amCaregiver) "อัปเดตข้อมูลลูก" else "บันทึกกิจกรรม"
                        } else {
                            if (amCaregiver) "Child update" else "Log activity"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onHandover,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1A237E)
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        if (isThai) "ส่งต่อการดูแล" else "Handover",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

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
import androidx.core.content.FileProvider
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.NoChildDataCard
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

/** One tile in a Child-info Bento action group. */
data class ChildTile(
    val icon: ImageVector,
    val label: String,
    val route: String,
    val iconTint: Color = Primary,
    val iconBg: Color = PrimaryContainer.copy(0.45f),
    val badge: String? = null
)

/**
 * Modern Bento-grid action group with responsive 2-column layout and vibrant icon containers.
 */
@Composable
fun ChildActionGroup(
    title: String,
    tiles: List<ChildTile>,
    onNavigate: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { t ->
                    Card(
                        onClick = { onNavigate(t.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(t.iconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(t.icon, null, tint = t.iconTint, modifier = Modifier.size(20.dp))
                                }
                                if (t.badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = t.iconTint.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = t.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = t.iconTint,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = t.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // An odd tile count keeps its half-width instead of stretching across the row.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
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
    ChildScreenScaffold(title = if (s.isThai) "ข้อมูลของเด็ก" else "Child Info", currentTab = "overview", navController = navController, showBack = false) { padding ->
        if (childState.isLoading && childState.children.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
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
            LaunchedEffect(Unit) { careRoleVm.load() }
            val amCaregiver by careRoleVm.amCaregiver.collectAsState()

            Card(
                onClick = {
                    navController.navigate(
                        if (amCaregiver) Screen.CaregiverUpdate.route else Screen.ActivityHistory.route
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Primary),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    Modifier.padding(18.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Color.White.copy(0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (amCaregiver) Icons.Filled.EditNote else Icons.Filled.Timeline,
                            null, tint = Color.White, modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                amCaregiver && s.isThai -> "อัปเดตข้อมูลลูก"
                                amCaregiver -> "Child update"
                                s.isThai -> "ประวัติกิจกรรม"
                                else -> "Activity history"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            when {
                                amCaregiver && s.isThai -> "บันทึกกิจกรรมประจำวันและรายงานให้ผู้ปกครอง"
                                amCaregiver -> "Log the day's care and report it to the parents"
                                s.isThai -> "ดูทุกบันทึกของลูก พร้อมชื่อผู้บันทึก"
                                else -> "Every record for this child, with who wrote it"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(0.85f)
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(0.7f))
                }
            }

            if (!tourPreview && child != null) {
                com.lullabyai.app.ui.screens.access.HandoffHomeCard(child.id,s.isThai,
                    onOpen = { navController.navigate(Screen.CareHandoff.route(child.id)) })
            }
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

            // Profile card (row)
            Box(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                        val avatar = childState.childAvatarSignedUrl
                        if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(36.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // Name + switch-child trigger ("สลับบัญชี") — opens the bottom sheet below.
                        var showSwitcherSheet by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showSwitcherSheet = true }
                                .tourAnchor("parent_child_switch")
                        ) {
                            Text(
                                if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.displayName(s.isThai)
                                else child?.name ?: (if (s.isThai) "ลูกของคุณ" else "Your child"),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (s.isThai) "สลับบัญชี" else "Switch", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Filled.ArrowDropDown, null, tint = Primary, modifier = Modifier.size(16.dp))
                                }
                            }
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
                                onDismiss = { showSwitcherSheet = false }
                            )
                        }

                        Text(
                            (if (s.isThai) "อายุ " else "Age ") +
                                (if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.ageLabel(s.isThai) else childViewModel.calculateAge(s.isThai)) +
                                " (" + genderLabel(child?.gender, s.isThai) + ")",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text((if (s.isThai) "วันเกิด " else "Born ") + formatDate(child?.birthdate, s.isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
                // "ดูสมุดบันทึกสุขภาพ" (View Health Record Book) CTA -> Interactive Health Book screen
                Button(
                    onClick = { navController.navigate(Screen.HealthBook.route) },
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.MenuBook, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "ดูสมุดบันทึกสุขภาพ" else "View Health Record Book", fontWeight = FontWeight.Bold)
                }
              }
            }
            // Floating edit button (mockup: absolute top-right over the card)
            IconButton(
                onClick = { navController.navigate(Screen.EditChild.route) },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(Icons.Filled.Edit, null, tint = Outline, modifier = Modifier.size(20.dp))
            }
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
                val ageMonths = if (tourPreview) com.lullabyai.app.ui.screens.onboarding.TourSampleData.ageMonths() else childViewModel.getAgeMonths()
                val weightStatus = com.lullabyai.app.util.GrowthReference.weightStatus(child?.weightKg, ageMonths)
                val heightStatus = com.lullabyai.app.util.GrowthReference.heightStatus(child?.heightCm, ageMonths)
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

            // Unique standout AI Guidance ("คำแนะนำ AI") button linking to full AI Guidance & Daily Brief
            Card(
                onClick = { navController.navigate(AI_GUIDANCE_ROUTE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF8E24AA).copy(alpha = 0.5f),
                            Color(0xFF673AB7).copy(alpha = 0.6f),
                            Color(0xFF3949AB).copy(alpha = 0.5f)
                        )
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF5E35B1),
                                    Color(0xFF4527A0),
                                    Color(0xFF283593)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    if (s.isThai) "คำแนะนำ AI" else "AI Guidance",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xFFFFD54F).copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        if (s.isThai) "✨ เจาะลึกรายวัน" else "✨ Daily Focus",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFFE082),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                if (s.isThai) "คำแนะนำพัฒนาการและกิจวัตรประจำวันเฉพาะสำหรับลูกน้อย"
                                else "Personalized developmental & daily guidance for your baby",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
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
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text(if (s.isThai) "การวิเคราะห์จาก AI" else "AI Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.08f)) {
                            Text(if (s.isThai) "เรียลไทม์" else "Real-time", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                                CircularProgressIndicator(progress = { devPercent / 100f }, modifier = Modifier.fillMaxSize(), color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 7.dp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$devPercent%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                                    Text(if (s.isThai) "คะแนน" else "Score", style = MaterialTheme.typography.labelSmall, color = Outline, fontSize = 8.sp)
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
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (childState.allergies.isNotEmpty()) Error.copy(alpha = 0.35f)
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
                        if (childState.chronicConditions.isNotEmpty()) Primary.copy(alpha = 0.35f)
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
                                        if (childState.chronicConditions.isNotEmpty()) Primary.copy(alpha = 0.12f)
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
                                        color = Primary.copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
                                    ) {
                                        Text(
                                            cc.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Primary,
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
                                        color = Primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recommended Action Banner (Stitch Step 3.2: แนะนำอัปเดตน้ำหนักและส่วนสูง)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.TrendingUp,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (s.isThai) "แนะนำ: อัปเดตน้ำหนักและส่วนสูงวันนี้" else "Tip: Update weight & height today",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (s.isThai) "เพื่อติดตามพัฒนาการที่แม่นยำ" else "For precise growth tracking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { navController.navigate(Screen.AddGrowthRecord.route) },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (s.isThai) "บันทึก" else "Record", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Primary Quick Actions (เมนูหลัก) Matching Stitch
            Column(
                Modifier.fillMaxWidth().tourAnchor("parent_child_actions"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (s.isThai) "เมนูหลัก" else "Main Menu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Hero Full-Width Diary Card
                Card(
                    onClick = { navController.navigate(Screen.ChildDiary.route) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoStories,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (s.isThai) "ไดอารี่" else "Diary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                if (s.isThai) "บันทึกเรื่องราวประจำวัน & โมเมนต์แสนพิเศษ" else "Daily journal & special moments",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                // 2-Column Grid Actions
                val isGuestUser = childViewModel.isGuest()
                ChildActionGroup(
                    title = "",
                    tiles = listOf(
                        ChildTile(
                            icon = Icons.Filled.EventRepeat,
                            label = if (s.isThai) "กิจกรรม" else "Activities",
                            route = Screen.ActivitySchedule.route,
                            iconTint = Color(0xFF0288D1),
                            iconBg = Color(0xFFE1F5FE)
                        ),
                        ChildTile(
                            icon = Icons.Filled.Chat,
                            label = if (s.isThai) "แชทกับผู้ดูแล" else "Care chat",
                            route = Screen.CareCircle.route,
                            iconTint = Color(0xFF1565C0),
                            iconBg = Color(0xFFE3F2FD)
                        ),
                        ChildTile(
                            icon = Icons.Filled.MedicalServices,
                            label = if (s.isThai) "ศูนย์สุขภาพ" else "Health Hub",
                            route = Screen.ChildHealth.route,
                            iconTint = Color(0xFF00897B),
                            iconBg = Color(0xFFE0F2F1)
                        ),
                        ChildTile(
                            icon = Icons.Filled.PictureAsPdf,
                            label = if (s.isThai) "ส่งออก PDF" else "Export PDF",
                            route = Screen.PdfExport.route,
                            iconTint = Color(0xFFC62828),
                            iconBg = Color(0xFFFFEBEE)
                        ),
                        ChildTile(
                            icon = Icons.Filled.LockPerson,
                            label = if (s.isThai) "สิทธิ์การเข้าถึง" else "Access & roles",
                            route = Screen.PermissionAccess.route,
                            iconTint = Color(0xFF5E35B1),
                            iconBg = Color(0xFFEDE7F6)
                        ),
                        ChildTile(
                            icon = Icons.Filled.Bolt,
                            label = if (s.isThai) "บันทึกด่วน" else "Quick log",
                            route = Screen.QuickLogHub.route,
                            iconTint = Color(0xFF7B1FA2),
                            iconBg = Color(0xFFF3E5F5)
                        )
                    ),
                    onNavigate = { navController.navigate(it) }
                )

                Spacer(Modifier.height(4.dp))

                // More Utilities (เครื่องมืออื่นๆ)
                ChildActionGroup(
                    title = if (s.isThai) "เครื่องมืออื่นๆ" else "More Tools",
                    tiles = listOf(
                        ChildTile(
                            icon = Icons.Filled.AddCircle,
                            label = if (s.isThai) "บันทึกสุขภาพ" else "Health log",
                            route = Screen.HealthLog.route,
                            iconTint = Color(0xFF00897B),
                            iconBg = Color(0xFFE0F2F1)
                        ),
                        ChildTile(
                            icon = Icons.Filled.Analytics,
                            label = if (s.isThai) "ดูสถิติ" else "Stats",
                            route = Screen.StatisticsInsights.route,
                            iconTint = Color(0xFF3949AB),
                            iconBg = Color(0xFFE8EAF6)
                        ),
                        ChildTile(
                            icon = Icons.Filled.Payments,
                            label = if (s.isThai) "ค่าใช้จ่าย" else "Expenses",
                            route = Screen.Expenses.route,
                            iconTint = Color(0xFF2E7D32),
                            iconBg = Color(0xFFE8F5E9)
                        ),
                        ChildTile(
                            icon = Icons.Filled.FolderOpen,
                            label = if (s.isThai) "คลังเอกสาร" else "Document vault",
                            route = Screen.MedicalDataVault.route,
                            iconTint = Color(0xFF0097A7),
                            iconBg = Color(0xFFE0F7FA)
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
                        colors = CardDefaults.cardColors(containerColor = if (isActive) Primary.copy(0.06f) else MaterialTheme.colorScheme.surface),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Primary.copy(0.25f)) else null
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                                if (isActive && !childAvatarSignedUrl.isNullOrBlank()) {
                                    AsyncImage(model = childAvatarSignedUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(26.dp))
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
                            if (isActive) Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                OutlinedButton(
                    onClick = onAddChild,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Filled.Add, null, tint = Primary)
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
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CardHeaderInline(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
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
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (status != null) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Text(status, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
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
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.08f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
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
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Outline, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.lullabyai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.lullabyai.app.ui.screens.onboarding.tourAnchor
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * App-wide signed avatar URL for the current user, provided once near the NavHost root
 * (see AppNavigation.kt) so any header can show the real profile photo without threading
 * it through every screen's parameter list — mirrors the existing LocalStrings pattern.
 */
val LocalAvatarUrl = staticCompositionLocalOf<String?> { null }

/**
 * The app's NavController, provided once near the NavHost root (see AppNavigation.kt).
 *
 * ponytail: exists so tab-switching lives in ONE place instead of being re-implemented in the
 * 56 screens that render the footer. Those screens still pass their onHome/onChild/... lambdas
 * and still compile, but when this local is present [AppBottomNav] ignores them and navigates
 * itself — the only way to get per-tab back-stack save/restore right without editing every call
 * site. Upgrade path: delete the now-dead lambdas from the call sites in a separate cleanup pass.
 */
val LocalNavController = staticCompositionLocalOf<NavController?> { null }

/**
 * Number of unread *important* notifications (category == "warning"), provided at the NavHost
 * root so every header's bell can show a red count badge without each screen loading them.
 */
val LocalImportantNotificationCount = staticCompositionLocalOf { 0 }

/** Root destination of each footer tab. Re-tapping a tab returns here; switching tabs restores its saved stack. */
private fun tabRootRoute(tab: BottomTab): String = when (tab) {
    BottomTab.HOME -> Screen.Dashboard.route
    BottomTab.CHILD -> Screen.ChildSummary.route
    BottomTab.CHATBOT -> Screen.AiChat.route
    BottomTab.CONNECT -> Screen.ConnectServicesSelection.route
    BottomTab.COMMUNITY -> Screen.Community.route
    BottomTab.SETTINGS -> Screen.Settings.route
    // Stats belongs to the Child tab now, so its root is the Child root.
    BottomTab.STATS -> Screen.ChildSummary.route
}

/**
 * Switch footer tabs, preserving each tab's own back stack.
 *
 * Anchors on Dashboard rather than the graph's start destination because the graph starts at
 * Welcome/Login — popping to that would throw the user out of the signed-in area.
 */
private fun NavController.switchToTab(tab: BottomTab, isReselect: Boolean) {
    if (tab == BottomTab.HOME) {
        navigate(Screen.Dashboard.route) {
            popUpTo(Screen.Dashboard.route) {
                inclusive = false
            }
            launchSingleTop = true
        }
        return
    }
    navigate(tabRootRoute(tab)) {
        popUpTo(Screen.Dashboard.route) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        // Re-tapping the tab you're already in is an explicit "take me to the top of this
        // section", so we deliberately do NOT restore that tab's saved inner stack.
        restoreState = !isReselect
    }
}

/**
 * Throw away every parked per-tab back stack. Call on sign-out.
 *
 * `saveState = true` above parks the tab's inner stack inside the NavController, keyed by
 * destination id. `popUpTo(0)` at logout empties the *live* stack but does not touch those parked
 * copies — androidx only drops them on restore or on [NavController.clearBackStack] — and the
 * NavController outlives the session. So the next account to sign in would land straight back on
 * the previous account's page the first time it tapped that tab.
 */
fun NavController.clearSavedTabStacks() {
    val roots = BottomTab.entries.map(::tabRootRoute) + listOf(
        Screen.DoctorDashboard.route,
        Screen.DoctorPatientManagement.route,
        Screen.DoctorSettings.route
    )
    // A route with nothing parked under it is a no-op, but this runs mid-sign-out: a nav-internal
    // failure must not be what stops someone from logging out.
    roots.distinct().forEach { runCatching { clearBackStack(it) } }
}

/** The tabs of the app-wide bottom navigation. CHATBOT is launched from the center floating button, not a bar item. */
enum class BottomTab { HOME, CHATBOT, CHILD, STATS, CONNECT, COMMUNITY, SETTINGS }

/** Small pill button showing the language you'd switch TO (e.g. shows "EN" while on Thai) — used
 *  on the pre-login screens (Welcome/Login/SignUp) where there's no Settings screen to reach yet. */
@Composable
fun LanguageToggleButton(isThai: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onToggle, shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Language, null, tint = Primary, modifier = Modifier.size(16.dp))
            Text(if (isThai) "EN" else "ไทย", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
        }
    }
}

/** Large Home / Child Info card for the open info-ask form. Tap or CTA opens [Screen.InfoAsk]. */
@Composable
fun InfoAskBanner(
    isThai: Boolean,
    subjectName: String,
    questionCount: Int,
    preview: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.HelpOutline, null, tint = Primary, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                    Text(
                        if (isThai) "ขอข้อมูลเพิ่มเติม" else "Asking more information",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (questionCount <= 1) {
                            if (isThai) "เกี่ยวกับ $subjectName" else "on $subjectName"
                        } else {
                            if (isThai) "$questionCount คำถามเกี่ยวกับ $subjectName"
                            else "$questionCount questions on $subjectName"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onClick)
            )
            Text(
                if (isThai) "กรอกเพิ่มเพื่อให้วิเคราะห์แม่นขึ้น — ช่องที่ตรงกับสมุดสุขภาพน้องรักจะถูกเขียนลงสมุดด้วย"
                else "Fill more so analysis is better. Fields that match the NongRak Health Book are written there too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onClick)
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(
                    if (questionCount > 1) {
                        if (isThai) "ตอบทั้งหมด" else "Answer all"
                    } else {
                        if (isThai) "ตอบเลย" else "Answer now"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Shared app footer used on every primary screen (Dashboard, AI Chat, Child pages, Settings).
 *
 * Callback-based so it works whether the caller navigates via a real NavController or via
 * onNavigate* lambdas. The [selected] tab is highlighted; tapping any other tab fires its callback.
 *
 * Bar tabs: หน้าหลัก (Home) • ข้อมูลลูก (Child) • สถิติ (Stats) • การตั้งค่า (Settings)
 * Center floating button: AI Chat (always visible, overlaps the bar).
 */
@Composable
fun AppBottomNav(
    selected: BottomTab,
    onHome: () -> Unit,
    onChatbot: () -> Unit,
    onChild: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    // Only child-family screens (via ChildBottomNav) pass this — when present it replaces the Stats
    // slot with Connect, since those screens already offer Stats via the dashboard's button grid.
    onConnect: (() -> Unit)? = null,
    onCommunity: (() -> Unit)? = null
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val nav = LocalNavController.current

    // 4 standard footer tabs + center floating AI chatbot
    data class NavItem(val tab: BottomTab, val icon: ImageVector, val label: String, val fallback: () -> Unit)
    val leftItems = listOf(
        NavItem(BottomTab.HOME, Icons.Filled.Home, if (s.isThai) "หน้าแรก" else s.navHome, onHome),
        NavItem(BottomTab.CHILD, Icons.Filled.ChildCare, if (s.isThai) "ข้อมูลลูก" else s.navChild, onChild)
    )
    val rightItems = listOf(
        NavItem(BottomTab.COMMUNITY, Icons.Filled.Groups, if (s.isThai) "ชุมชน" else "Community", onCommunity ?: { nav?.navigate(Screen.Community.route); Unit }),
        NavItem(BottomTab.SETTINGS, Icons.Filled.Settings, if (s.isThai) "ตั้งค่า" else s.navSettings, onSettings)
    )

    // With a NavController in scope we own the tab transition (save/restore per tab, and
    // re-tap = back to that tab's root). Without one — previews, tests — fall back to the
    // caller's lambda so nothing breaks.
    fun go(item: NavItem) {
        val n = nav
        if (n != null) n.switchToTab(item.tab, isReselect = item.tab == selected)
        else if (item.tab != selected) item.fallback()
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val isDark = com.lullabyai.app.ui.theme.LocalDarkTheme.current
        val barBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White

        NavigationBar(
            containerColor = barBg,
            tonalElevation = 0.dp,
            // navigationBarsPadding lifts the footer above the system gesture bar for proper margin;
            // windowInsets(0) prevents double-padding from the bar's own default insets.
            windowInsets = WindowInsets(0),
            modifier = Modifier.navigationBarsPadding()
        ) {
            leftItems.forEach { item -> BottomNavBarItem(item.tab, selected, item.icon, item.label) { go(item) } }
            // Reserves the center slot so the floating AI button has room to sit above the bar.
            NavigationBarItem(
                selected = false,
                onClick = {
                    val n = nav
                    if (n != null) n.switchToTab(BottomTab.CHATBOT, isReselect = selected == BottomTab.CHATBOT)
                    else onChatbot()
                },
                icon = { Spacer(Modifier.size(24.dp)) },
                label = { Spacer(Modifier.height(12.dp)) },
                enabled = false,
                colors = NavigationBarItemDefaults.colors(disabledIconColor = Color.Transparent, disabledTextColor = Color.Transparent)
            )
            rightItems.forEach { item -> BottomNavBarItem(item.tab, selected, item.icon, item.label) { go(item) } }
        }
        FloatingActionButton(
            onClick = {
                val n = nav
                if (n != null) n.switchToTab(BottomTab.CHATBOT, isReselect = selected == BottomTab.CHATBOT)
                else onChatbot()
            },
            shape = CircleShape,
            containerColor = Primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            modifier = Modifier
                .tourAnchor("nav_ai_fab")
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
                .border(2.5.dp, barBg, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF173DB8)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.lullabyai.app.R.drawable.ic_nongrak_chatbot_footer),
                    contentDescription = s.navChatbot,
                    modifier = Modifier.size(32.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomNavBarItem(tab: BottomTab, selected: BottomTab, icon: ImageVector, label: String, onClick: () -> Unit) {
    val isSelected = tab == selected
    NavigationBarItem(
        selected = isSelected,
        modifier = Modifier.tourAnchor("nav_" + tab.name),
        // Fires even when already selected — re-tapping the active tab is how the user gets
        // back to that section's main page (#6).
        onClick = onClick,
        icon = { Icon(icon, label, modifier = Modifier.size(24.dp)) },
        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Primary,
            selectedTextColor = Primary,
            unselectedIconColor = Outline,
            unselectedTextColor = Outline,
            indicatorColor = Color.Transparent
        )
    )
}

@Composable
fun ChildSidebarNav(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    val tabs = listOf(
        Triple("overview", Icons.Filled.GridView, "ภาพรวม"),
        Triple("development", Icons.Filled.TrendingUp, "พัฒนาการ"),
        Triple("health", Icons.Filled.FavoriteBorder, "สุขภาพ"),
        Triple("vaccine", Icons.Filled.Vaccines, "สมุดวัคซีน"),
        Triple("chronic", Icons.Filled.HealthAndSafety, "โรคประจำตัว")
    )
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text("AI", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        tabs.forEach { (key, icon, label) ->
            val selected = currentTab == key
            Surface(
                onClick = { onTabSelected(key) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Primary.copy(alpha = 0.1f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected) Primary else Outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Stitch-style AI Insight card — gradient surface, primary border, rounded icon tile,
 * soft decorative orb. Used wherever designs show an "AI Analysis / AI Insight" panel.
 * [title] defaults to localized [Strings.aiAnalysis]; [emptyMessage] shows when not loading
 * and [text] is blank (e.g. not enough log data yet).
 */
@Composable
fun AIInsightCard(
    text: String,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    title: String? = null,
    emptyMessage: String? = null,
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val heading = title ?: s.aiAnalysis
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.20f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            PrimaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 28.dp, y = (-28).dp)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.06f))
            )
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleSmall,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    when {
                        isLoading -> LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Primary
                        )
                        text.isNotBlank() -> Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        !emptyMessage.isNullOrBlank() -> Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders an AI-generated [text] once it arrives; while null, shows a spinner for up to
 * [timeoutMs], then swaps to [timeoutMessage] instead of spinning forever. Several screens
 * (Disease Risk, Child Health, Sleep Tracker) loaded AI insights with a bare "if null, spin" —
 * if the underlying call ever hung or failed silently without setting state, the spinner never
 * resolved and a parent had no way to know something went wrong.
 */
@Composable
fun AiTextOrTimeout(
    text: String?,
    timeoutMessage: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null,
    timeoutMs: Long = 12_000L
) {
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        timedOut = false
        if (text == null) {
            delay(timeoutMs)
            timedOut = true
        }
    }
    when {
        text != null -> Text(text, modifier = modifier, style = style, color = color, textAlign = textAlign)
        timedOut -> Text(timeoutMessage, modifier = modifier, style = style, color = Error, textAlign = textAlign)
        else -> LinearProgressIndicator(modifier = modifier.fillMaxWidth(), color = Primary)
    }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val (color, text) = when (status.lowercase()) {
        "completed", "normal" -> Pair(Color(0xFF4CAF50), if (status == "completed") "เสร็จแล้ว" else "NORMAL")
        "scheduled" -> Pair(Primary, "นัดหมาย")
        "overdue" -> Pair(Error, "เลยกำหนด")
        "needs attention", "attention" -> Pair(Color(0xFFFF5722), "NEEDS ATTENTION")
        "low risk" -> Pair(Color(0xFF4CAF50), "LOW RISK")
        else -> Pair(Outline, status)
    }
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The single shared header for the whole app. [navController] drives the standard notification
 * bell (-> Screen.Notifications) and profile photo (-> Screen.Settings) actions — omit it (or set
 * [showNotification]/[showProfile] to false) for screens that intentionally skip them, e.g. modal
 * Save/Edit forms that already occupy the actions slot with their own primary action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LullabyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    navController: NavController? = null,
    centerTitle: Boolean = false,
    showNotification: Boolean = navController != null,
    showProfile: Boolean = navController != null,
    /**
     * Send the bell and avatar to the DOCTOR notifications/settings instead of the parent ones.
     *
     * This is why doctor screens used to suppress both icons entirely: the shared bar hardcoded
     * `Screen.Notifications` / `Screen.Settings`, so tapping either dropped a doctor onto a parent
     * screen with a parent footer and no way back. Routing by role lets doctor pages carry the
     * standard back — title — bell — avatar header like everything else.
     */
    isDoctor: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val avatarUrl = LocalAvatarUrl.current
    val titleSlot: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
    }
    val navSlot: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "ย้อนกลับ", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    val actionsSlot: @Composable RowScope.() -> Unit = {
        actions()
        if (showNotification && navController != null) {
            val importantCount = LocalImportantNotificationCount.current
            IconButton(onClick = {
                navController.navigate(
                    if (isDoctor) Screen.DoctorNotifications.route else Screen.Notifications.route
                )
            }) {
                // Bigger bell, and it turns red the moment there is anything important waiting,
                // with a count badge so several important notices are visible at a glance (#8).
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "การแจ้งเตือน",
                        tint = if (importantCount > 0) Error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp)
                    )
                    if (importantCount > 0) {
                        Box(
                            modifier = Modifier
                                .offset(x = 6.dp, y = (-4).dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Error),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (importantCount > 9) "9+" else importantCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        if (showProfile && navController != null) {
            IconButton(onClick = {
                navController.navigate(
                    if (isDoctor) Screen.DoctorSettings.route else Screen.Settings.route
                )
            }) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl, contentDescription = "โปรไฟล์",
                            modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = "โปรไฟล์", tint = Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    val barColors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    if (centerTitle) {
        CenterAlignedTopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, colors = barColors)
    } else {
        TopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, colors = barColors)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        shape = RoundedCornerShape(100.dp),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

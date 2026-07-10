package com.lullabyai.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import kotlinx.coroutines.delay

/** The tabs of the app-wide bottom navigation. CHATBOT is launched from the center floating button, not a bar item. */
enum class BottomTab { HOME, CHATBOT, CHILD, STATS, SETTINGS }

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

/** Dismissible banner nudging the user to answer the AI's daily question — tapping it opens the
 *  actual question dialog rather than popping the dialog up unprompted. Shared between Dashboard
 *  and Child Info so the same question/state (ChildViewModel.uiState.dailyQuestion) surfaces on both. */
@Composable
fun DailyQuestionBanner(isThai: Boolean, onClick: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = Primary.copy(0.08f)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.HelpOutline, null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(
                if (isThai) "ตอบคำถามเพื่อช่วยการวิเคราะห์" else "Answer a question to help analysis",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.Close, null, tint = Primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The daily-question dialog itself — shared between Dashboard and Child Info so both trigger the
 *  same answer/skip flow via [DailyQuestionBanner] instead of duplicating this markup. */
@Composable
fun DailyQuestionDialog(question: String, isThai: Boolean, onAnswer: (String) -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit) {
    var dailyAnswer by remember(question) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.HelpOutline, null, tint = Primary) },
        title = { Text(question) },
        text = {
            Column {
                Text(
                    if (isThai) "คำถามจาก AI เพื่อช่วยวิเคราะห์ลูกของคุณได้แม่นยำขึ้น (สูงสุด 3 คำถาม/วัน)"
                    else "A question from the AI to analyze your child more accurately (max 3/day)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = dailyAnswer, onValueChange = { dailyAnswer = it },
                    label = { Text(if (isThai) "คำตอบ" else "Answer") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(dailyAnswer) }, enabled = dailyAnswer.isNotBlank()) {
                Text(if (isThai) "ส่ง" else "Submit", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(if (isThai) "ข้าม" else "Skip") }
        }
    )
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
    onSettings: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    data class NavItem(val tab: BottomTab, val icon: ImageVector, val label: String, val onClick: () -> Unit)
    val leftItems = listOf(
        NavItem(BottomTab.HOME, Icons.Filled.Home, s.navHome, onHome),
        NavItem(BottomTab.CHILD, Icons.Filled.ChildCare, s.navChild, onChild)
    )
    val rightItems = listOf(
        NavItem(BottomTab.STATS, Icons.Filled.BarChart, if (s.isThai) "สถิติ" else "Stats", onStats),
        NavItem(BottomTab.SETTINGS, Icons.Filled.Settings, s.navSettings, onSettings)
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            // navigationBarsPadding lifts the footer above the system gesture bar for proper margin;
            // windowInsets(0) prevents double-padding from the bar's own default insets.
            windowInsets = WindowInsets(0),
            modifier = Modifier.navigationBarsPadding()
        ) {
            leftItems.forEach { item -> BottomNavBarItem(item.tab, selected, item.icon, item.label, item.onClick) }
            // Reserves the center slot so the floating AI button has room to sit above the bar.
            NavigationBarItem(
                selected = false,
                onClick = onChatbot,
                icon = { Spacer(Modifier.size(24.dp)) },
                label = { Spacer(Modifier.height(12.dp)) },
                enabled = false,
                colors = NavigationBarItemDefaults.colors(disabledIconColor = Color.Transparent, disabledTextColor = Color.Transparent)
            )
            rightItems.forEach { item -> BottomNavBarItem(item.tab, selected, item.icon, item.label, item.onClick) }
        }
        FloatingActionButton(
            onClick = onChatbot,
            shape = CircleShape,
            containerColor = Primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
        ) {
            Icon(Icons.Filled.SmartToy, s.navChatbot, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun RowScope.BottomNavBarItem(tab: BottomTab, selected: BottomTab, icon: ImageVector, label: String, onClick: () -> Unit) {
    val isSelected = tab == selected
    NavigationBarItem(
        selected = isSelected,
        onClick = { if (!isSelected) onClick() },
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
        Triple("risk", Icons.Filled.Warning, "ความเสี่ยงโรค")
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

@Composable
fun AIInsightCard(
    text: String,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Primary, PrimaryContainer))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.aiAnalysis,
                    style = MaterialTheme.typography.titleSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                } else {
                    // Larger, generously spaced text for easy reading (parent-facing AI advice)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LullabyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "ย้อนกลับ", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
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
            .height(56.dp),
        shape = RoundedCornerShape(100.dp),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 16.sp)
        }
    }
}

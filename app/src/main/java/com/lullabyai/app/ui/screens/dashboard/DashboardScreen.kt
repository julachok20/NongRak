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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
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
    onNavigateToStats: () -> Unit = {}
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()

    val child = childState.child
    val username = authState.profile?.username?.takeIf { it.isNotBlank() }
        ?: authState.profile?.fullName?.takeIf { it.isNotBlank() }

    LaunchedEffect(Unit) { childViewModel.loadChild(); authViewModel.loadProfile() }

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
                onHome = {}, onChatbot = onNavigateToChat, onChild = onNavigateToChild, onStats = onNavigateToStats, onSettings = onNavigateToSettings
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
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
                            if (username != null) (if (isThai) "สวัสดีค่ะ คุณ$username" else "Hello, $username") else (if (isThai) "สวัสดีค่ะ คุณแม่" else "Hello, Mom"),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold
                        )
                    }
                }
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

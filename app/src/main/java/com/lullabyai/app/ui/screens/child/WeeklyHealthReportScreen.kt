@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun WeeklyHealthReportScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child

    LaunchedEffect(child) {
        child?.let { chatViewModel.loadWeeklyHealthReport(it, s.isThai) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "รายงานสุขภาพเชิงลึก" else "In-depth Health Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Header card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(if (s.isThai) "รายงานโดย AI" else "AI REPORT", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(if (s.isThai) "รายงานสุขภาพรายสัปดาห์" else "Weekly Health Report", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Full narrative
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (chatState.weeklyHealthReport != null) {
                        Text(chatState.weeklyHealthReport!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                    }
                }
            }

            // Factors considered — real data only, no fabricated history/trend
            child?.let {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (s.isThai) "ปัจจัยที่ AI ใช้พิจารณา" else "Factors AI considered", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.padding(4.dp)) {
                            val rows = buildList {
                                add((if (s.isThai) "อายุ" else "Age") to childViewModel.calculateAge(s.isThai))
                                it.weightKg?.let { w -> add((if (s.isThai) "น้ำหนัก" else "Weight") to "$w กก.") }
                                it.heightCm?.let { h -> add((if (s.isThai) "ส่วนสูง" else "Height") to "$h ซม.") }
                                it.sleepHours?.let { sh -> add((if (s.isThai) "นอนหลับเฉลี่ย" else "Avg sleep") to "${sh.toInt()} ${if (s.isThai) "ชม./วัน" else "hr/day"}") }
                                add((if (s.isThai) "ระดับกิจกรรม" else "Activity level") to when (it.activityLevel) {
                                    "low" -> if (s.isThai) "น้อย" else "Low"
                                    "high" -> if (s.isThai) "มาก" else "High"
                                    else -> if (s.isThai) "ปานกลาง" else "Moderate"
                                })
                                it.allergies?.takeIf { a -> a.isNotBlank() }?.let { a -> add((if (s.isThai) "ประวัติแพ้" else "Allergy history") to a) }
                            }
                            rows.forEachIndexed { i, (label, value) ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }
            }

            Text(
                if (s.isThai) "รายงานนี้จัดทำจากข้อมูลที่ผู้ปกครองบันทึกไว้ในแอปเท่านั้น ไม่ใช่การวินิจฉัยทางการแพทย์ กรุณาปรึกษาแพทย์หากมีข้อกังวล"
                else "This report is generated only from data recorded in the app by the parent — it is not a medical diagnosis. Please consult a doctor if you have concerns.",
                style = MaterialTheme.typography.labelSmall, color = Outline
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

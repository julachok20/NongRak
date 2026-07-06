@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

private data class WakeWindow(val ageRange: String, val ageRangeEn: String, val window: String, val windowEn: String)
private val WAKE_WINDOWS = listOf(
    WakeWindow("0-2 เดือน", "0-2 months", "45-60 นาที", "45-60 min"),
    WakeWindow("3-4 เดือน", "3-4 months", "1-1.5 ชม.", "1-1.5 hr"),
    WakeWindow("5-7 เดือน", "5-7 months", "2-3 ชม.", "2-3 hr"),
    WakeWindow("8-12 เดือน", "8-12 months", "3-4 ชม.", "3-4 hr")
)

@Composable
fun SleepGuidanceScreen(navController: NavController, onBack: () -> Unit, onCreateSchedule: () -> Unit = {}) {
    val s = LocalStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "คำแนะนำการนอน" else "Sleep Guidance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Thermostat, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(if (s.isThai) "อุณหภูมิที่เหมาะสม" else "Ideal temperature", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(if (s.isThai) "24-26°C ช่วยให้ลูกน้อยหลับสบายตลอดคืน" else "24-26°C helps your baby sleep comfortably through the night", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.LightMode, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(if (s.isThai) "การปรับแสง" else "Lighting", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(if (s.isThai) "หรี่ไฟลง 30 นาทีก่อนนอนเพื่อกระตุ้นเมลาโทนิน" else "Dim lights 30 minutes before bed to help melatonin release", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (s.isThai) "ช่วงเวลาตื่นที่แนะนำตามวัย" else "Recommended wake windows by age", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(4.dp)) {
                        WAKE_WINDOWS.forEachIndexed { i, w ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (s.isThai) w.ageRange else w.ageRangeEn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (s.isThai) w.window else w.windowEn, style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Bold)
                            }
                            if (i < WAKE_WINDOWS.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (s.isThai) "กิจวัตรก่อนนอน" else "Bedtime routine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                RoutineStep(1, if (s.isThai) "ผ่อนคลายร่างกาย" else "Calm the body", if (s.isThai) "อาบน้ำอุ่นหรือเช็ดตัวเบาๆ" else "Warm bath or a gentle wipe-down")
                RoutineStep(2, if (s.isThai) "กระตุ้นพัฒนาการ" else "Wind-down activity", if (s.isThai) "อ่านนิทานสั้นๆ ก่อนนอน" else "Read a short book together")
                RoutineStep(3, if (s.isThai) "กล่อมเข้านอน" else "Settle to sleep", if (s.isThai) "เปิดเพลงกล่อมเบาๆ หรือร้องเพลงกล่อม" else "Soft lullaby music or singing")
            }

            Button(
                onClick = onCreateSchedule, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (s.isThai) "สร้างตารางการนอน" else "Create sleep schedule", fontWeight = FontWeight.Bold)
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoutineStep(number: Int, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
            Text("$number", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

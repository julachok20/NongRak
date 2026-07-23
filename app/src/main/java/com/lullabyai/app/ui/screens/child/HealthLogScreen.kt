package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

/**
 * "Health Log" hub (per log_child_data mockup) — one place to reach every record-type screen,
 * reached from the child dashboard's "บันทึกสุขภาพ" button. Each card links to the existing
 * screen for that record type rather than duplicating their logic here.
 */
@Composable
fun HealthLogScreen(navController: NavController, isThai: Boolean) {
    ChildScreenScaffold(title = if (isThai) "บันทึกสุขภาพ" else "Health Log", currentTab = "health_log", navController = navController) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card -> the existing Health Hub (profile, AI analysis, allergy, vaccine progress,
            // medication, documents, doctor visits).
            Card(
                onClick = { navController.navigate(Screen.ChildHealth.route) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Primary)
            ) {
                Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MedicalServices, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) "ศูนย์สุขภาพ" else "Health Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            if (isThai) "ดูสรุปข้อมูลสุขภาพและการแจ้งเตือนทั้งหมด" else "See your child's full health summary and alerts",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.85f)
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Text(if (isThai) "บันทึกข้อมูล" else "Log data", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            val categories = listOf(
                LogCategory(Icons.Filled.MonitorWeight, if (isThai) "น้ำหนักและส่วนสูง" else "Weight & height", if (isThai) "ติดตามการเติบโต" else "Track growth", Screen.GrowthHistory.route),
                LogCategory(Icons.Filled.Vaccines, if (isThai) "สมุดวัคซีน" else "Vaccine book", if (isThai) "ตรวจสอบและบันทึกวัคซีน" else "Track vaccinations", Screen.VaccineBook.route),
                LogCategory(Icons.Filled.TrendingUp, if (isThai) "พัฒนาการ" else "Development", if (isThai) "บันทึกความก้าวหน้า" else "Log progress", Screen.ChildDevelopment.route),
                LogCategory(Icons.Filled.Bedtime, if (isThai) "การนอน" else "Sleep", if (isThai) "ติดตามการนอนหลับ" else "Track sleep", Screen.SleepTracker.route),
                LogCategory(Icons.Filled.Restaurant, if (isThai) "โภชนาการ" else "Nutrition", if (isThai) "บันทึกมื้ออาหาร" else "Log meals", Screen.Nutrition.route),
                LogCategory(Icons.Filled.FitnessCenter, if (isThai) "การออกกำลังกาย" else "Exercise", if (isThai) "กิจกรรมทางกาย" else "Physical activity", Screen.Exercise.route)
            )
            categories.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { cat ->
                        LogCategoryCard(cat, Modifier.weight(1f)) { navController.navigate(cat.route) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Lightbulb, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(
                        if (isThai) "การบันทึกข้อมูลสม่ำเสมอช่วยให้ AI วิเคราะห์สุขภาพลูกได้แม่นยำขึ้น" else "Logging consistently helps the AI analyze your child's health more accurately",
                        style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class LogCategory(val icon: ImageVector, val title: String, val subtitle: String, val route: String)

@Composable
private fun LogCategoryCard(category: LogCategory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(category.icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.AddCircle, null, tint = Primary, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(category.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(category.subtitle, style = MaterialTheme.typography.labelSmall, color = Outline)
            }
        }
    }
}

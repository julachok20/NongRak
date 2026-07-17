@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.growth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.EmptyChartState
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.GrowthReference
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun WhoWeightForAgeDetailScreen(childViewModel: ChildViewModel, navController: NavController) {
    WhoGrowthDetailScreen(childViewModel, navController, isWeight = true)
}

@Composable
fun WhoHeightForAgeDetailScreen(childViewModel: ChildViewModel, navController: NavController) {
    WhoGrowthDetailScreen(childViewModel, navController, isWeight = false)
}

/** Weight-for-age / height-for-age detail — shared implementation since both mockups are the same
 *  layout with a different metric. The reference band is the app's existing approximate age-banded
 *  range (see GrowthReference), NOT a real WHO LMS percentile/Z-score curve — no Z-score column is
 *  shown for the same reason GrowthHistoryScreen doesn't fabricate one. */
@Composable
private fun WhoGrowthDetailScreen(childViewModel: ChildViewModel, navController: NavController, isWeight: Boolean) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val child = state.child
    val records = state.growthRecords // newest-first
    val ageMonthsNow = childViewModel.getAgeMonths()
    val latest = records.firstOrNull { if (isWeight) it.weightKg != null else it.heightCm != null }
    val latestValue = latest?.let { if (isWeight) it.weightKg else it.heightCm }
    val status = if (isWeight) GrowthReference.weightStatus(latestValue, ageMonthsNow) else GrowthReference.heightStatus(latestValue, ageMonthsNow)
    val unit = if (isWeight) (if (isThai) "กก." else "kg") else (if (isThai) "ซม." else "cm")
    val title = if (isWeight) (if (isThai) "กราฟน้ำหนักตามเกณฑ์อายุ (WHO)" else "Weight-for-Age Chart (WHO)")
                else (if (isThai) "กราฟส่วนสูงตามเกณฑ์อายุ (WHO)" else "Height-for-Age Chart (WHO)")

    Scaffold(
        topBar = { LullabyTopBar(title = title, onBack = { navController.popBackStack() }) },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.STATS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(child?.name ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("($ageMonthsNow " + (if (isThai) "เดือน)" else "months)"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (latestValue != null) {
                        Text("$latestValue $unit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                    }
                    status?.let {
                        Surface(shape = RoundedCornerShape(100.dp), color = it.color.copy(0.12f)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = it.color, modifier = Modifier.size(16.dp))
                                Text(if (isThai) it.labelTh else it.labelEn, style = MaterialTheme.typography.labelMedium, color = it.color, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.GrowthHistory.route) },
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(if (isWeight) Icons.Filled.MonitorWeight else Icons.Filled.Straighten, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text((if (isThai) "อัปเดต" else "Update ") + (if (isWeight) (if (isThai) "น้ำหนัก" else "weight") else (if (isThai) "ส่วนสูง" else "height")), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (isThai) "กราฟการเจริญเติบโต" else "Growth chart", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (isThai) "แถบสีคือช่วงอ้างอิงคร่าวๆ ตามวัย (ไม่ใช่กราฟเปอร์เซ็นไทล์ WHO จริง)" else "The shaded band is an approximate age-based reference range (not a real WHO percentile chart)",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        val hasPoints = records.any { if (isWeight) it.weightKg != null else it.heightCm != null }
                        if (!hasPoints) EmptyChartState(isThai) else GrowthBandChart(child?.birthdate, records, isWeight)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isThai) "ประวัติการบันทึก" else "Record history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.GrowthHistory.route) }) {
                        Text(if (isThai) "ดูประวัติทั้งหมด" else "View all", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val recent = records.filter { if (isWeight) it.weightKg != null else it.heightCm != null }.take(3)
            if (recent.isEmpty()) {
                item { EmptyChartState(isThai) }
            } else {
                items(recent) { r ->
                    val idx = records.indexOf(r)
                    val prev = records.drop(idx + 1).firstOrNull { if (isWeight) it.weightKg != null else it.heightCm != null }
                    val value = if (isWeight) r.weightKg else r.heightCm
                    val prevValue = if (isWeight) prev?.weightKg else prev?.heightCm
                    val delta = if (value != null && prevValue != null) value - prevValue else null
                    val ageAt = child?.birthdate?.let { ageMonthsAt(it, r.recordedAt) } ?: 0
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("$ageAt " + (if (isThai) "เดือน" else "mo"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(com.lullabyai.app.util.TimeUtils.localDate(r.recordedAt) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$value $unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Primary)
                                delta?.let {
                                    val deltaColor = if (it >= 0) Color(0xFF2E7D32) else Error
                                    Text((if (it >= 0) "+" else "") + "%.1f $unit ".format(it) + (if (isThai) "จากค่าเฉลี่ย" else "vs prior"), style = MaterialTheme.typography.labelSmall, color = deltaColor)
                                }
                            }
                        }
                    }
                }
            }

            item {
                val nextAge = ageMonthsNow + 3
                val (bandMin, bandMax) = GrowthReference.band(nextAge, isWeight)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isThai) "เป้าหมายถัดไป" else "Next milestone", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            (if (isThai) "อายุ $nextAge เดือน" else "At $nextAge months") + ": " +
                                (if (isThai) "ช่วงเฉลี่ย WHO ~" else "WHO reference range ~") + "%.1f-%.1f $unit".format(bandMin, bandMax),
                            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.9f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

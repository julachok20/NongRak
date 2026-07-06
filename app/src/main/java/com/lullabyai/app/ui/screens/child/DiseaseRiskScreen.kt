@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

private val AttentionColor = Color(0xFFBA1A1A)

@Composable
fun DiseaseRiskScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child

    LaunchedEffect(child) {
        child?.let { chatViewModel.loadDiseaseRisk(it, s.isThai); chatViewModel.loadWeeklyHealthReport(it, s.isThai) }
    }

    val diseases = chatState.diseaseList
    val preventions = chatState.preventionList

    ChildScreenScaffold(title = if (s.isThai) "ความเสี่ยงโรค" else "Disease Risk", currentTab = "risk", navController = navController, centerTitle = true) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Overview card — shield icon, framing, "อัปเดตข้อมูล" refresh button
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape)
                                .background(Primary.copy(0.08f))
                                .then(Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.VerifiedUser, null, tint = Primary, modifier = Modifier.size(32.dp))
                        }
                        Text(
                            if (s.isThai) "ภาพรวมสุขภาพวันนี้" else "Today's Health Overview",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        com.lullabyai.app.ui.components.AiTextOrTimeout(
                            text = chatState.diseaseRiskAnalysis,
                            timeoutMessage = if (s.isThai) "ไม่สามารถโหลดข้อมูลเพิ่มเติม ลองแตะ \"อัปเดตข้อมูล\" อีกครั้ง" else "Unable to load. Try \"Update data\" again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { child?.let { chatViewModel.refreshAllAnalysis(it, s.isThai) } },
                            enabled = !chatState.isRefreshing,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (chatState.isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(if (s.isThai) "วิเคราะห์อีกครั้ง" else "Re-analyze", fontWeight = FontWeight.Bold)
                            }
                        }
                        // The label alone doesn't say what it touches — refreshAllAnalysis() re-runs
                        // the AI over disease risk, growth, health and the weekly report together.
                        Text(
                            if (s.isThai) "วิเคราะห์ข้อมูลสุขภาพ พัฒนาการ และความเสี่ยงโรคด้วย AI อีกครั้ง"
                            else "Re-runs AI analysis over health, growth, and disease risk data",
                            style = MaterialTheme.typography.labelSmall, color = Outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Disease risk list
            item { Text(if (s.isThai) "โรคที่ควรเฝ้าระวัง" else "Diseases to watch", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            if (diseases.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "กำลังวิเคราะห์..." else "Analyzing...", color = Outline) } }
            } else {
                items(diseases) { d ->
                    RiskCard(RiskItem(icon = iconFor(d.name), name = d.name, detail = d.description, risk = d.percent, level = d.level))
                }
            }

            // AI Prediction — weekly health report teaser
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                if (s.isThai) "คาดการณ์โดย AI" else "AI PREDICTION",
                                style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            if (s.isThai) "รายงานสุขภาพรายสัปดาห์" else "Weekly health report",
                            style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold
                        )
                        val teaser = chatState.weeklyHealthReport?.let { if (it.length > 140) it.take(140) + "…" else it }
                            ?: (if (s.isThai) "กำลังวิเคราะห์..." else "Analyzing...")
                        Text(teaser, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                        Button(
                            onClick = { navController.navigate(Screen.WeeklyHealthReport.route) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Primary)
                        ) {
                            Text(if (s.isThai) "ดูรายงานเชิงลึก" else "View in-depth report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Prevention tips
            item { Text(if (s.isThai) "คำแนะนำการป้องกัน" else "Prevention tips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            if (preventions.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "กำลังวิเคราะห์..." else "Analyzing...", color = Outline) } }
            } else {
                items(preventions) { p -> PreventionCard(Icons.Filled.Shield, p.title, p.detail) }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

data class RiskItem(val icon: ImageVector, val name: String, val detail: String, val risk: Int, val level: String)

private fun iconFor(name: String): ImageVector = when {
    name.contains("หวัด") || name.contains("ไข้") -> Icons.Filled.WbSunny
    name.contains("ผิว") || name.contains("ภูมิแพ้") || name.contains("ผื่น") -> Icons.Filled.Healing
    name.contains("RSV") || name.contains("ทางเดินหายใจ") || name.contains("ปอด") -> Icons.Filled.AcUnit
    name.contains("อาหาร") || name.contains("ท้อง") || name.contains("ลำไส้") -> Icons.Filled.Restaurant
    name.contains("ฟัน") || name.contains("เหงือก") || name.contains("ปาก") -> Icons.Filled.Spa
    else -> Icons.Filled.MonitorHeart
}

/**
 * Badge/icon color is neutral grey for normal/low risk and reserved (solid red) only for
 * attention/high — matches the DESIGN.md "calm, non-alarming" principle: color emphasis should
 * signal something worth noticing, not decorate every card.
 */
@Composable
private fun RiskCard(riskItem: RiskItem) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isAttention = riskItem.level == "attention" || riskItem.level == "high"
    val accent = if (isAttention) AttentionColor else Primary
    val iconBg = if (isAttention) AttentionColor.copy(0.12f) else Primary.copy(0.08f)
    val badgeBg = if (isAttention) AttentionColor else MaterialTheme.colorScheme.surfaceVariant
    val badgeFg = if (isAttention) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val badgeText = when (riskItem.level) {
        "attention", "high" -> "NEEDS ATTENTION"
        "low" -> "LOW RISK"
        else -> "NORMAL"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                    Icon(riskItem.icon, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badgeBg) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = badgeFg, fontWeight = FontWeight.Bold)
                }
            }
            Text(riskItem.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(riskItem.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (s.isThai) "ความเสี่ยง: " else "Risk: ") + "${riskItem.risk}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAttention) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isAttention) FontWeight.Bold else FontWeight.Normal
                )
                LinearProgressIndicator(
                    progress = { riskItem.risk / 100f },
                    modifier = Modifier.width(96.dp).height(8.dp).clip(RoundedCornerShape(100.dp)),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreventionCard(icon: ImageVector, title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

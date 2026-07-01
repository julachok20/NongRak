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
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun DiseaseRiskScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child

    LaunchedEffect(child) {
        child?.let { chatViewModel.loadDiseaseRisk(it) }
    }

    val diseases = chatState.diseaseList
    val preventions = chatState.preventionList
    val s = com.lullabyai.app.ui.localization.LocalStrings.current

    ChildScreenScaffold(title = if (s.isThai) "ความเสี่ยงโรค" else "Disease Risk", currentTab = "risk", navController = navController, centerTitle = true) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Outline.copy(0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Security, null, tint = Outline, modifier = Modifier.size(36.dp))
                }
                Text(if (s.isThai) "เร็วๆ นี้" else "Coming Soon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(if (s.isThai) "ระบบวิเคราะห์ความเสี่ยงโรคกำลังพัฒนา\nติดตามอัปเดตเร็วๆ นี้" else "Disease risk analysis is coming soon.\nStay tuned for updates!", style = MaterialTheme.typography.bodyMedium, color = Outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
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

@Composable
private fun RiskCard(riskItem: RiskItem) {
    val (badgeColor, badgeText) = when (riskItem.level) {
        "attention" -> Pair(Color(0xFFFF5722), "NEEDS ATTENTION")
        "low" -> Pair(Color(0xFF4CAF50), "LOW RISK")
        else -> Pair(Primary, "NORMAL")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(
                    (if (riskItem.level == "attention") Color(0xFFFF5722) else if (riskItem.level == "low") Color(0xFF4CAF50) else Primary).copy(0.1f)
                ), contentAlignment = Alignment.Center) {
                    Icon(riskItem.icon, null, tint = badgeColor, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(riskItem.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(riskItem.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.1f)) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text((if (com.lullabyai.app.ui.localization.LocalStrings.current.isThai) "ความเสี่ยง: " else "Risk: ") + "${riskItem.risk}%", style = MaterialTheme.typography.labelSmall, color = Outline)
            }
            LinearProgressIndicator(
                progress = { riskItem.risk / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
                color = badgeColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
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

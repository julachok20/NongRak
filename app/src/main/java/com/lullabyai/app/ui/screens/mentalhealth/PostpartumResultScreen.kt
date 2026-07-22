package com.lullabyai.app.ui.screens.mentalhealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.PostpartumCatalog
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel

private fun tierColor(tier: String) = when (tier) {
    "high" -> Error
    "moderate" -> Color(0xFFEF6C00)
    else -> Color(0xFF2E7D32)
}

/** Rule-based narrative — no extra AI call per submission, same zero-running-cost approach as
 *  MentalHealthResultScreen's narrative(). */
private fun narrative(tier: String, isThai: Boolean): String = when (tier) {
    "high" -> if (isThai) "ผลของคุณบ่งชี้ว่าควรพบแพทย์เร็วๆ นี้ โดยเฉพาะหากมีเลือดออกมากหรือมีไข้สูง อย่ารอ ให้ติดต่อโรงพยาบาลหรือสายด่วนฉุกเฉิน 1669"
        else "Your result suggests you should see a doctor soon — especially if bleeding is heavy or you have a high fever. Don't wait; contact your hospital or the 1669 emergency line."
    "moderate" -> if (isThai) "มีบางอาการที่ควรติดตามใกล้ชิด พักผ่อนให้เพียงพอ และนัดพบแพทย์หากอาการไม่ดีขึ้นภายในไม่กี่วัน"
        else "A few symptoms are worth watching closely. Rest as much as you can, and see a doctor if things don't improve within a few days."
    else -> if (isThai) "ร่างกายของคุณกำลังฟื้นตัวได้ดี ยังคงพักผ่อนและดูแลตัวเองต่อไป ทำแบบประเมินนี้อีกครั้งในเดือนหน้า"
        else "Your body is recovering well. Keep resting and taking care of yourself, and check in again next month."
}

@Composable
fun PostpartumResultScreen(authViewModel: AuthViewModel, navController: androidx.navigation.NavController, onBackToDashboard: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val authState by authViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { authViewModel.loadPostpartumHistory() }
    val result = authState.lastPostpartumResult ?: authState.postpartumHistory.firstOrNull()

    Scaffold(
        topBar = { com.lullabyai.app.ui.components.LullabyTopBar(title = if (isThai) "ผลการประเมิน" else "Your Result", onBack = onBackToDashboard) },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.HOME,
                onHome = onBackToDashboard,
                onChatbot = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (result == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            return@Scaffold
        }
        val color = tierColor(result.riskTier)
        val maxScore = PostpartumCatalog.questions.size * 3
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(140.dp)) {
                    val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    drawArc(Color(0xFFE0E3E5), -90f, 360f, false, style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = Offset(inset, inset))
                    drawArc(color, -90f, 360f * (result.totalScore.toFloat() / maxScore).coerceIn(0f, 1f), false, style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = Offset(inset, inset))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${result.totalScore}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                    Text("/ $maxScore", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(100.dp), color = color.copy(0.12f)) {
                Text(PostpartumCatalog.riskTierLabel(result.riskTier, isThai), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = color, fontWeight = FontWeight.Bold)
            }

            if (result.flaggedUrgent) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Error.copy(0.08f))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, null, tint = Error)
                            Text(if (isThai) "อาการนี้ควรพบแพทย์ทันที" else "This needs urgent medical attention", fontWeight = FontWeight.Bold, color = Error)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isThai) "หากเลือดออกมากหรือมีไข้สูง ให้ไปโรงพยาบาลทันที หรือโทร 1669 (สายด่วนฉุกเฉิน ฟรี ตลอด 24 ชั่วโมง)" else "If bleeding is heavy or you have a high fever, go to the hospital now, or call 1669 (free 24-hour emergency line).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Favorite, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(if (isThai) "คำแนะนำ" else "Guidance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(narrative(result.riskTier, isThai), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (authState.postpartumHistory.size > 1) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (isThai) "แนวโน้มการฟื้นตัว" else "Recovery trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        PostpartumTrendChart(authState.postpartumHistory, maxScore)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text(if (isThai) "กลับหน้าหลัก" else "Back to Dashboard", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PostpartumTrendChart(history: List<com.lullabyai.app.data.model.PostpartumCheckIn>, maxScore: Int) {
    val points = history.reversed() // oldest first
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = size.height * (1f - p.totalScore.toFloat() / maxScore)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(Primary, radius = 3.dp.toPx(), center = Offset(x, y))
        }
        drawPath(path, color = Primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

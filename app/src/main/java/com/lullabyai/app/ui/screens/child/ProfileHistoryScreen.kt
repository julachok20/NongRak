@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun ProfileHistoryScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child
    val milestones = childState.milestones

    LaunchedEffect(child) { child?.let { chatViewModel.loadChildInfoSummary(it, s.isThai) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ประวัติเด็ก" else "Child Profile", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Header — photo, name, age, edit
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SecondaryContainer.copy(0.45f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        val avatar = childState.childAvatarSignedUrl
                        if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text("👶", fontSize = 44.sp)
                    }
                    Text(child?.name ?: (if (s.isThai) "ลูกของคุณ" else "Your child"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(childViewModel.calculateAge(s.isThai), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { navController.navigate(Screen.EditChild.route) }, shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (s.isThai) "แก้ไขโปรไฟล์" else "Edit profile", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Basic info
            ProfileCard {
                CardHeader(Icons.Filled.Info, if (s.isThai) "ข้อมูลเบื้องต้น" else "Basic info")
                InfoLine(if (s.isThai) "วันเกิด" else "Birthdate", formatDate(child?.birthdate, s.isThai))
                InfoLine(if (s.isThai) "เพศ" else "Gender", genderLabel(child?.gender, s.isThai))
            }

            // Latest growth
            ProfileCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CardHeaderInline(Icons.Filled.TrendingUp, if (s.isThai) "การเติบโตล่าสุด" else "Latest growth")
                    Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFF4CAF50).copy(0.15f)) {
                        Text(if (s.isThai) "ปกติ" else "Normal", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GrowthStatCard(if (s.isThai) "น้ำหนัก" else "Weight", "${child?.weightKg ?: "-"} " + (if (s.isThai) "กก." else "kg"), "", Modifier.weight(1f)) { navController.navigate(Screen.GrowthMetrics.route) }
                    GrowthStatCard(if (s.isThai) "ส่วนสูง" else "Height", "${child?.heightCm ?: "-"} " + (if (s.isThai) "ซม." else "cm"), "", Modifier.weight(1f)) { navController.navigate(Screen.GrowthMetrics.route) }
                }
                LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)), color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (s.isThai) "เกณฑ์ต่ำ" else "Low", style = MaterialTheme.typography.labelSmall, color = Outline)
                    Text(if (s.isThai) "เกณฑ์ปกติ" else "Normal", style = MaterialTheme.typography.labelSmall, color = Primary)
                    Text(if (s.isThai) "เกณฑ์สูง" else "High", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }

            // Health history
            ProfileCard {
                CardHeader(Icons.Filled.FavoriteBorder, if (s.isThai) "ประวัติสุขภาพ" else "Health history")
                Text(if (s.isThai) "อาการแพ้" else "Allergies", style = MaterialTheme.typography.labelMedium, color = Outline)
                if (childState.allergies.isEmpty()) {
                    Text(if (s.isThai) "ไม่มีประวัติแพ้" else "None", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        childState.allergies.take(4).forEach { a ->
                            Surface(shape = RoundedCornerShape(100.dp), color = Error.copy(0.1f)) {
                                Text(a.name, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Error)
                            }
                        }
                    }
                }
                Text(if (s.isThai) "โรคประจำตัว" else "Chronic conditions", style = MaterialTheme.typography.labelMedium, color = Outline)
                Text(if (s.isThai) "ไม่มี" else "None", style = MaterialTheme.typography.bodyMedium)
            }

            // Development
            ProfileCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CardHeaderInline(Icons.Filled.EmojiEvents, if (s.isThai) "พัฒนาการ" else "Development")
                    Text(if (s.isThai) "ดูทั้งหมด" else "View all", modifier = Modifier.clickable { navController.navigate(Screen.DevelopmentHistory.route) }, color = Primary, style = MaterialTheme.typography.labelMedium)
                }
                val devCats = milestones.map { it.category }.distinct().take(3)
                if (devCats.isEmpty()) Text(if (s.isThai) "ยังไม่มีข้อมูลพัฒนาการ" else "No development data yet", style = MaterialTheme.typography.bodySmall, color = Outline)
                devCats.forEach { cat ->
                    val catMs = milestones.filter { it.category == cat }
                    val done = catMs.count { it.isAchieved }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = if (done > 0) Primary else Outline, modifier = Modifier.size(20.dp))
                        Column {
                            Text(catLabel(cat, s.isThai), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("$done/${catMs.size} " + (if (s.isThai) "ทักษะ" else "skills"), style = MaterialTheme.typography.labelSmall, color = Outline)
                        }
                    }
                }
            }

            // Monthly health summary (AI)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.07f))) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (s.isThai) "สรุปภาพรวมสุขภาพรายเดือน" else "Monthly health summary", style = MaterialTheme.typography.labelMedium, color = Primary)
                    Text(if (s.isThai) "สุขภาพโดยรวม: ยอดเยี่ยม" else "Overall: Excellent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                    if (chatState.childInfoSummary != null) Text(chatState.childInfoSummary!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    else LinearProgressIndicator(color = Primary)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

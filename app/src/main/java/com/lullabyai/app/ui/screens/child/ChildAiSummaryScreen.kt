package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** AI-aggregated child summary — pulls together data that already lives in ChildUiState/repos into
 *  one glanceable set of tiles, reached from ChildSummaryScreen's "ตรวจสอบข้อมูลสุขภาพ" CTA. Almost
 *  everything here is existing data being surfaced in a new place, not new backend work. */
@Composable
fun ChildAiSummaryScreen(childViewModel: ChildViewModel, chatViewModel: ChatViewModel, navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child
    val context = LocalContext.current

    var chronicCondition by remember { mutableStateOf<String?>(null) }
    var todayKcal by remember { mutableStateOf(0) }
    LaunchedEffect(child?.id) {
        child?.let { c ->
            chronicCondition = runCatching {
                com.lullabyai.app.data.repository.ChildRepository().getMedicalHistory(c.id).getOrNull()?.underlyingConditions
            }.getOrNull()
            val today = java.time.LocalDate.now().toString()
            todayKcal = runCatching {
                com.lullabyai.app.data.repository.NutritionRepository().getFoodLogs(c.id, today).sumOf { it.calories ?: 0 }
            }.getOrDefault(0)
            chatViewModel.loadChildInfoSummary(c, isThai)
        }
    }

    val ageMonths = childViewModel.getAgeMonths()
    val nextGoal = remember(childState.milestones, ageMonths) {
        childState.milestones.filter { !it.isAchieved && it.skipReason == null }
            .sortedBy { it.ageMonthsExpected ?: 0 }.firstOrNull()
    }
    val milestonePct = remember(childState.milestones) {
        if (childState.milestones.isEmpty()) 0 else childState.milestones.count { it.isAchieved } * 100 / childState.milestones.size
    }
    val sleepTarget = childViewModel.sleepTargetHours()
    val nutritionTargetKcal = 1200 // rough daily reference; no per-child target field exists yet

    // Growth-related Stitch bits: verification badge, last-updated, trend (decision #13)
    val records = childState.growthRecords
    val latestGrowthAt = records.firstOrNull()?.recordedAt
    val latestWeight = records.firstOrNull { it.weightKg != null }
    val prevWeight = latestWeight?.let { lw ->
        records.drop(records.indexOf(lw) + 1).firstOrNull { it.weightKg != null }?.weightKg
    }
    val weightTrend = if (latestWeight?.weightKg != null && prevWeight != null) latestWeight.weightKg!! - prevWeight else null
    val latestHeight = records.firstOrNull { it.heightCm != null }
    val prevHeight = latestHeight?.let { lh ->
        records.drop(records.indexOf(lh) + 1).firstOrNull { it.heightCm != null }?.heightCm
    }
    val heightTrend = if (latestHeight?.heightCm != null && prevHeight != null) latestHeight.heightCm!! - prevHeight else null
    val lastUpdatedLabel = remember(latestGrowthAt, isThai, chatState.childInfoSummary) {
        formatAiSummaryUpdated(latestGrowthAt, isThai)
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "ข้อมูลสรุปเด็กจาก AI" else "AI Child Summary", onBack = onBack, navController = navController) },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.MyDoctorsHub.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Profile header — verified badge + last-updated (Stitch AI Child Profile Summary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                        val avatar = childState.childAvatarSignedUrl
                        if (!avatar.isNullOrBlank()) coil.compose.AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Text("👶", fontSize = 28.sp)
                    }
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Verified, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(child?.name ?: (if (isThai) "ลูกของคุณ" else "Your child"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(100.dp), color = Secondary.copy(0.18f)) {
                            Text(
                                childViewModel.calculateAge(isThai),
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        child?.gender?.takeIf { it.isNotBlank() }?.let { g ->
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.12f)) {
                                Text(
                                    genderLabel(g, isThai),
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Schedule, null, tint = Outline, modifier = Modifier.size(14.dp))
                        Text(
                            lastUpdatedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AI narrative — bolder "highlighted insight" treatment (solid tint + left accent bar)
            // rather than a near-white generic info card.
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.14f))) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(Primary))
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Primary.copy(0.25f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(14.dp))
                            }
                            Text(if (isThai) "ข้อมูลเชิงลึกจาก AI" else "AI Insights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (chatState.childInfoSummary != null) Text(chatState.childInfoSummary!!, style = MaterialTheme.typography.bodyMedium)
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(1), modifier = Modifier.heightIn(max = 1200.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val trendBits = buildList {
                        weightTrend?.let { add((if (it >= 0) "+" else "") + "%.1f kg".format(it)) }
                        heightTrend?.let { add((if (it >= 0) "+" else "") + "%.1f cm".format(it)) }
                    }.joinToString(" · ")
                    SummaryTile(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        if (isThai) "การเจริญเติบโต" else "Growth",
                        "${child?.weightKg ?: "-"}kg / ${child?.heightCm ?: "-"}cm" +
                            (if (trendBits.isNotBlank()) "\n${if (isThai) "แนวโน้ม" else "Trend"}: $trendBits" else "")
                    ) { navController.navigate(Screen.GrowthHistory.route) }
                }
                item {
                    SummaryTile(Icons.Filled.MedicalServices, if (isThai) "ข้อมูลทางการแพทย์" else "Medical",
                        if (child?.bloodGroup != null) "${child.bloodGroup}" else (if (isThai) "ไม่ระบุกรุ๊ปเลือด" else "No blood type")
                    ) { navController.navigate(Screen.EditChild.route) }
                }
                item {
                    SummaryTile(Icons.Filled.Warning, if (isThai) "ภูมิแพ้" else "Allergies",
                        if (childState.allergies.isEmpty()) (if (isThai) "ไม่มีประวัติแพ้" else "No known allergies")
                        else "${childState.allergies.size} " + (if (isThai) "รายการ" else "recorded")
                    ) { navController.navigate(Screen.AllergyHistory.route) }
                }
                item {
                    SummaryTile(Icons.Filled.HealthAndSafety, if (isThai) "โรคประจำตัว" else "Chronic conditions",
                        chronicCondition?.takeIf { it.isNotBlank() } ?: (if (isThai) "ไม่มีโรคประจำตัว" else "None recorded")
                    ) { navController.navigate(Screen.EditChild.route) }
                }
                item {
                    SummaryTile(Icons.Filled.TrendingUp, if (isThai) "พัฒนาการ" else "Milestones",
                        "$milestonePct% " + (if (isThai) "สำเร็จ" else "done") +
                            (nextGoal?.let { " · ${it.milestoneName}" } ?: "")) { navController.navigate(Screen.ChildDevelopment.route) }
                }
                item {
                    SummaryTile(Icons.Filled.Bedtime, if (isThai) "การนอน" else "Sleep",
                        "${child?.sleepHours?.toInt() ?: "-"}h / ${sleepTarget}h " + (if (isThai) "เป้าหมาย" else "target")) { navController.navigate(Screen.SleepTracker.route) }
                }
                item {
                    SummaryTile(Icons.Filled.Restaurant, if (isThai) "โภชนาการวันนี้" else "Nutrition today",
                        "$todayKcal / $nutritionTargetKcal kcal") { navController.navigate(Screen.Nutrition.route) }
                }
                item {
                    SummaryTile(Icons.Filled.Description, if (isThai) "เอกสารทางการแพทย์" else "Documents",
                        "${childState.medicalDocuments.size} " + (if (isThai) "ไฟล์" else "files")) { navController.navigate(Screen.MedicalDocumentHistory.route) }
                }
            }

            Spacer(Modifier.height(4.dp))
            // Stacked, primary action first — matches the mockup's emphasis (update is the main
            // action here; PDF export is secondary), reversed from the earlier side-by-side layout.
            Button(
                onClick = { navController.navigate(Screen.EditChild.route) },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text(if (isThai) "อัปเดตข้อมูล" else "Update data", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { navController.navigate(Screen.PdfExport.route) },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(100.dp)
            ) { Text(if (isThai) "ส่งออก PDF" else "Export PDF") }
        }
    }
}

@Composable
private fun SummaryTile(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun genderLabel(gender: String, isThai: Boolean): String = when (gender.lowercase()) {
    "male", "m", "ชาย", "เด็กชาย" -> if (isThai) "เพศชาย" else "Male"
    "female", "f", "หญิง", "เด็กหญิง" -> if (isThai) "เพศหญิง" else "Female"
    else -> gender
}

/** Stitch-style "อัปเดตล่าสุด: วันนี้, HH:mm" from latest growth record date (or now if AI just loaded). */
private fun formatAiSummaryUpdated(recordedAt: String?, isThai: Boolean): String {
    val datePart = recordedAt?.take(10)
    val today = LocalDate.now().toString()
    val timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    return when {
        datePart == null -> if (isThai) "อัปเดตล่าสุด: วันนี้, $timeNow น." else "Last updated: today, $timeNow"
        datePart == today -> if (isThai) "อัปเดตล่าสุด: วันนี้, $timeNow น." else "Last updated: today, $timeNow"
        else -> {
            val display = com.lullabyai.app.util.TimeUtils.localDate(recordedAt) ?: datePart
            if (isThai) "อัปเดตล่าสุด: $display" else "Last updated: $display"
        }
    }
}

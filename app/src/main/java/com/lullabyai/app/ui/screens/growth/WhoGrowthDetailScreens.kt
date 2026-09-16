@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.growth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.EmptyChartState
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.GrowthReference
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun WhoWeightForAgeDetailScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController
) {
    WhoGrowthDetailScreen(childViewModel, chatViewModel, navController, isWeight = true)
}

@Composable
fun WhoHeightForAgeDetailScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController
) {
    WhoGrowthDetailScreen(childViewModel, chatViewModel, navController, isWeight = false)
}

/** Weight-for-age / height-for-age detail — Stitch WHO charts.
 *  Status and band come from WHO LMS tables by sex and age (GrowthReference). */
@Composable
private fun WhoGrowthDetailScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    isWeight: Boolean
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child
    val records = state.growthRecords // newest-first
    val ageMonthsNow = childViewModel.getAgeMonths()
    val latest = records.firstOrNull { if (isWeight) it.weightKg != null else it.heightCm != null }
    val latestValue = latest?.let { if (isWeight) it.weightKg else it.heightCm }
    val latestWeight = records.firstOrNull { it.weightKg != null }?.weightKg
    val latestHeight = records.firstOrNull { it.heightCm != null }?.heightCm
    val gender = child?.gender
    // WHO, for this sex, at the child's age on the day of the reading — not today's age.
    val latestAgeDays = GrowthReference.ageDaysAt(child?.birthdate, latest?.recordedAt)
    val status = if (isWeight) GrowthReference.weightStatus(latestValue, latestAgeDays, gender)
    else GrowthReference.heightStatus(latestValue, latestAgeDays, gender)
    // สมส่วน needs weight and height from the same day; mixing a new weight with an old height misleads.
    val pairRecord = records.firstOrNull { it.weightKg != null && it.heightCm != null }
    val wfhStatus = GrowthReference.weightForHeightStatus(
        pairRecord?.weightKg, pairRecord?.heightCm, GrowthReference.ageDaysAt(child?.birthdate, pairRecord?.recordedAt), gender
    )
    val unit = if (isWeight) (if (isThai) "กก." else "kg") else (if (isThai) "ซม." else "cm")
    val title = if (isWeight) (if (isThai) "กราฟน้ำหนักตามเกณฑ์อายุ (WHO)" else "Weight-for-Age Chart (WHO)")
    else (if (isThai) "กราฟส่วนสูงตามเกณฑ์อายุ (WHO)" else "Height-for-Age Chart (WHO)")

    LaunchedEffect(child?.id, isThai) {
        val c = child ?: return@LaunchedEffect
        chatViewModel.loadGrowthInsight(c, isThai)
    }

    Scaffold(
        topBar = { LullabyTopBar(title = title, onBack = { navController.popBackStack() }, navController = navController) },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(child?.name ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "($ageMonthsNow " + (if (isThai) "เดือน)" else "months)"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (latestValue != null) {
                        Text(
                            "$latestValue $unit",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    status?.let {
                        Surface(shape = RoundedCornerShape(100.dp), color = it.color.copy(0.12f)) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, tint = it.color, modifier = Modifier.size(16.dp))
                                Text(
                                    if (isThai) it.labelTh else it.labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = it.color,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.AddGrowthRecord.route) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(
                        if (isWeight) Icons.Filled.MonitorWeight else Icons.Filled.Straighten,
                        null, tint = Color.White, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (isThai) "อัปเดต" else "Update ") +
                            (if (isWeight) (if (isThai) "น้ำหนัก" else "weight") else (if (isThai) "ส่วนสูง" else "height")),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (isThai) "กราฟการเจริญเติบโต" else "Growth chart",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isThai) "เส้นประคือช่วงปกติตามมาตรฐาน WHO (−2 ถึง +2 SD) ตามอายุและเพศ"
                            else "Dashed lines are the WHO normal range (−2 to +2 SD) for age and sex",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        val hasPoints = records.any { if (isWeight) it.weightKg != null else it.heightCm != null }
                        if (!hasPoints) EmptyChartState(isThai) else GrowthBandChart(child?.birthdate, records, isWeight, gender)
                    }
                }
            }

            // AI recommendation (shared narrative) + metric-specific bullets / tips
            item {
                if (chatState.growthInsight == null) {
                    AIInsightCard(text = "", isLoading = true, title = if (isThai) "คำแนะนำจาก AI" else "AI recommendations")
                } else {
                    WhoAiRecommendationCard(
                        insight = chatState.growthInsight,
                        isLoading = false,
                        isWeight = isWeight,
                        latestValue = latestValue,
                        unit = unit,
                        status = status,
                        ageMonths = ageMonthsNow,
                        isThai = isThai
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (isThai) "ประวัติการบันทึก" else "Record history",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
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
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "$ageAt " + (if (isThai) "เดือน" else "mo"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    com.lullabyai.app.util.TimeUtils.localDate(r.recordedAt) ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "$value $unit",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                                delta?.let {
                                    val deltaColor = if (it >= 0) Color(0xFF2E7D32) else Error
                                    Text(
                                        (if (it >= 0) "+" else "") + "%.1f $unit ".format(it) +
                                            (if (isThai) "จากครั้งก่อน" else "vs prior"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = deltaColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isWeight) {
                // Weight-for-Height + next target bento (Stitch WHO Weight-for-Age)
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.55f))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (isThai) "การเปรียบเทียบเทียบส่วนสูง" else "Height comparison",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Weight-for-Height", style = MaterialTheme.typography.labelSmall, color = Outline)
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        wfhStatus?.let { if (isThai) it.labelTh else it.labelEn }
                                            ?: (if (isThai) "ยังไม่มีข้อมูล" else "No data"),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = wfhStatus?.color ?: Primary
                                    )
                                    Icon(Icons.Filled.Straighten, null, tint = Primary.copy(0.5f), modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        val nextAge = ageMonthsNow + 3
                        val mid = GrowthReference.median(GrowthReference.monthsToDays(nextAge), isWeight = true, gender)
                        Card(
                            Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Secondary.copy(0.18f))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (isThai) "เป้าหมายถัดไป" else "Next milestone",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isThai) "อายุ $nextAge เดือน" else "At $nextAge months",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            mid?.let { "%.1f kg".format(it) } ?: "-",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (isThai) "(ค่ามัธยฐาน WHO)" else "(WHO median)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Outline,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Icon(Icons.Filled.Flag, null, tint = Secondary, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                // Bone-building + AI tip (Stitch WHO Height-for-Age)
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.14f))
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.FitnessCenter, null, tint = Primary, modifier = Modifier.size(28.dp))
                            Text(
                                if (isThai) "เสริมสร้างมวลกระดูก" else "Bone building",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Text(
                                if (isThai) "กิจกรรมและสารอาหารแนะนำเพื่อช่วยให้น้องเติบโตอย่างสมบูรณ์ตามเกณฑ์"
                                else "Activities and nutrients that support healthy height growth",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            BoneTipRow(
                                if (isThai) "ท่าบริหารยืดเหยียดสำหรับวัยเตาะแตะ"
                                else "Gentle stretch moves for toddlers"
                            )
                            BoneTipRow(
                                if (isThai) "เมนูแคลเซียมสูงที่เด็กชอบ"
                                else "Calcium-rich meals kids enjoy"
                            )
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary) {
                                Text(
                                    if (isThai) "อ่านคำแนะนำจาก AI" else "Read AI tips",
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Secondary.copy(0.14f))
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Lightbulb, null, tint = Secondary, modifier = Modifier.size(22.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (isThai) "เกร็ดความรู้ AI" else "AI tip",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isThai) "การนอนหลับสนิทในช่วง 22:00 - 02:00 น. สำคัญมากต่อการหลั่ง Growth Hormone เพื่อความสูงของลูกน้อย"
                                    else "Deep sleep between 10pm–2am supports Growth Hormone release for healthy height.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                item {
                    val nextAge = ageMonthsNow + 3
                    val nextBand = GrowthReference.band(GrowthReference.monthsToDays(nextAge), isWeight = false, gender)
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Primary)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (isThai) "เป้าหมายถัดไป" else "Next milestone",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                (if (isThai) "อายุ $nextAge เดือน" else "At $nextAge months") + ": " +
                                    (if (isThai) "ช่วงปกติ WHO " else "WHO normal range ") +
                                    (nextBand?.let { "%.1f-%.1f $unit".format(it.first, it.second) } ?: "-"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(0.9f)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun BoneTipRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Verified, null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
    }
}

@Composable
private fun WhoAiRecommendationCard(
    insight: String?,
    isLoading: Boolean,
    isWeight: Boolean,
    latestValue: Double?,
    unit: String,
    status: GrowthReference.GrowthStatus?,
    ageMonths: Int,
    isThai: Boolean
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(
                    if (isThai) "คำแนะนำจาก AI" else "AI recommendations",
                    style = MaterialTheme.typography.titleSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
            } else {
                val fallback = buildWhoFallbackNarrative(isWeight, latestValue, unit, status, isThai)
                Text(
                    insight?.takeIf { it.isNotBlank() } ?: fallback,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Lightbulb, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Text(
                            if (isThai) "ข้อเสนอแนะสำหรับช่วงวัยนี้:" else "Tips for this age:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    whoAgeTips(isWeight, ageMonths, isThai).forEach { tip ->
                        Text("• $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun buildWhoFallbackNarrative(
    isWeight: Boolean,
    latestValue: Double?,
    unit: String,
    status: GrowthReference.GrowthStatus?,
    isThai: Boolean
): String {
    val valuePart = latestValue?.let { "$it $unit" } ?: (if (isThai) "ยังไม่มีค่าล่าสุด" else "no latest reading")
    val statusPart = status?.let { if (isThai) it.labelTh else it.labelEn }
        ?: (if (isThai) "ยังประเมินไม่ได้" else "not yet assessed")
    return if (isWeight) {
        if (isThai) "ปัจจุบันน้ำหนักอยู่ที่ $valuePart ซึ่งประเมินว่า$statusPart ตามช่วงอ้างอิงตามวัย ติดตามต่อเนื่องเพื่อรักษาแนวโน้มการเติบโตให้สม่ำเสมอ"
        else "Current weight is $valuePart — assessed as $statusPart against the age reference. Keep logging to maintain a steady growth trend."
    } else {
        if (isThai) "ปัจจุบันส่วนสูงอยู่ที่ $valuePart ซึ่งประเมินว่า$statusPart ตามช่วงอ้างอิงตามวัย นอนหลับและโภชนาการช่วยสนับสนุนการเติบโตด้านความสูง"
        else "Current height is $valuePart — assessed as $statusPart against the age reference. Sleep and nutrition support height growth."
    }
}

private fun whoAgeTips(isWeight: Boolean, ageMonths: Int, isThai: Boolean): List<String> = if (isWeight) {
    when {
        ageMonths < 6 -> listOf(
            if (isThai) "ให้นมแม่อย่างเพียงพอตามสัญญาณหิวของลูก" else "Feed on hunger cues — breast milk / formula as advised",
            if (isThai) "ชั่งน้ำหนักสม่ำเสมอทุก 2–4 สัปดาห์" else "Weigh regularly every 2–4 weeks",
            if (isThai) "พบแพทย์ตามนัดตรวจสุขภาพทารก" else "Keep well-baby checkups on schedule"
        )
        ageMonths < 12 -> listOf(
            if (isThai) "ส่งเสริมอาหารเสริมที่มีธาตุเหล็กและโปรตีน" else "Offer iron- and protein-rich complementary foods",
            if (isThai) "รักษาวินัยการนอนเพื่อสนับสนุน Growth Hormone" else "Keep a steady sleep routine to support Growth Hormone",
            if (isThai) "นัดตรวจสุขภาพและวัคซีนตามรอบ" else "Keep health checks and vaccines on schedule"
        )
        else -> listOf(
            if (isThai) "ส่งเสริมอาหารที่มีธาตุเหล็กและแคลเซียมสูง" else "Promote iron- and calcium-rich foods",
            if (isThai) "รักษาวินัยการนอนหลับ เพื่อสนับสนุน Growth Hormone" else "Maintain sleep discipline to support Growth Hormone",
            if (isThai) "นัดหมายตรวจสุขภาพตามรอบอายุ" else "Schedule age-appropriate health checkups"
        )
    }
} else {
    listOf(
        if (isThai) "ท่าบริหารยืดเหยียดเบาๆ สำหรับวัยนี้" else "Gentle stretch / play moves for this age",
        if (isThai) "เมนูแคลเซียมและวิตามินดีที่เด็กชอบ" else "Calcium- and vitamin-D-friendly meals kids enjoy",
        if (isThai) "นอนหลับสนิทช่วงดึกเพื่อการหลั่ง Growth Hormone" else "Prioritize deep nighttime sleep for Growth Hormone"
    )
}

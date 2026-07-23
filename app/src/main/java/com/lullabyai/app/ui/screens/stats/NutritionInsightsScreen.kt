@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.NutritionViewModel

/** Detailed nutrition stats page — matches Child-Project/Update/29.stats_nutrition. Reached from the
 *  Nutrition hub's "ดูรายละเอียดทั้งหมด" button; replaces the old 7-day-chart version of this screen. */
@Composable
fun NutritionInsightsScreen(
    nutritionViewModel: NutritionViewModel,
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val lang = if (isThai) "th" else "en"
    val ui by nutritionViewModel.ui.collectAsState()
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = state.child ?: ui.child

    LaunchedEffect(ui.child?.id, lang) { nutritionViewModel.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { nutritionViewModel.loadMacros(lang) }
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { nutritionViewModel.loadMicronutrients(lang) }

    val todayMilkMl = ui.todayMilkMl
    val milkTargetMl = 850

    LaunchedEffect(child?.id, ui.foods) {
        child?.let {
            if (ui.foods.isNotEmpty()) {
                val intake = ui.summary?.intakeKcal ?: 0
                val summary = (if (isThai) "แคลอรี่วันนี้: " else "Today's calories: ") + "$intake / ${ui.targetKcal} kcal, " +
                    (if (isThai) "นมวันนี้: " else "Milk today: ") + "$todayMilkMl ml"
                chatViewModel.loadNutritionInsight(it, summary, isThai)
            }
        }
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "สรุปโภชนาการแบบละเอียด" else "Detailed Nutrition Summary", onBack = { navController.popBackStack() }) },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.STATS,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                val intake = ui.summary?.intakeKcal ?: 0
                val target = ui.targetKcal.coerceAtLeast(1)
                val rawPct = intake.toFloat() / target   // UNCLAMPED — status must react past 100%, not just up to it
                val pct = rawPct.coerceIn(0f, 1f)          // clamped only for the ring/bar drawing below
                val remaining = (target - intake).coerceAtLeast(0)
                // Absolute hard cap regardless of the child's target: intake this high is a logging
                // error or genuinely abnormal either way, and a ratio-only check would hide it once
                // clamped to the ring's 0-100% range (a 29,940 kcal day used to still read "balanced").
                val abnormal = intake > 2000 || rawPct > 1.5f
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column {
                                Text(if (isThai) "เป้าหมายพลังงานวันนี้" else "Today's energy goal", style = MaterialTheme.typography.labelSmall, color = Outline)
                                val statusColor = when {
                                    abnormal -> Color(0xFFC62828)
                                    rawPct >= 0.5f -> Color(0xFF2E7D32)
                                    else -> Color(0xFFEF6C00)
                                }
                                val statusText = when {
                                    abnormal -> if (isThai) "ผิดปกติ" else "Abnormal"
                                    rawPct >= 0.5f -> if (isThai) "สมดุลดี" else "Good Balance"
                                    else -> if (isThai) "ยังไม่ถึงเป้า" else "Below target"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                                    Text(statusText, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                                if (abnormal) {
                                    Text(
                                        if (isThai) "แคลอรี่สูงผิดปกติ ($intake kcal) โปรดตรวจสอบรายการที่บันทึก" else "Unusually high ($intake kcal) — please check your logged entries",
                                        color = Color(0xFFC62828), style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            if (child != null) {
                                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.12f)) {
                                    Text(child.name, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Box(Modifier.size(180.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                drawArc(color = SurfaceContainer, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke, size = Size(size.width, size.height))
                                drawArc(color = Primary, startAngle = -90f, sweepAngle = 360f * pct, useCenter = false, style = stroke, size = Size(size.width, size.height))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$intake", style = MaterialTheme.typography.headlineLarge, color = Primary, fontWeight = FontWeight.Bold)
                                Text("/ ${ui.targetKcal} kcal", style = MaterialTheme.typography.bodySmall, color = Outline)
                            }
                        }
                        if (intake > target) {
                            val over = intake - target
                            Text(
                                if (isThai) "เกินเป้าหมายไป $over kcal" else "$over kcal over today's goal",
                                style = MaterialTheme.typography.bodySmall, color = if (abnormal) Color(0xFFC62828) else Outline
                            )
                        } else {
                            Text(
                                if (isThai) "อีก $remaining kcal เพื่อให้ครบตามเป้าหมายของวันนี้" else "$remaining kcal left to reach today's goal",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isThai) "สารอาหารหลัก (Macronutrients)" else "Macronutrients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (ui.macros == null) {
                        Text(if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้" else "No food logged today yet", style = MaterialTheme.typography.bodySmall, color = Outline)
                    } else {
                        val m = ui.macros!!
                        val (targetCarb, targetProtein, targetFat) = ui.targetMacros
                        MacroBarCard(Icons.Filled.Egg, if (isThai) "โปรตีน (Protein)" else "Protein", m.protein, targetProtein, "g")
                        MacroBarCard(Icons.Filled.BakeryDining, if (isThai) "คาร์โบไฮเดรต (Carbs)" else "Carbs", m.carb, targetCarb, "g")
                        MacroBarCard(Icons.Filled.WaterDrop, if (isThai) "ไขมัน (Fats)" else "Fats", m.fat, targetFat, "g", warn = true)
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFBBDEFB)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Opacity, null, tint = Color(0xFF1976D2))
                            }
                            Column {
                                Text(if (isThai) "การดื่มน้ำและนม" else "Water & milk intake", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (isThai) "ดื่มไปแล้ว $todayMilkMl / $milkTargetMl มล." else "Drank $todayMilkMl / $milkTargetMl ml so far",
                                    style = MaterialTheme.typography.bodySmall, color = Outline
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SmartToy, null, tint = Primary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isThai) "การวิเคราะห์จาก AI Nutri-Bot" else "AI Nutri-Bot analysis", fontWeight = FontWeight.Bold, color = Primary)
                            if (ui.foods.isEmpty()) {
                                Text(if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้ บันทึกเพิ่มเพื่อรับคำแนะนำ" else "No food logged today — log some to get advice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (chatState.nutritionInsight != null) {
                                Text(chatState.nutritionInsight!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (isThai) "วิตามินและแร่ธาตุแบบละเอียด" else "Vitamins & minerals in detail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isThai) "ข้อมูลสรุปการได้รับสารอาหารรองทั้งหมดของวันนี้" else "A summary of today's micronutrient intake",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val m = ui.macros
                    if (m != null) {
                        val caOk = m.calciumMg >= ui.targetCalciumMg.coerceAtLeast(1) * 0.9f
                        val feOk = m.ironMg >= ui.targetIronMg.coerceAtLeast(1) * 0.9f
                        MicroRow("Ca", Color(0xFFBBDEFB), Color(0xFF1976D2), if (isThai) "แคลเซียม" else "Calcium", if (isThai) "เสริมสร้างฟันและกระดูกให้แข็งแรงตามวัย" else "Builds strong teeth and bones", if (caOk) MicroStatus.OK else MicroStatus.LOW, isThai)
                        MicroRow("Fe", Color(0xFFFFCDD2), Color(0xFFC62828), if (isThai) "ธาตุเหล็ก" else "Iron", if (isThai) "สำคัญต่อพัฒนาการสมองและการสร้างเลือด" else "Important for brain development and blood cells", if (feOk) MicroStatus.OK else MicroStatus.LOW, isThai)
                    }
                    if (ui.microStatusLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                    }
                    ui.microStatus?.let { status ->
                        val rows = listOf(
                            Quad("A", Color(0xFFFFE0B2), Color(0xFFE65100), if (isThai) "วิตามิน เอ" else "Vitamin A") to (if (isThai) "บำรุงสายตาและเสริมสร้างเยื่อบุให้แข็งแรง" else "Supports eyesight and healthy tissue"),
                            Quad("D", Color(0xFFFFF9C4), Color(0xFFF9A825), if (isThai) "วิตามิน ดี" else "Vitamin D") to (if (isThai) "จำเป็นต่อการดูดซึมแคลเซียมและกระดูก" else "Needed for calcium absorption and bones"),
                            Quad("C", Color(0xFFC8E6C9), Color(0xFF2E7D32), if (isThai) "วิตามิน ซี" else "Vitamin C") to (if (isThai) "เสริมสร้างภูมิคุ้มกันและช่วยดูดซึมธาตุเหล็ก" else "Boosts immunity and iron absorption"),
                            Quad("B12", Color(0xFFE1BEE7), Color(0xFF6A1B9A), if (isThai) "วิตามิน บี 12" else "Vitamin B12") to (if (isThai) "บำรุงระบบประสาทและสร้างเม็ดเลือดแดง" else "Supports the nervous system and red blood cells"),
                            Quad("Zn", Color(0xFFCFD8DC), Color(0xFF455A64), if (isThai) "สังกะสี (Zinc)" else "Zinc") to (if (isThai) "ช่วยในการเจริญเติบโตและการสมานแผล" else "Supports growth and wound healing"),
                            Quad("DHA", Color(0xFFC5CAE9), Color(0xFF283593), if (isThai) "DHA/โอเมก้า-3" else "DHA/Omega-3") to (if (isThai) "บำรุงเซลล์สมองและจอประสาทตา" else "Supports brain cells and retina development")
                        )
                        rows.forEach { (q, desc) ->
                            val st = when (status[q.key]) { "boost" -> MicroStatus.BOOST; "low" -> MicroStatus.LOW; else -> MicroStatus.OK }
                            MicroRow(q.key, q.bg, q.fg, q.label, desc, st, isThai)
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.Nutrition.route) { launchSingleTop = true } },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.RestaurantMenu, null); Spacer(Modifier.width(10.dp))
                    Text(if (isThai) "แนะนำเมนูอาหารที่เหมาะสม" else "Recommend a suitable menu", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private data class Quad(val key: String, val bg: Color, val fg: Color, val label: String)

private enum class MicroStatus { OK, BOOST, LOW }

@Composable
private fun MacroBarCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, current: Int, target: Int, unit: String, warn: Boolean = false) {
    val safeTarget = target.coerceAtLeast(1)
    val pct = (current.toFloat() / safeTarget).coerceIn(0f, 1f)
    val barColor = if (warn) Color(0xFFEF6C00) else Primary
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null, tint = barColor, modifier = Modifier.size(18.dp))
                    Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                Text("$current$unit / $target$unit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = barColor)
            }
            LinearProgressIndicator(
                progress = { pct }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(100.dp)),
                color = barColor, trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun MicroRow(shortLabel: String, bg: Color, fg: Color, name: String, desc: String, status: MicroStatus, isThai: Boolean) {
    val (statusText, statusColor) = when (status) {
        MicroStatus.OK -> (if (isThai) "เพียงพอ" else "Sufficient") to Color(0xFF2E7D32)
        MicroStatus.BOOST -> (if (isThai) "ควรเสริม" else "Could boost") to Color(0xFFEF6C00)
        MicroStatus.LOW -> (if (isThai) "ต่ำ" else "Low") to Color(0xFFC62828)
    }
    Card(
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Text(shortLabel, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(0.12f)) {
                        Text(statusText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(desc, style = MaterialTheme.typography.labelSmall, color = Outline)
            }
        }
    }
}

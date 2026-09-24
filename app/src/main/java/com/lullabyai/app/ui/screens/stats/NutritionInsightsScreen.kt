@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.DayNutrition
import com.lullabyai.app.util.DayNutrition.Status
import com.lullabyai.app.util.DriReference
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.NutritionViewModel
import kotlin.math.roundToInt

/**
 * สถิติ → โภชนาการ (Stitch "Full Comprehensive Nutrition Summary").
 *
 * Every number is computed, not guessed: targets from Thai DRI 2563 ([DriReference]); intake from
 * ตารางแสดงคุณค่าทางโภชนาการของอาหารไทย ([com.lullabyai.app.util.ThaiFoodTable]), with an AI
 * estimate only for foods the table lacks. The source card at the bottom says which was which.
 * Vitamin D, B12 and DHA are not in the table — those three stay AI-judged and are labelled so.
 */
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
    val dri = ui.dri
    val totals = ui.dayTotals

    LaunchedEffect(ui.child?.id, lang) { nutritionViewModel.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilk, lang) { nutritionViewModel.loadDayNutrition(lang) }
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { nutritionViewModel.loadMicronutrients(lang) }

    // Energy: table/AI-weighted portions, plus the logged kcal of any food we could not weigh — its
    // energy is known even when its nutrients are not.
    val intake = nutritionViewModel.dayIntakeKcal() ?: ui.foods.sumOf { it.calories ?: 0 }
    val target = dri?.energyKcal ?: ui.targetKcal

    val micro = remember(totals, dri, isThai) { microRows(totals, dri, isThai) }
    val tips = remember(micro, totals, dri, isThai) { tipsFor(micro, totals, dri, isThai) }

    LaunchedEffect(child?.id, totals) {
        val c = child ?: return@LaunchedEffect
        if (totals == null) return@LaunchedEffect
        val lows = micro.filter { it.status == Status.LOW || it.status == Status.FAIRLY_LOW }.joinToString { it.name }
        val summary = (if (isThai) "พลังงานวันนี้ $intake / $target kcal" else "Energy today $intake / $target kcal") +
            (if (lows.isNotBlank()) (if (isThai) "; ยังต่ำ: $lows" else "; still low: $lows") else "")
        chatViewModel.loadNutritionInsight(c, summary, isThai)
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "สรุปโภชนาการแบบละเอียด" else "Detailed Nutrition Summary", onBack = { navController.popBackStack() }, navController = navController) },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
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
            item { EnergyCard(intake, target, child?.name, isThai) }

            if (dri?.breastMilkCovers == true) {
                item {
                    NoteCard(
                        Icons.Filled.ChildCare,
                        if (isThai) "ช่วง 0-5 เดือน นมแม่ให้สารอาหารครบตามปริมาณอ้างอิง (DRI ไทย 2563) ยังไม่ต้องนับวิตามินและแร่ธาตุรายตัว"
                        else "0-5 months: breast milk covers the reference intakes (Thai DRI 2020); no need to track each vitamin yet."
                    )
                }
            }

            if (ui.dayTotalsLoading && totals == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isThai) "สารอาหารหลัก (Macronutrients)" else "Macronutrients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (totals == null) {
                        Text(if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้" else "No food logged today yet", style = MaterialTheme.typography.bodySmall, color = Outline)
                    } else {
                        val g = if (isThai) "ก." else "g"
                        MacroBar(Icons.Filled.Egg, if (isThai) "โปรตีน (Protein)" else "Protein",
                            totals.amount["protein"] ?: 0.0, dri?.proteinG?.let { it..it }, g, totals.missing["protein"] ?: 0, isThai)
                        MacroBar(Icons.Filled.BakeryDining, if (isThai) "คาร์โบไฮเดรต (Carbs)" else "Carbs",
                            totals.amount["carb"] ?: 0.0, dri?.carbG, g, totals.missing["carb"] ?: 0, isThai)
                        MacroBar(Icons.Filled.WaterDrop, if (isThai) "ไขมัน (Fats)" else "Fats",
                            totals.amount["fat"] ?: 0.0, dri?.fatG, g, totals.missing["fat"] ?: 0, isThai, warn = true)
                    }
                }
            }

            item { NutriBotCard(chatState.nutritionInsight, ui.foods.isEmpty() && ui.todayMilk.isEmpty(), tips, isThai) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (isThai) "วิตามินและแร่ธาตุแบบละเอียด" else "Vitamins & minerals in detail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isThai) "เทียบกับปริมาณอ้างอิงของ${dri?.labelTh ?: "เด็ก"} (DRI ไทย 2563)"
                        else "Compared with the reference for ${dri?.labelEn ?: "children"} (Thai DRI 2020)",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                }
            }
            if (totals != null && dri?.breastMilkCovers != true) {
                items(micro, key = { it.key }) { MicroRow(it, isThai) }
            }
            if (dri?.breastMilkCovers != true) {
                val status = ui.microStatus
                if (status != null) {
                    items(aiOnlyRows(status, isThai), key = { it.key }) { MicroRow(it, isThai) }
                } else if (ui.microStatusLoading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary) }
                }
            }

            if (totals != null) item { SourceCard(totals, dri, isThai) }

            item {
                Button(
                    onClick = { navController.navigate(Screen.Nutrition.route) { launchSingleTop = true } },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.RestaurantMenu, null); Spacer(Modifier.width(10.dp))
                    Text(if (isThai) "แนะนำเมนูอาหารที่เหมาะสม" else "Recommend a suitable menu", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ---------------------------------------------------------------- rows & rules

private data class MicroItem(
    val key: String,
    val badge: String,
    val bg: Color,
    val fg: Color,
    val name: String,
    val desc: String,
    val status: Status,
    /** "3.2 / 5 มก." — null for the AI-judged rows, which have no number. */
    val amountLine: String?,
    val aiJudged: Boolean = false
)

private data class Spec(val key: String, val badge: String, val bg: Long, val fg: Long, val th: String, val en: String, val descTh: String, val descEn: String, val unitTh: String, val unitEn: String)

private val SPECS = listOf(
    Spec("vitaminA", "A", 0xFFFFE0B2, 0xFFE65100, "วิตามิน เอ", "Vitamin A", "บำรุงสายตาและเสริมสร้างเยื่อบุให้แข็งแรง", "Supports eyesight and healthy tissue", "มคก.", "mcg"),
    Spec("vitaminC", "C", 0xFFC8E6C9, 0xFF2E7D32, "วิตามิน ซี", "Vitamin C", "เสริมสร้างภูมิคุ้มกันและช่วยดูดซึมธาตุเหล็ก", "Boosts immunity and iron absorption", "มก.", "mg"),
    Spec("calcium", "Ca", 0xFFBBDEFB, 0xFF1976D2, "แคลเซียม", "Calcium", "เสริมสร้างฟันและกระดูกให้แข็งแรงตามวัย", "Builds strong teeth and bones", "มก.", "mg"),
    Spec("iron", "Fe", 0xFFFFCDD2, 0xFFC62828, "ธาตุเหล็ก", "Iron", "สำคัญต่อพัฒนาการสมองและการสร้างเลือด", "Important for brain development and blood", "มก.", "mg"),
    Spec("zinc", "Zn", 0xFFCFD8DC, 0xFF455A64, "สังกะสี (Zinc)", "Zinc", "ช่วยในการเจริญเติบโตและการสมานแผล", "Supports growth and wound healing", "มก.", "mg"),
    Spec("thiamin", "B1", 0xFFE1F5FE, 0xFF0277BD, "วิตามิน บี 1", "Vitamin B1", "ช่วยเปลี่ยนอาหารเป็นพลังงาน", "Turns food into energy", "มก.", "mg"),
    Spec("riboflavin", "B2", 0xFFFFF3E0, 0xFFEF6C00, "วิตามิน บี 2", "Vitamin B2", "บำรุงผิว ปาก และการเจริญเติบโต", "Skin, mouth and growth", "มก.", "mg"),
    Spec("sodium", "Na", 0xFFF5F5F5, 0xFF616161, "โซเดียม", "Sodium", "ไม่ควรเกินช่วงแนะนำ ระวังเครื่องปรุงรส", "Keep within range — watch seasoning", "มก.", "mg")
)

private fun fmt(v: Double): String = when {
    v >= 100 -> v.roundToInt().toString()
    v >= 10 -> "%.0f".format(v)
    else -> "%.1f".format(v)
}

private fun microRows(totals: DayNutrition.Totals?, dri: DriReference.Targets?, isThai: Boolean): List<MicroItem> {
    if (totals == null || dri == null) return emptyList()
    return SPECS.mapNotNull { sp ->
        val amount = totals.amount[sp.key] ?: 0.0
        val missing = totals.missing[sp.key] ?: 0
        val unit = if (isThai) sp.unitTh else sp.unitEn
        val range = dri.ranges[sp.key]
        val value = dri.values[sp.key]
        val (status, line) = when {
            range != null -> DayNutrition.rangeStatus(amount, range, missing) to "${fmt(amount)} / ${fmt(range.start)}-${fmt(range.endInclusive)} $unit"
            value != null && value > 0 -> DayNutrition.status(amount, value, missing) to "${fmt(amount)} / ${fmt(value)} $unit"
            else -> return@mapNotNull null
        }
        MicroItem(
            sp.key, sp.badge, Color(sp.bg), Color(sp.fg), if (isThai) sp.th else sp.en, if (isThai) sp.descTh else sp.descEn,
            status, line + if (missing > 0 && status != Status.ENOUGH) (if (isThai) " (บางรายการไม่มีข้อมูล)" else " (some foods lack data)") else ""
        )
    }
}

private data class AiSpec(val key: String, val bg: Long, val fg: Long, val th: String, val en: String, val descTh: String, val descEn: String)

private val AI_SPECS = listOf(
    AiSpec("D", 0xFFFFF9C4, 0xFFF9A825, "วิตามิน ดี", "Vitamin D", "จำเป็นต่อการดูดซึมแคลเซียมและกระดูก", "Needed for calcium absorption and bones"),
    AiSpec("B12", 0xFFE1BEE7, 0xFF6A1B9A, "วิตามิน บี 12", "Vitamin B12", "บำรุงระบบประสาทและสร้างเม็ดเลือดแดง", "Nervous system and red blood cells"),
    AiSpec("DHA", 0xFFC5CAE9, 0xFF283593, "DHA/โอเมก้า-3", "DHA/Omega-3", "บำรุงเซลล์สมองและจอประสาทตา", "Brain cells and retina")
)

/** Vitamin D, B12 and DHA: not in the Thai table, so the AI's qualitative read, marked as such. */
private fun aiOnlyRows(status: Map<String, String>, isThai: Boolean): List<MicroItem> = AI_SPECS.mapNotNull { a ->
    val st = when (status[a.key]) { "ok" -> Status.ENOUGH; "boost" -> Status.FAIRLY_LOW; "low" -> Status.LOW; else -> return@mapNotNull null }
    MicroItem("ai_${a.key}", a.key, Color(a.bg), Color(a.fg), if (isThai) a.th else a.en, if (isThai) a.descTh else a.descEn, st, null, aiJudged = true)
}

private data class Tip(val title: String, val body: String, val icon: ImageVector)

/** Rule-based tips from the computed statuses — no extra AI call, and they always match the rows. */
private fun tipsFor(micro: List<MicroItem>, totals: DayNutrition.Totals?, dri: DriReference.Targets?, isThai: Boolean): List<Tip> {
    if (totals == null || dri == null || dri.breastMilkCovers) return emptyList()
    fun low(k: String) = micro.firstOrNull { it.key == k }?.status.let { it == Status.LOW || it == Status.FAIRLY_LOW }
    val pairing = when {
        low("iron") -> if (isThai) "เพิ่ม ตับ ไข่แดง หรือ เนื้อสัตว์ ในมื้อถัดไป และกินคู่กับผลไม้วิตามินซีสูง เช่น ฝรั่ง ส้ม เพื่อช่วยดูดซึมธาตุเหล็ก"
            else "Add liver, egg yolk or meat next meal, with vitamin-C fruit like guava or orange to help absorb the iron."
        low("calcium") -> if (isThai) "เพิ่ม นม โยเกิร์ต เต้าหู้ หรือปลาตัวเล็ก เพื่อเสริมแคลเซียม" else "Add milk, yoghurt, tofu or small fish for calcium."
        low("vitaminA") -> if (isThai) "เพิ่มผักผลไม้สีส้ม เช่น ฟักทอง แครอท มะละกอสุก หรือไข่แดง" else "Add orange foods like pumpkin, carrot, ripe papaya, or egg yolk."
        low("vitaminC") -> if (isThai) "เพิ่มผลไม้ เช่น ฝรั่ง ส้ม มะละกอ ในมื้อว่าง" else "Add fruit like guava, orange or papaya as a snack."
        low("zinc") -> if (isThai) "เพิ่มเนื้อสัตว์ ไข่ หรือถั่ว เพื่อเสริมสังกะสี" else "Add meat, egg or beans for zinc."
        else -> if (isThai) "วันนี้สมดุลดี ลองเพิ่มผักหลากสีในมื้อถัดไป" else "Well balanced today — try more coloured vegetables next meal."
    }
    val sodium = micro.firstOrNull { it.key == "sodium" }?.status == Status.TOO_MUCH
    val lifestyle = if (sodium) {
        if (isThai) "วันนี้โซเดียมเกินช่วงแนะนำ ลดน้ำปลา ซีอิ๊ว และขนมกรุบกรอบในมื้อที่เหลือ" else "Sodium is over the range today — ease off fish sauce, soy sauce and salty snacks."
    } else {
        if (isThai) "พาน้องออกไปรับแสงแดดอ่อนช่วงเช้า 10-15 นาที เพื่อช่วยให้ร่างกายสร้างวิตามินดีตามธรรมชาติ"
        else "10-15 minutes of gentle morning sun helps the body make vitamin D naturally."
    }
    return listOf(
        Tip("FOOD PAIRING TIP", pairing, Icons.Filled.Lightbulb),
        Tip("LIFESTYLE TIP", lifestyle, Icons.Filled.WbSunny)
    )
}

// ---------------------------------------------------------------- composables

@Composable
private fun EnergyCard(intake: Int, target: Int, childName: String?, isThai: Boolean) {
    val safeTarget = target.coerceAtLeast(1)
    val rawPct = intake.toFloat() / safeTarget          // unclamped: status must react past 100 %
    val pct = rawPct.coerceIn(0f, 1f)                     // clamped only for drawing
    // An absolute cap as well as the ratio: a mistyped 29,940 kcal day must never read "balanced".
    val abnormal = intake > 2500 || rawPct > 1.5f
    val (statusText, statusColor) = when {
        abnormal -> (if (isThai) "ผิดปกติ" else "Abnormal") to Color(0xFFC62828)
        rawPct > 1.1f -> (if (isThai) "เกินเป้าหมาย" else "Over target") to Color(0xFFEF6C00)
        rawPct >= 0.5f -> (if (isThai) "สมดุลดี (Good Balance)" else "Good Balance") to Color(0xFF2E7D32)
        else -> (if (isThai) "ยังไม่ถึงเป้า" else "Below target") to Color(0xFFEF6C00)
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(if (isThai) "เป้าหมายพลังงานวันนี้" else "Today's energy goal", style = MaterialTheme.typography.labelSmall, color = Outline)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Text(statusText, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (childName != null) {
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.12f)) {
                        Text(childName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
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
                    Text("%,d".format(intake), style = MaterialTheme.typography.headlineLarge, color = Primary, fontWeight = FontWeight.Bold)
                    Text("/ ${"%,d".format(target)} kcal", style = MaterialTheme.typography.bodySmall, color = Outline)
                }
            }
            val remaining = target - intake
            Text(
                if (remaining > 0) {
                    if (isThai) "อีก ${"%,d".format(remaining)} kcal เพื่อให้ครบตามเป้าหมายของวันนี้" else "${"%,d".format(remaining)} kcal left to reach today's goal"
                } else {
                    if (isThai) "เกินเป้าหมายไป ${"%,d".format(-remaining)} kcal" else "${"%,d".format(-remaining)} kcal over today's goal"
                },
                style = MaterialTheme.typography.bodySmall, color = if (abnormal) Color(0xFFC62828) else Outline
            )
        }
    }
}

@Composable
private fun MacroBar(
    icon: ImageVector, label: String, amount: Double, target: ClosedFloatingPointRange<Double>?,
    unit: String, missing: Int, isThai: Boolean, warn: Boolean = false
) {
    val barColor = if (warn) Color(0xFFEF6C00) else Primary
    val top = target?.endInclusive ?: 0.0
    val pct = if (top > 0) (amount / top).toFloat().coerceIn(0f, 1f) else 0f
    val targetText = when {
        target == null -> "-"
        target.start == target.endInclusive -> "${fmt(target.start)}$unit"
        else -> "${fmt(target.start)}-${fmt(target.endInclusive)}$unit"
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null, tint = barColor, modifier = Modifier.size(18.dp))
                    Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                Text("${fmt(amount)}$unit / $targetText", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = barColor)
            }
            LinearProgressIndicator(
                progress = { pct }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(100.dp)),
                color = barColor, trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (missing > 0) {
                Text(
                    if (isThai) "* $missing รายการไม่มีข้อมูลนี้ ตัวเลขอาจน้อยกว่าจริง" else "* $missing item(s) had no figure — may be an undercount",
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
            }
        }
    }
}

@Composable
private fun NutriBotCard(insight: String?, nothingLogged: Boolean, tips: List<Tip>, isThai: Boolean) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SmartToy, null, tint = Primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isThai) "การวิเคราะห์จาก AI Nutri-Bot" else "AI Nutri-Bot analysis", fontWeight = FontWeight.Bold, color = Primary)
                    when {
                        nothingLogged -> Text(if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้ บันทึกเพิ่มเพื่อรับคำแนะนำ" else "No food logged today — log some to get advice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        insight != null -> Text(insight, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        else -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                    }
                }
            }
            tips.forEach { t ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(t.icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Column {
                            Text(t.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
                            Text(t.body, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicroRow(m: MicroItem, isThai: Boolean) {
    val (statusText, statusColor) = when (m.status) {
        Status.ENOUGH -> (if (isThai) "เพียงพอ" else "Sufficient") to Color(0xFF2E7D32)
        Status.FAIRLY_LOW -> (if (m.aiJudged) (if (isThai) "ควรเสริม" else "Could boost") else (if (isThai) "ค่อนข้างต่ำ" else "Fairly low")) to Color(0xFFEF6C00)
        Status.LOW -> (if (isThai) "ต่ำ" else "Low") to Color(0xFFC62828)
        Status.TOO_MUCH -> (if (isThai) "เกิน" else "Too much") to Color(0xFFC62828)
        Status.INCOMPLETE -> (if (isThai) "ข้อมูลไม่ครบ" else "Incomplete data") to Color(0xFF757575)
    }
    val border = when (m.status) {
        Status.LOW, Status.TOO_MUCH -> BorderStroke(1.5.dp, Color(0xFFC62828).copy(0.7f))
        Status.FAIRLY_LOW -> BorderStroke(1.5.dp, Color(0xFFEF6C00).copy(0.7f))
        else -> null
    }
    Card(
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp), border = border
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(m.bg), contentAlignment = Alignment.Center) {
                Text(m.badge, color = m.fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(0.12f)) {
                        Text(statusText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(m.desc, style = MaterialTheme.typography.labelSmall, color = Outline)
                Text(
                    m.amountLine ?: (if (isThai) "ไม่มีในตารางกรมอนามัย · ประเมินโดย AI" else "Not in the Thai table · AI estimate"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (m.amountLine != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (m.amountLine != null) MaterialTheme.colorScheme.onSurface else Color(0xFF757575)
                )
            }
        }
    }
}

@Composable
private fun SourceCard(totals: DayNutrition.Totals, dri: DriReference.Targets?, isThai: Boolean) {
    var open by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = Outline, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "ที่มาของข้อมูล" else "Where these numbers come from", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Outline)
            }
            val uncounted = totals.uncounted.size
            Text(
                if (isThai) "ตารางคุณค่าทางโภชนาการอาหารไทย (กรมอนามัย): ${totals.tableCount} รายการ · ประเมินโดย AI: ${totals.aiCount} รายการ" +
                    (if (uncounted > 0) " · ไม่ได้นับ: $uncounted" else "")
                else "Thai food composition table: ${totals.tableCount} · AI estimate: ${totals.aiCount}" +
                    (if (uncounted > 0) " · not counted: $uncounted" else ""),
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
            if (dri != null) {
                Text(
                    if (isThai) "เป้าหมาย: ปริมาณสารอาหารอ้างอิงสำหรับคนไทย พ.ศ. 2563 (${dri.labelTh})" else "Targets: Thai Dietary Reference Intake 2020 (${dri.labelEn})",
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
            }
            if (open) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                totals.portions.forEach { p ->
                    val (tag, color) = when (p.source) {
                        DayNutrition.Source.TABLE -> (if (isThai) "ตาราง" else "Table") to Color(0xFF2E7D32)
                        DayNutrition.Source.AI -> "AI" to Color(0xFF1976D2)
                        DayNutrition.Source.NO_PORTION -> (if (isThai) "ไม่ทราบปริมาณ" else "No portion") to Color(0xFF757575)
                        DayNutrition.Source.NO_DATA -> (if (isThai) "ไม่มีข้อมูล" else "No data") to Color(0xFF757575)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.12f)) {
                            Text(tag, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = MaterialTheme.typography.bodySmall)
                            val detail = listOfNotNull(
                                p.matchedName?.takeIf { it != p.label }?.let { if (isThai) "เทียบกับ: $it" else "matched: $it" },
                                p.grams?.let { "≈ ${it.roundToInt()} " + if (isThai) "ก." else "g" }
                            ).joinToString(" · ")
                            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = Outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(icon: ImageVector, text: String) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Primary)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

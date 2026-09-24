@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.screens.child.HubChildAvatar
import com.lullabyai.app.ui.screens.child.hubAgeLabel
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.DayNutrition
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.time.LocalTime

private val Mint = Color(0xFF5EEAD4)
private val Good = Color(0xFF00796B)
private val Watch = Color(0xFFEF6C00)

/**
 * Nutrition hub — Stitch "โภชนาการและการเจริญเติบโต (Nutrition & Growth Guardian)". Same numbers as
 * สถิติ → โภชนาการ: targets from Thai DRI 2563, intake from the กรมอนามัย food table (AI only for foods
 * the table lacks). The obesity card adds WHO BMI-for-age from the profile weight and height.
 */
@Composable
fun NutritionScreen(vm: NutritionViewModel, lang: String, navController: NavController) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(lang) { vm.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilk, lang) { vm.loadDayNutrition(lang) }
    LaunchedEffect(ui.child?.id, lang, ui.dayTotals) { vm.ensureMenuRecommendations(lang) }

    val child = ui.child
    val dri = ui.dri
    val months = vm.ageMonths()
    val totals = ui.dayTotals
    val nothingLogged = ui.foods.isEmpty() && ui.todayMilk.isEmpty()
    val intake = vm.dayIntakeKcal() ?: ui.foods.sumOf { it.calories ?: 0 }
    val targetKcal = dri?.energyKcal ?: ui.targetKcal
    val energyShare = if (targetKcal > 0 && !nothingLogged) intake.toDouble() / targetKcal else null
    val (targetCarb, targetProtein, _) = ui.targetMacros
    val pillars = listOf(
        Pillar(if (isThai) "โปรตีน (Protein)" else "Protein", Icons.Filled.Egg, "protein", targetProtein, "g", Primary),
        Pillar(if (isThai) "คาร์โบไฮเดรต (Carbs)" else "Carbs", Icons.Filled.BakeryDining, "carb", targetCarb, "g", PrimaryContainer),
        Pillar(if (isThai) "แคลเซียม (Calcium)" else "Calcium", Icons.Filled.LocalDrink, "calcium", ui.targetCalciumMg, "mg", Good),
        Pillar(if (isThai) "ธาตุเหล็ก (Iron)" else "Iron", Icons.Filled.Bloodtype, "iron", ui.targetIronMg, "mg", SurfaceTint)
    )
    val overall = if (nothingLogged) null
        else DayNutrition.overallShare(pillars.map { (totals?.amount?.get(it.key) ?: 0.0) to it.target.toDouble() })
    val breastMilkOnly = dri?.breastMilkCovers == true

    ChildScreenScaffold(title = if (isThai) "โภชนาการและการเจริญเติบโต" else "Nutrition & Growth", currentTab = "nutrition", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                val status = when {
                    breastMilkOnly -> if (isThai) "นมคืออาหารหลักของวัยนี้" else "Milk is the whole diet at this age"
                    nothingLogged -> if (isThai) "ยังไม่มีบันทึกวันนี้" else "Nothing logged yet today"
                    overall == null -> "—"
                    overall >= 0.9 -> if (isThai) "สมดุลดีเยี่ยม" else "Well balanced"
                    overall >= 0.7 -> if (isThai) "ค่อนข้างดี" else "Fairly good"
                    else -> if (isThai) "ยังต้องเสริม" else "Needs more"
                }
                HeaderCard(
                    name = child?.name.orEmpty(), avatarUrl = child?.avatarUrl, age = hubAgeLabel(child?.birthdate, isThai),
                    status = status, intake = intake, targetKcal = targetKcal, energyShare = energyShare,
                    milkMl = ui.todayMilkMl, milkMin = ui.todayMilk.sumOf { it.durationMin ?: 0 },
                    waterMl = ui.todayWaterMl.takeIf { months >= 6 }, isThai = isThai,
                    onTrend = { navController.navigate(Screen.NutritionInsights.route) },
                    onDrink = { navController.navigate(Screen.MilkRecord.route) }
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Shield, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(if (isThai) "ดัชนีชี้วัดความเสี่ยง 2 ด้าน" else "2 risk indicators", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(if (isThai) "เกณฑ์ WHO · DRI" else "WHO · DRI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bmi = vm.bmiStatus()
                    val risk = DayNutrition.overweightRisk(bmi?.z, energyShare)
                    RiskCard(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.MonitorWeight,
                        badge = when (risk) {
                            DayNutrition.Risk.LOW -> (if (isThai) "ความเสี่ยงต่ำ" else "Low risk") to Good
                            DayNutrition.Risk.WATCH -> (if (isThai) "เฝ้าระวัง" else "Watch") to Watch
                            DayNutrition.Risk.HIGH -> (if (isThai) "ความเสี่ยงสูง" else "High risk") to Error
                            DayNutrition.Risk.UNKNOWN -> (if (isThai) "ยังไม่มีข้อมูล" else "No data") to Outline
                        },
                        title = if (isThai) "ป้องกันโรคอ้วน" else "Overweight",
                        body = bmi?.let { (if (isThai) "รูปร่างตามวัย: " else "Build for age: ") + (if (isThai) it.labelTh else it.labelEn) }
                            ?: (if (isThai) "บันทึกน้ำหนักและส่วนสูงเพื่อประเมินตามเกณฑ์ WHO" else "Log weight and height to check against WHO"),
                        footer = when {
                            energyShare == null -> if (isThai) "ยังไม่มีบันทึกอาหาร" else "No food logged"
                            energyShare > 1.2 -> if (isThai) "พลังงานเกินเป้าวันนี้" else "Energy over target today"
                            else -> if (isThai) "พลังงานสมดุล" else "Energy on track"
                        },
                        footerOk = energyShare != null && energyShare <= 1.2,
                        onClick = { navController.navigate(if (bmi == null) Screen.AddGrowthRecord.route else Screen.GrowthHistory.route) }
                    )
                    val groupNames = mapOf(
                        1 to (if (isThai) "โปรตีน" else "protein"), 2 to (if (isThai) "คาร์โบไฮเดรต" else "carbs"),
                        3 to (if (isThai) "ผัก" else "vegetables"), 4 to (if (isThai) "ผลไม้" else "fruit"), 5 to (if (isThai) "ไขมัน" else "fat")
                    )
                    val missing = totals?.let { (1..5) - it.groups }.orEmpty()
                    RiskCard(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.MonitorHeart,
                        badge = when {
                            breastMilkOnly -> (if (isThai) "นมเพียงพอ" else "Milk covers it") to Good
                            totals == null -> (if (isThai) "ยังไม่มีข้อมูล" else "No data") to Outline
                            missing.isEmpty() -> (if (isThai) "ครบ 5 หมู่" else "All 5 groups") to Good
                            else -> (if (isThai) "ขาด ${missing.size} หมู่" else "${missing.size} missing") to Watch
                        },
                        title = if (isThai) "ป้องกันขาดสารอาหาร" else "Undernutrition",
                        body = when {
                            breastMilkOnly -> if (isThai) "0–5 เดือน: นมแม่หรือนมผงให้สารอาหารครบ ยังไม่ต้องเริ่มอาหารอื่น" else "0–5 months: breast milk or formula covers everything"
                            totals == null -> if (isThai) "บันทึกมื้ออาหารเพื่อเช็กอาหารหลัก 5 หมู่" else "Log meals to check the 5 food groups"
                            missing.isEmpty() -> if (isThai) "ได้อาหารครบ 5 หมู่ตามวัย $months เดือน" else "All 5 food groups for $months months"
                            else -> (if (isThai) "ยังขาด: " else "Missing: ") + missing.mapNotNull { groupNames[it] }.joinToString(", ")
                        },
                        footer = when {
                            breastMilkOnly || totals == null -> if (isThai) "อาหารหลัก 5 หมู่" else "Thai 5 food groups"
                            totals.groupsUnknown.isNotEmpty() -> if (isThai) "ระบุหมู่ไม่ได้ ${totals.groupsUnknown.size} รายการ" else "${totals.groupsUnknown.size} not classified"
                            missing == setOf(5) -> if (isThai) "เติมน้ำมันพืชเล็กน้อยในอาหาร" else "Add a little vegetable oil"
                            missing.isEmpty() -> if (isThai) "ครบถ้วนทุกหมู่" else "Complete"
                            else -> if (isThai) "เพิ่ม${groupNames[missing.first()]}ในมื้อถัดไป" else "Add ${groupNames[missing.first()]} next meal"
                        },
                        footerOk = breastMilkOnly || (missing.isEmpty() && totals != null),
                        onClick = { navController.navigate(Screen.NutritionInsights.route) }
                    )
                }
            }

            item {
                PillarsCard(
                    pillars = pillars, amounts = totals?.amount.orEmpty(), missing = totals?.missing.orEmpty(),
                    overall = overall, loading = ui.dayTotalsLoading && totals == null, breastMilkOnly = breastMilkOnly,
                    isThai = isThai, onClick = { navController.navigate(Screen.NutritionInsights.route) }
                )
            }

            item { ServicesCard(isThai, ui.fridgeIngredients.size, navController) }

            item {
                RecommendationsCard(
                    recs = ui.menuRecs, loading = ui.menuRecsLoading, isThai = isThai,
                    onOpen = { rec -> vm.loadRecipe(rec.title, lang); navController.navigate(Screen.RecipeDetail.route) },
                    onGenerate = { vm.ensureMenuRecommendations(lang) },
                    onMore = { navController.navigate(Screen.RecommendedFood.route) }
                )
            }

            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(Modifier.width(5.dp).fillMaxHeight().background(Primary))
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SmartToy, null, tint = Primary)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(if (isThai) "น้องรัก AI วิเคราะห์วันนี้" else "NongRak AI today", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(vm.loggingInsightText(isThai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            child?.allergies?.takeIf { it.isNotBlank() }?.let { allergyText ->
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Error.copy(0.08f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(22.dp))
                                Text(if (isThai) "ข้อควรระวังเรื่องการแพ้" else "Allergy Watch", fontWeight = FontWeight.Bold, color = Error, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(allergyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.AllergyHistory.route) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                            ) { Text(if (isThai) "ดูรายการอาหารที่เคยทดสอบแล้ว" else "View tested foods", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

private data class Pillar(val label: String, val icon: ImageVector, val key: String, val target: Int, val unit: String, val color: Color)

@Composable
private fun HeaderCard(
    name: String, avatarUrl: String?, age: String, status: String,
    intake: Int, targetKcal: Int, energyShare: Double?,
    milkMl: Int, milkMin: Int, waterMl: Int?, isThai: Boolean,
    onTrend: () -> Unit, onDrink: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubChildAvatar(avatarUrl, 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (age.isNotBlank()) Surface(shape = RoundedCornerShape(100.dp), color = Mint) {
                            Text(age, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00201C), maxLines = 1)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Good))
                        Text((if (isThai) "โภชนาการวันนี้: " else "Today: ") + status, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Good)
                    }
                }
                Surface(onClick = onTrend, shape = CircleShape, color = Primary.copy(0.1f), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Insights, if (isThai) "สถิติโภชนาการ" else "Nutrition statistics", tint = Primary) }
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Primary.copy(0.06f)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (energyShare ?: 0.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(),
                            color = Primary, strokeWidth = 10.dp, trackColor = Primary.copy(0.15f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(energyShare?.let { "${(it * 100).toInt()}%" } ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (isThai) "เกณฑ์ DRI" else "of DRI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text(if (isThai) "พลังงานรวม" else "Energy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%,d / %,d kcal".format(intake, targetKcal), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val ml = if (isThai) "มล." else "ml"
                            val milkValue = when {
                                milkMl > 0 && milkMin > 0 -> "$milkMl $ml +${milkMin}′"
                                milkMin > 0 -> "$milkMin ${if (isThai) "นาที" else "min"}"
                                else -> "$milkMl $ml"
                            }
                            DrinkTile(Modifier.weight(1f), Icons.Filled.BabyChangingStation, if (isThai) "นมแม่/ชง" else "Milk", milkValue, onDrink)
                            if (waterMl != null) DrinkTile(Modifier.weight(1f), Icons.Filled.WaterDrop, if (isThai) "น้ำต้มสุก" else "Water", "$waterMl $ml", onDrink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrinkTile(modifier: Modifier, icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RiskCard(
    modifier: Modifier, icon: ImageVector, badge: Pair<String, Color>, title: String, body: String,
    footer: String, footerOk: Boolean, onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier.fillMaxHeight(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badge.second.copy(0.15f)) {
                    Text(badge.first, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badge.second, maxLines = 1)
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (footerOk) Icons.Filled.CheckCircle else Icons.Filled.Info, null, tint = if (footerOk) Good else Watch, modifier = Modifier.size(16.dp))
                Text(footer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (footerOk) Good else Watch)
            }
        }
    }
}

@Composable
private fun PillarsCard(
    pillars: List<Pillar>, amounts: Map<String, Double>, missing: Map<String, Int>, overall: Double?,
    loading: Boolean, breastMilkOnly: Boolean, isThai: Boolean, onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.MilitaryTech, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(if (isThai) "4 เสาหลักโภชนาการวันนี้" else "Today's 4 key nutrients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(if (isThai) "สารอาหารสำคัญต่อการพัฒนาสมองและกระดูก" else "Key for brain and bone growth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                overall?.let { Text((if (isThai) "ภาพรวม " else "Overall ") + "${(it * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Primary, fontWeight = FontWeight.Bold) }
            }
            when {
                breastMilkOnly -> Text(
                    if (isThai) "0–5 เดือน: นมแม่ให้สารอาหารครบตามเกณฑ์ DRI ยังไม่ต้องติดตามทีละตัว" else "0–5 months: breast milk covers the DRI; no need to track each nutrient yet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                else -> pillars.forEach { p ->
                    val amount = amounts[p.key] ?: 0.0
                    val share = if (p.target > 0) amount / p.target else 0.0
                    val gap = (missing[p.key] ?: 0) > 0 && share < 0.9
                    Surface(shape = RoundedCornerShape(14.dp), color = Primary.copy(0.06f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(p.color.copy(0.12f)), contentAlignment = Alignment.Center) {
                                        Icon(p.icon, null, tint = p.color, modifier = Modifier.size(16.dp))
                                    }
                                    Text(p.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    "${fmt(amount)} / ${p.target}${p.unit} (${(share * 100).toInt()}%)" + if (gap) "*" else "",
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { share.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                                color = p.color, trackColor = p.color.copy(0.15f)
                            )
                        }
                    }
                }
            }
            if (!breastMilkOnly && pillars.any { (missing[it.key] ?: 0) > 0 }) Text(
                if (isThai) "* บางรายการไม่มีข้อมูลสารอาหารนี้ ตัวเลขจริงอาจสูงกว่า" else "* Some foods have no figure for this nutrient; the real amount may be higher",
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
        }
    }
}

private fun fmt(v: Double) = if (v < 10) "%.1f".format(v) else "%.0f".format(v)

@Composable
private fun ServicesCard(isThai: Boolean, fridgeCount: Int, navController: NavController) {
    val go = { route: String -> { navController.navigate(route) } }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TileIcon(Icons.Filled.Tune, Primary)
                Column(Modifier.weight(1f)) {
                    Text(if (isThai) "บริการและการจัดการโภชนาการ" else "Nutrition tools", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (isThai) "บันทึกและติดตามกิจกรรมสุขภาพรายวัน" else "Log and track daily", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                    Text(if (isThai) "5 รายการ" else "5 tools", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceTile(Modifier.weight(1f), Icons.Filled.Restaurant, Primary, if (isThai) "บันทึกอาหาร" else "Log food", if (isThai) "มื้อหลักและว่าง" else "Meals and snacks", go(Screen.CalorieCalculator.route))
                ServiceTile(Modifier.weight(1f), Icons.Filled.LocalFireDepartment, Error, if (isThai) "การเผาผลาญ" else "Energy burned", if (isThai) "กิจกรรมและแคลอรี่" else "Activity and kcal", go(Screen.Exercise.route))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceTile(Modifier.weight(1f), Icons.Filled.Kitchen, Good, if (isThai) "คลังวัตถุดิบ" else "Fridge", if (isThai) "เช็กของสดในบ้าน ($fridgeCount)" else "At home ($fridgeCount)", go(Screen.RefrigeratorScanner.route))
                ServiceTile(Modifier.weight(1f), Icons.Filled.LocalDrink, Tertiary, if (isThai) "บันทึกการดื่ม" else "Log drinks", if (isThai) "นม · น้ำ (มล.)" else "Milk · water (ml)", go(Screen.MilkRecord.route))
            }
            Surface(onClick = go(Screen.FoodHistory.route), shape = RoundedCornerShape(16.dp), color = Primary.copy(0.06f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TileIcon(Icons.AutoMirrored.Filled.ReceiptLong, Primary)
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) "ประวัติมื้ออาหาร" else "Meal history", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(if (isThai) "รายการอาหารที่ทานและสารอาหารย้อนหลัง" else "Past meals and nutrients", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (isThai) "ดูประวัติ" else "View", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TileIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color)
    }
}

@Composable
private fun ServiceTile(modifier: Modifier, icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = Primary.copy(0.06f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileIcon(icon, color)
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecommendationsCard(
    recs: List<SuggestionItem>, loading: Boolean, isThai: Boolean,
    onOpen: (SuggestionItem) -> Unit, onGenerate: () -> Unit, onMore: () -> Unit
) {
    val meal = LocalTime.now().hour.let {
        when {
            it < 10 -> if (isThai) "มื้อเช้าวันนี้" else "Breakfast"
            it < 14 -> if (isThai) "มื้อกลางวันวันนี้" else "Lunch"
            it < 17 -> if (isThai) "มื้อว่างบ่าย" else "Snack"
            else -> if (isThai) "มื้อเย็นวันนี้" else "Dinner"
        }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lightbulb, null, tint = Primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (isThai) "คำแนะนำสิ่งที่ลูกควรทานเพิ่ม" else "What to add next", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (isThai) "ปรับสมดุลตามสารอาหารที่ยังขาด" else "Balanced for today's gaps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                    Text(meal, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
                }
            }
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Primary)
                    Text(if (isThai) "AI กำลังคิดเมนูที่เหมาะกับน้อง..." else "AI is picking dishes...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                recs.isEmpty() -> Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "สร้างเมนูแนะนำจาก AI" else "Get meal ideas")
                }
                else -> recs.take(2).forEach { rec ->
                    Surface(onClick = { onOpen(rec) }, shape = RoundedCornerShape(16.dp), color = Primary.copy(0.06f)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(
                                model = foodImageUrl(rec.title), contentDescription = rec.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.08f))
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(rec.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    nutrientTagSymbols(listOf(rec.subtitle) + rec.tags).firstOrNull()?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary, maxLines = 1)
                                    }
                                }
                                if (rec.subtitle.isNotBlank()) Text(rec.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Surface(onClick = onMore, shape = RoundedCornerShape(14.dp), color = Primary.copy(0.1f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "ดูเพิ่มเติม (See More)" else "See more", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Primary)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Primary)
                }
            }
        }
    }
}

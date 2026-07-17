@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

/** Hub page — matches Child-Project/Update/28.nutrition/daily_nutrition_nutrient_summary. */
@Composable
fun NutritionScreen(vm: NutritionViewModel, lang: String, navController: NavController) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(lang) { vm.load(lang) }
    // AI-estimates today's macronutrients from the logged food names (loadMacros internally
    // caches by food-set so this is a no-op once already loaded for the current foods).
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { vm.loadMacros(lang) }

    val todayMilkMl = ui.todayMilkMl
    val milkTargetMl = 850 // rough reference; no per-child feeding target field exists yet

    ChildScreenScaffold(title = if (isThai) "โภชนาการ" else "Nutrition", currentTab = "nutrition", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Allergy watch
            val allergies = ui.child?.allergies?.takeIf { !it.isNullOrBlank() }
            if (allergies != null) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Error.copy(0.08f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(22.dp))
                                Text(if (isThai) "ข้อควรระวังเรื่องการแพ้" else "Allergy Watch", fontWeight = FontWeight.Bold, color = Error, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(allergies, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.AllergyHistory.route) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                            ) { Text(if (isThai) "ดูรายการอาหารที่เคยทดสอบแล้ว" else "View tested foods", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // Calorie summary hero — centered, matches the mockup's big number + 3 stacked buttons.
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val intake = ui.summary?.intakeKcal ?: 0
                        val burned = ui.summary?.burnedKcal ?: 0
                        val net = intake - burned
                        val target = ui.targetKcal.coerceAtLeast(1)
                        val pct = (intake.toFloat() / target).coerceIn(0f, 1f)
                        val pctText = intake * 100 / target
                        Text(if (isThai) "สรุปแคลอรี่วันนี้" else "Today's calories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$intake", style = MaterialTheme.typography.headlineMedium, color = Primary, fontWeight = FontWeight.Bold)
                                Text(" / ${ui.targetKcal} kcal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                                color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                if (isThai) "ได้รับพลังงานแล้ว $pctText% ของปริมาณที่แนะนำ" else "$pctText% of the recommended amount so far",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                (if (isThai) "สุทธิ $net kcal (ได้รับ $intake − เผาผลาญ $burned)" else "Net $net kcal (intake $intake − burned $burned)"),
                                style = MaterialTheme.typography.labelSmall, color = Outline
                            )
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { navController.navigate(Screen.CalorieCalculator.route) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.width(8.dp))
                                Text(if (isThai) "บันทึกและคำนวณแคลอรี่" else "Log & calculate calories", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.CalorieCheck.route) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                            ) {
                                Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text(if (isThai) "ตรวจสอบแคลอรี่" else "Calorie check", fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { navController.navigate(Screen.EditFoodEntries.route) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.ListAlt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text(if (isThai) "ดูรายการอาหารทั้งหมดวันนี้" else "View all of today's food", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Milk feeding summary — centered hero + AI-analysis blurb, matches the mockup.
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.BabyChangingStation, null, tint = Primary)
                            Text(if (isThai) "บันทึกการดื่มนม" else "Milk feeding", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.widthIn(max = 240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val pct = (todayMilkMl.toFloat() / milkTargetMl).coerceIn(0f, 1f)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$todayMilkMl", style = MaterialTheme.typography.headlineMedium, color = Primary, fontWeight = FontWeight.Bold)
                                Text(" / $milkTargetMl มล.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                                color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            val milkPct = (todayMilkMl * 100 / milkTargetMl.coerceAtLeast(1))
                            Text(
                                if (isThai) "ดื่มไปแล้ว $milkPct% ของปริมาณที่แนะนำ" else "$milkPct% of the recommended amount so far",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(14.dp))
                                Text(
                                    (if (isThai) "วิเคราะห์โดย AI: " else "AI analysis: ") +
                                        (if (isThai) "ปริมาณการดื่มนมวันนี้อยู่ในเกณฑ์ปกติ แนะนำให้รักษาช่วงเวลาเดิม" else "Today's milk intake is within the normal range — keep the same feeding schedule"),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Button(
                            onClick = { navController.navigate(Screen.MilkRecord.route) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "บันทึกการดื่มนม" else "Log a feeding", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Daily nutrient summary — protein/carbs/calcium/iron bars + AI bullet analysis, matches
            // the mockup's "สรุปสารอาหารรายวัน" card. "ดูรายละเอียดทั้งหมด" opens the full stats page.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "สรุปสารอาหารรายวัน" else "Today's nutrient summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Card(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (ui.macrosLoading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                        } else if (ui.macros == null) {
                            Text(
                                if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้" else "No food logged today yet",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val m = ui.macros!!
                            val (targetCarb, targetProtein, _) = ui.targetMacros
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                NutrientBar(if (isThai) "โปรตีน (Protein)" else "Protein", m.protein, targetProtein, "g", isThai)
                                NutrientBar(if (isThai) "คาร์โบไฮเดรต (Carbs)" else "Carbs", m.carb, targetCarb, "g", isThai)
                                NutrientBar(if (isThai) "แคลเซียม (Calcium)" else "Calcium", m.calciumMg, ui.targetCalciumMg, "mg", isThai)
                                NutrientBar(if (isThai) "ธาตุเหล็ก (Iron)" else "Iron", m.ironMg, ui.targetIronMg, "mg", isThai)
                            }
                            val insights = buildList {
                                val enough = m.calciumMg >= ui.targetCalciumMg.coerceAtLeast(1) * 0.9f
                                add(
                                    (if (enough) (if (isThai) "ได้รับแคลเซียมเพียงพอ: ช่วยเสริมสร้างกระดูกและฟันให้แข็งแรง" else "Calcium looks sufficient: supports bone and teeth development")
                                    else (if (isThai) "ควรเพิ่มแคลเซียม: สำคัญต่อการเสริมสร้างกระดูกและฟัน" else "Could use more calcium: important for bone and teeth development"))
                                        to enough
                                )
                                val ironOk = m.ironMg >= ui.targetIronMg.coerceAtLeast(1) * 0.9f
                                add(
                                    (if (ironOk) (if (isThai) "ได้รับธาตุเหล็กเพียงพอ: ช่วยพัฒนาสมองและการสร้างเม็ดเลือด" else "Iron looks sufficient: supports brain development and red blood cells")
                                    else (if (isThai) "ควรเพิ่มธาตุเหล็ก: สำคัญต่อการพัฒนาสมองและการสร้างเม็ดเลือดแดง" else "Could use more iron: important for brain development and red blood cells"))
                                        to ironOk
                                )
                            }
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(16.dp))
                                            Text(if (isThai) "การวิเคราะห์โดย AI" else "AI analysis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        TextButton(onClick = { navController.navigate(Screen.NutritionInsights.route) }) {
                                            Text(if (isThai) "ดูรายละเอียดทั้งหมด" else "See full detail", color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    insights.forEach { (text, ok) ->
                                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info, null,
                                                tint = if (ok) Color(0xFF2E7D32) else Color(0xFFEF6C00), modifier = Modifier.size(16.dp)
                                            )
                                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            Text(
                                if (isThai) "ประมาณการโดย AI จากรายการอาหารที่บันทึกวันนี้ (ค่าอ้างอิงคร่าวๆ ไม่ใช่คำแนะนำทางการแพทย์)"
                                else "AI estimate from today's logged foods (rough reference, not medical advice)",
                                style = MaterialTheme.typography.labelSmall, color = Outline
                            )
                            Button(
                                onClick = { navController.navigate(Screen.RecommendedFood.route) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Filled.Lightbulb, null); Spacer(Modifier.width(8.dp))
                                Text(if (isThai) "ดูสิ่งที่คุณควรเพิ่ม" else "See what to add more of", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Feeding tips — static, matches the mockup's "เคล็ดลับการป้อน" strip.
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = SecondaryContainer.copy(0.3f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isThai) "เคล็ดลับการป้อน" else "Feeding tips", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        listOf(
                            if (isThai) "เริ่มต้นเพียง 1-2 ช้อนชาในช่วงแรก เพื่อให้ลูกคุ้นเคยกับช้อน" else "Start with just 1-2 teaspoons at first, so your child gets used to the spoon",
                            if (isThai) "ให้อาหารเสริมในเวลาที่ลูกอารมณ์ดีและไม่หิวจัดเกินไป" else "Offer solids when your child is in a good mood and not overly hungry"
                        ).forEach { tip ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

        }
    }
}

/** One row of the "สรุปสารอาหารรายวัน" card: label, current/target, a progress bar, and an
 *  enough/need-more status line. 90% of target counts as "enough" — matches the loose framing
 *  the AI estimate itself carries (a rough estimate shouldn't claim single-unit precision). */
@Composable
private fun NutrientBar(label: String, current: Int, target: Int, unit: String, isThai: Boolean) {
    val safeTarget = target.coerceAtLeast(1)
    val pct = (current.toFloat() / safeTarget).coerceIn(0f, 1f)
    val sufficient = current >= safeTarget * 0.9f
    val statusColor = if (sufficient) Color(0xFF2E7D32) else Color(0xFFEF6C00)
    val statusText = if (sufficient) {
        if (isThai) "เพียงพอ" else "Enough"
    } else {
        val remaining = (target - current).coerceAtLeast(0)
        if (isThai) "ควรเพิ่มอีกประมาณ $remaining$unit" else "Needs about $remaining$unit more"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("$current / $target$unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
            color = statusColor, trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
    }
}

@Composable
fun SuggestionCard(item: SuggestionItem, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Restaurant, null, tint = Primary)
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}

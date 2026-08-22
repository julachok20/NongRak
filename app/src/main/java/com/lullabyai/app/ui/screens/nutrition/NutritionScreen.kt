@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

/** Hub page — matches Child-Project/Update/32.Food/enhanced_daily_nutrient_dashboard: nutrients
 *  are the hero (4 big cards up top), kcal and milk are demoted to small summary cards near the
 *  bottom. Same underlying data as before (ui.macros / ui.summary / ui.todayMilkMl) — this is a
 *  reorder + restyle to match the agreed "nutrients first, not kcal" direction, not new backend. */
@Composable
fun NutritionScreen(vm: NutritionViewModel, lang: String, navController: NavController) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(lang) { vm.load(lang) }
    // AI-estimates today's macronutrients from the logged food names (loadMacros internally
    // caches by food-set so this is a no-op once already loaded for the current foods).
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { vm.loadMacros(lang) }
    LaunchedEffect(ui.child?.id, lang, ui.macros) { vm.ensureMenuRecommendations(lang) }

    val todayMilkMl = ui.todayMilkMl
    val milkTargetMl = 850 // rough reference; no per-child feeding target field exists yet

    ChildScreenScaffold(title = if (isThai) "โภชนาการและสารอาหาร" else "Nutrition", currentTab = "nutrition", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item { Text(if (isThai) "แดชบอร์ดสารอาหาร" else "Nutrient Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

            item {
                AIInsightCard(
                    text = vm.loggingInsightText(isThai),
                    isLoading = ui.macrosLoading && ui.macros == null
                )
            }

            // Nutrient hero cards — one big card per nutrient, matches the mockup's visual weight.
            if (ui.macrosLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary) }
            } else if (ui.macros == null) {
                item {
                    Text(if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้" else "No food logged today yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val m = ui.macros!!
                val (targetCarb, targetProtein, _) = ui.targetMacros
                item { NutrientHeroCard(Icons.Filled.Circle, if (isThai) "โปรตีน" else "Protein", m.protein, targetProtein, "g", isThai) }
                item { NutrientHeroCard(Icons.Filled.BarChart, if (isThai) "คาร์โบไฮเดรต" else "Carbs", m.carb, targetCarb, "g", isThai) }
                item { NutrientHeroCard(Icons.Filled.Storage, if (isThai) "แคลเซียม" else "Calcium", m.calciumMg, ui.targetCalciumMg, "mg", isThai) }
                item { NutrientHeroCard(Icons.Filled.WaterDrop, if (isThai) "ธาตุเหล็ก" else "Iron", m.ironMg, ui.targetIronMg, "mg", isThai) }

                // AI analysis card — insight bullets + recommendation/report actions.
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                                    Text(if (isThai) "วิเคราะห์เชิงลึกโดย AI" else "AI deep analysis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Primary)
                                }
                                TextButton(onClick = { navController.navigate(Screen.NutritionInsights.route) }, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (isThai) "ดูแผนโภชนาการรายสัปดาห์" else "Weekly plan", color = Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            val enough = m.calciumMg >= ui.targetCalciumMg.coerceAtLeast(1) * 0.9f
                            val ironOk = m.ironMg >= ui.targetIronMg.coerceAtLeast(1) * 0.9f
                            listOf(
                                (if (enough) (if (isThai) "แคลเซียมอยู่ในเกณฑ์ดี: ส่งผลดีต่อการสร้างมวลกระดูกและฟันในช่วงขยายตัว" else "Calcium looks good: supports healthy bone and teeth growth")
                                 else (if (isThai) "ควรเพิ่มแคลเซียม: สำคัญต่อการเสริมสร้างกระดูกและฟัน" else "Could use more calcium: important for bone and teeth development")) to enough,
                                (if (ironOk) (if (isThai) "ธาตุเหล็กอยู่ในเกณฑ์ดี: ช่วยพัฒนาสมองและการสร้างเม็ดเลือด" else "Iron looks good: supports brain development and red blood cells")
                                 else (if (isThai) "ควรเสริมธาตุเหล็ก: ลองอาหารไม่แนะนำ ตับบด หรือผักโขม เพื่อช่วยเรื่องการสร้างเม็ดเลือด" else "Could use more iron: try liver, or spinach, to help with red blood cell production")) to ironOk
                            ).forEach { (text, ok) ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info, null, tint = if (ok) Color(0xFF2E7D32) else Color(0xFFEF6C00), modifier = Modifier.size(16.dp))
                                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(
                                onClick = { navController.navigate(Screen.RecommendedFood.route) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Filled.Lightbulb, null); Spacer(Modifier.width(8.dp))
                                Text(if (isThai) "รับคำแนะนำเมนูอาหารฉบับเต็ม" else "View All Meal Recommendations", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.NutritionInsights.route) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                            ) {
                                Icon(Icons.Filled.Assessment, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text(if (isThai) "ดูรายงานฉบับเต็ม" else "View full report", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                if (isThai) "ประมาณการโดย AI จากรายการอาหารที่บันทึกวันนี้ (ค่าอ้างอิงคร่าวๆ ไม่ใช่คำแนะนำทางการแพทย์)"
                                else "AI estimate from today's logged foods (rough reference, not medical advice)",
                                style = MaterialTheme.typography.labelSmall, color = Outline
                            )
                        }
                    }
                }
            }

            // ==================== AI RECOMMENDED MEALS SECTION ====================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                if (isThai) "เมนูอาหารแนะนำจาก AI สำหรับน้อง" else "AI Recommended Meals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = { navController.navigate(Screen.RecommendedFood.route) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (isThai) "ดูทั้งหมด" else "View all",
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        if (isThai) "คัดสรรเมนูตามช่วงวัย เสริมสารอาหารที่ลูกต้องการ พร้อมตรวจเช็ควัตถุดิบในบ้าน"
                        else "Tailored age-appropriate meals to boost target nutrients and match ingredients at home",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (ui.menuRecsLoading) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Primary)
                            Text(
                                if (isThai) "AI กำลังคิดค้นเมนูที่เหมาะสมกับน้อง..." else "AI is generating customized recipes...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (ui.menuRecs.isNotEmpty()) {
                items(ui.menuRecs.take(3), key = { it.title }) { rec ->
                    DishRecommendationCard(
                        rec = rec,
                        isThai = isThai,
                        onOpen = {
                            vm.loadRecipe(rec.title, lang)
                            navController.navigate(Screen.RecipeDetail.route)
                        }
                    )
                }
            } else {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                    ) {
                        Column(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (isThai) "แตะด้านล่างเพื่อรับคำแนะนำเมนูอาหารจาก AI" else "Tap below to get AI meal recommendations",
                                style = MaterialTheme.typography.bodySmall,
                                color = Outline
                            )
                            Button(
                                onClick = { vm.ensureMenuRecommendations(lang) },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isThai) "สร้างเมนูแนะนำจาก AI" else "Generate Meal Ideas", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // Log-food actions
            item {
                Button(
                    onClick = { navController.navigate(Screen.CalorieCalculator.route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Restaurant, null); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกอาหาร" else "Log food", fontWeight = FontWeight.Bold)
                }
            }
            item {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.RefrigeratorScanner.route) },
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(Icons.Filled.Kitchen, null, tint = Primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "ตู้เย็นอัจฉริยะ & สแกนวัตถุดิบ (${ui.fridgeIngredients.size})" else "Smart Refrigerator & Ingredients (${ui.fridgeIngredients.size})", fontWeight = FontWeight.Bold)
                }
            }
            item {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.CalorieCheck.route) },
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(Icons.Filled.FactCheck, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "ตรวจสอบอาหารและสารอาหาร" else "Check food & nutrients", fontWeight = FontWeight.Bold)
                }
            }
            item {
                TextButton(onClick = { navController.navigate(Screen.EditFoodEntries.route) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ListAlt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "ดูรายการอาหารวันนี้" else "View today's food list", fontWeight = FontWeight.Bold)
                }
            }

            // Kcal — demoted to a small summary card (nutrients are the hero now, not calories).
            item {
                val intake = ui.summary?.intakeKcal ?: 0
                val target = ui.targetKcal.coerceAtLeast(1)
                val pct = (intake.toFloat() / target).coerceIn(0f, 1f)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Bolt, null, tint = Outline, modifier = Modifier.size(16.dp))
                            Text(if (isThai) "สรุปแคลอรี่วันนี้" else "Today's calories", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("$intake / ${ui.targetKcal} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)), color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }

            // Milk — also demoted to a compact card.
            item {
                val pct = (todayMilkMl.toFloat() / milkTargetMl).coerceIn(0f, 1f)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.BabyChangingStation, null, tint = Outline, modifier = Modifier.size(16.dp))
                            Text(if (isThai) "การดื่มนม" else "Milk", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("$todayMilkMl / $milkTargetMl มล.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)), color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        OutlinedButton(onClick = { navController.navigate(Screen.MilkRecord.route) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)) {
                            Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "บันทึกการดื่มนม" else "Log a feeding", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Allergy watch — moved near the bottom, matches the mockup's position.
            val allergyText = ui.child?.allergies?.takeIf { it.isNotBlank() }
            if (allergyText != null) {
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

            // Feeding tips — static, matches the mockup's "เคล็ดลับการป้อนสัปดาห์นี้" strip.
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SecondaryContainer.copy(0.3f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isThai) "เคล็ดลับการป้อนสัปดาห์นี้" else "This week's feeding tips", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
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

/** One nutrient's hero card: icon, %-of-target badge, label, current/target, progress bar. */
@Composable
private fun NutrientHeroCard(icon: ImageVector, label: String, current: Int, target: Int, unit: String, isThai: Boolean) {
    val safeTarget = target.coerceAtLeast(1)
    val pct = (current.toFloat() / safeTarget).coerceIn(0f, 1f)
    val pctInt = (current * 100 / safeTarget).coerceIn(0, 999)
    val sufficient = current >= safeTarget * 0.9f
    val barColor = if (sufficient) Color(0xFF2E7D32) else Primary
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                Surface(shape = RoundedCornerShape(100.dp), color = barColor.copy(0.1f)) {
                    Text("$pctInt%", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = barColor, fontWeight = FontWeight.Bold)
                }
            }
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$current / $target $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)), color = barColor, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/**
 * Modern Bento Dish Recommendation Card for the Nutrition Dashboard
 */
@Composable
private fun DishRecommendationCard(rec: SuggestionItem, isThai: Boolean, onOpen: () -> Unit) {
    val tags = nutrientTagSymbols(listOf(rec.subtitle) + rec.tags)
    val timeTag = rec.tags.firstOrNull { it.contains("min", ignoreCase = true) || it.contains("นาที") }
    val inFridgeTag = rec.tags.any { it.contains("มีวัตถุดิบ", ignoreCase = true) || it.contains("fridge", ignoreCase = true) || it.contains("ในบ้าน", ignoreCase = true) }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp).background(Primary.copy(0.08f))) {
                AsyncImage(
                    model = foodImageUrl(rec.title),
                    contentDescription = rec.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (timeTag != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(0.92f)
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Schedule, null, tint = Primary, modifier = Modifier.size(12.dp))
                                Text(timeTag, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (inFridgeTag) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Primary
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Kitchen, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(if (isThai) "มีวัตถุดิบครบ" else "In Fridge", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (rec.subtitle.isNotBlank()) {
                    Text(rec.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.take(4).forEach { t ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Primary.copy(0.1f)
                            ) {
                                Text(
                                    t,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (isThai) "ดูสูตรอาหาร" else "See Recipe",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
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


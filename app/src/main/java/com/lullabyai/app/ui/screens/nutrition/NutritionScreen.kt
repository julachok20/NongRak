@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

@Composable
fun NutritionScreen(vm: NutritionViewModel, lang: String, navController: NavController) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(lang) { vm.load(lang) }
    // Recompute meal recommendations whenever the logged food changes (a meal getting logged removes
    // it from the recommendations) or the language changes. Idempotent — no-op if nothing changed.
    LaunchedEffect(ui.foods, ui.child?.id, lang) { vm.ensureMealRecommendations(lang) }

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
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(22.dp))
                            Column {
                                Text(if (isThai) "ข้อควรระวังเรื่องการแพ้" else "Allergy Watch", fontWeight = FontWeight.Bold, color = Error, style = MaterialTheme.typography.titleSmall)
                                Text(allergies, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Calorie summary entry
            item {
                Card(
                    onClick = { navController.navigate(Screen.CalorieSummary.route) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val intake = ui.summary?.intakeKcal ?: 0
                        val burned = ui.summary?.burnedKcal ?: 0
                        val net = intake - burned
                        val target = ui.targetKcal.coerceAtLeast(1)
                        val pct = (intake.toFloat() / target).coerceIn(0f, 1f)   // progress bar only (0–100%)
                        val pctText = intake * 100 / target                       // true %, may exceed 100 when over target
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalFireDepartment, null, tint = Primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (isThai) "สรุปแคลอรี่วันนี้" else "Today's calories", fontWeight = FontWeight.Bold)
                                // Intake vs the age-based daily target.
                                Text("$intake / ${ui.targetKcal} kcal", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                        }
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                            color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            (if (isThai) "${pctText}% ของเป้าหมาย • สุทธิ $net (ได้รับ $intake − เผาผลาญ $burned)" else "${pctText}% of target • net $net (intake $intake − burned $burned)") + " kcal",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Parent calorie-check tool (type any dish → kcal + macros + tip).
            item {
                Card(
                    onClick = { navController.navigate(Screen.CalorieCheck.route) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Bolt, null, tint = Primary)
                        Column(Modifier.weight(1f)) {
                            Text(if (isThai) "ตรวจสอบแคลอรี่ (สำหรับคุณพ่อคุณแม่)" else "Calorie check (for parents)", fontWeight = FontWeight.Bold)
                            Text(if (isThai) "พิมพ์ชื่ออาหารแล้วให้ AI คำนวณแคลอรี่และสารอาหาร" else "Type a dish — AI estimates calories & macros", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                    }
                }
            }

            // Today's logged food — driven entirely by what the user enters in the calculator
            // (no auto-generated AI meal plan). Tap "วิธีทำ" on any food to see its recipe on demand.
            item { Text(if (isThai) "อาหารของวันนี้" else "Today's food", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            if (ui.foods.isEmpty()) {
                item {
                    Text(
                        if (isThai) "ยังไม่มีรายการอาหารวันนี้ กดปุ่มด้านล่างเพื่อบันทึกและคำนวณแคลอรี่"
                        else "No food logged today. Use the button below to log & calculate calories.",
                        style = MaterialTheme.typography.bodySmall, color = Outline
                    )
                }
            } else {
                items(ui.foods, key = { it.id }) { f ->
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Restaurant, null, tint = Primary)
                            Column(Modifier.weight(1f)) {
                                Text(f.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text((if (isThai) "ประมาณ " else "approx. ") + "${f.calories ?: "-"} kcal", style = MaterialTheme.typography.labelSmall, color = Outline)
                            }
                        }
                    }
                }
            }

            // Recommended meals — only the meals the child hasn't logged yet today. Recipes ("วิธีทำ")
            // live here, on recommended food, not on the food already eaten above.
            item {
                Text(if (isThai) "แนะนำมื้อถัดไป" else "Recommended next meals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (ui.mealRecs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                        if (ui.mealRecsLoading) CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Text(if (isThai) "วันนี้ครบทุกมื้อแล้ว 🎉" else "All meals logged for today 🎉", style = MaterialTheme.typography.bodySmall, color = Outline)
                    }
                }
            } else {
                listOf("breakfast", "lunch", "dinner").forEach { mk ->
                    val rec = ui.mealRecs[mk]
                    if (rec != null) {
                        item {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(mealSectionLabel(mk, isThai), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                                    Text(rec.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    if (rec.subtitle.isNotBlank()) Text(rec.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Surface(
                                        onClick = { vm.loadRecipe(rec.title, lang); navController.navigate(Screen.RecipeDetail.route) },
                                        shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)
                                    ) {
                                        Text(if (isThai) "วิธีทำ" else "Recipe", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.CalorieCalculator.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Calculate, null); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกและคำนวณแคลอรี่" else "Log & calculate calories", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun mealSectionLabel(key: String, isThai: Boolean) = when (key) {
    "breakfast" -> if (isThai) "มื้อเช้า" else "Breakfast"
    "lunch" -> if (isThai) "มื้อกลางวัน" else "Lunch"
    "dinner" -> if (isThai) "มื้อเย็น" else "Dinner"
    else -> if (isThai) "ของว่าง / แนะนำ" else "Snack / Suggestion"
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

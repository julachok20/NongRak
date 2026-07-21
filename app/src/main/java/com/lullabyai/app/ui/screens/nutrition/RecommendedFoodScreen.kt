@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

private data class NutrientFood(val nameTh: String, val nameEn: String)

/** "อาหารที่แนะนำ" — matches Child-Project/Update/28.nutrition/recommended_food. Holds the menu
 *  recommendations moved off the Nutrition hub, plus food ideas for whichever mineral (calcium/iron)
 *  is currently under target. */
@Composable
fun RecommendedFoodScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { vm.loadMacros(lang) }
    // Re-runs once today's macros finish loading, so the recs target the CURRENT deficiencies
    // rather than the generic budget list (macros are null until loadMacros above resolves).
    LaunchedEffect(ui.child?.id, lang, ui.macros) { vm.ensureMenuRecommendations(lang) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "อาหารที่แนะนำสำหรับน้อง" else "Recommended food for your child", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text(
                    if (isThai) "คัดสรรโดย AI ตามบันทึกการเติบโตล่าสุด" else "Curated by AI from your recent food logs",
                    style = MaterialTheme.typography.bodySmall, color = Outline
                )
            }

            val m = ui.macros
            val needs = vm.deficientNutrients()
            val needLabels = mapOf(
                "protein" to (if (isThai) "โปรตีน (Protein)" else "Protein"),
                "carbohydrates" to (if (isThai) "คาร์โบไฮเดรต (Carbs)" else "Carbohydrates"),
                "fat" to (if (isThai) "ไขมัน (Fat)" else "Fat"),
                "calcium" to (if (isThai) "แคลเซียม (Calcium)" else "Calcium"),
                "iron" to (if (isThai) "ธาตุเหล็ก (Iron)" else "Iron")
            )

            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(40.dp).background(Primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Column {
                            Text(if (isThai) "การวิเคราะห์โดย AI" else "AI Analysis", fontWeight = FontWeight.Bold, color = Primary)
                            val msg = when {
                                m == null -> if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้ บันทึกเพิ่มเพื่อรับคำแนะนำที่ตรงจุด" else "No food logged today yet — log some to get tailored advice"
                                needs.isEmpty() -> if (isThai) "วันนี้ได้รับสารอาหารครบตามเป้าหมายแล้วค่ะ" else "All tracked nutrients look sufficient today"
                                else -> {
                                    val names = needs.mapNotNull { needLabels[it] }.joinToString(if (isThai) " และ" else " and ")
                                    if (isThai) "วันนี้ลูกน้อยของคุณต้องการ${names}เพิ่มเติม" else "Today your child could use more $names"
                                }
                            }
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (needs.isNotEmpty()) {
                item { Text(if (isThai) "สารอาหารที่ควรเพิ่ม" else "Nutrients to boost", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                val needFoods = mapOf(
                    "protein" to (Icons.Filled.Egg to listOf(NutrientFood("ไข่ต้ม", "Boiled egg"), NutrientFood("เนื้อสัตว์บด", "Minced meat"))),
                    "carbohydrates" to (Icons.Filled.BakeryDining to listOf(NutrientFood("ข้าวสวย", "Steamed rice"), NutrientFood("มันฝรั่งบด", "Mashed potato"))),
                    "fat" to (Icons.Filled.WaterDrop to listOf(NutrientFood("อะโวคาโด", "Avocado"), NutrientFood("ไข่แดง", "Egg yolk"))),
                    "calcium" to (Icons.Filled.WaterDrop to listOf(NutrientFood("นมแพะ/นมวัว", "Goat/cow milk"), NutrientFood("ปลาตัวเล็กทอด", "Small fried fish"))),
                    "iron" to (Icons.Filled.Bloodtype to listOf(NutrientFood("ตับไก่บด", "Minced chicken liver"), NutrientFood("ผักโขม", "Spinach")))
                )
                needs.forEach { key ->
                    val (icon, foods) = needFoods[key] ?: return@forEach
                    item {
                        NutrientFoodCard(needLabels[key] ?: key, icon, foods, isThai) { name -> vm.addFoodSmart(name, lang) }
                    }
                }
            }

            item {
                Text(if (isThai) "เมนูแนะนำ" else "Recommended menus", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (isThai) "อาหารบ้านๆ ราคาไม่แพง เหมาะกับวัยลูกน้อย"
                    else "Simple home cooking — affordable everyday ingredients",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ui.menuRecs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                        if (ui.menuRecsLoading) CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Text(if (isThai) "กำลังโหลดเมนูแนะนำ..." else "Loading menu ideas...", style = MaterialTheme.typography.bodySmall, color = Outline)
                    }
                }
            } else {
                items(ui.menuRecs, key = { it.title }) { rec ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(rec.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            if (rec.subtitle.isNotBlank()) {
                                Text(rec.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                onClick = { vm.loadRecipe(rec.title, lang); navController.navigate(Screen.RecipeDetail.route) },
                                shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.Visibility, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    Text(if (isThai) "ดูวิธีทำ" else "See recipe", color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientFoodCard(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, foods: List<NutrientFood>, isThai: Boolean,
    onAdd: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
            foods.forEach { f ->
                val name = if (isThai) f.nameTh else f.nameEn
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { onAdd(name) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.AddCircle, if (isThai) "เพิ่ม" else "Add", tint = Primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

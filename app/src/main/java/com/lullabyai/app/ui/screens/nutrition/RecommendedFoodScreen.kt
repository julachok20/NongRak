@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

private data class NutrientFood(val nameTh: String, val nameEn: String)

/** "อาหารที่แนะนำ" — Stitch recommended_food: AI insight, nutrient boosts, tagged menu cards. */
@Composable
fun RecommendedFoodScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { vm.loadMacros(lang) }
    LaunchedEffect(ui.child?.id, lang, ui.macros) { vm.ensureMenuRecommendations(lang) }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "อาหารที่แนะนำสำหรับน้อง" else "Recommended food for your child",
                onBack = onBack,
                navController = navController
            )
        },
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

            item {
                AIInsightCard(
                    text = vm.loggingInsightText(isThai),
                    isLoading = ui.macrosLoading && ui.macros == null
                )
            }

            val needs = vm.deficientNutrients()
            val needLabels = mapOf(
                "protein" to (if (isThai) "โปรตีน (Protein)" else "Protein"),
                "carbohydrates" to (if (isThai) "คาร์โบไฮเดรต (Carbs)" else "Carbohydrates"),
                "fat" to (if (isThai) "ไขมัน (Fat)" else "Fat"),
                "calcium" to (if (isThai) "แคลเซียม (Calcium)" else "Calcium"),
                "iron" to (if (isThai) "ธาตุเหล็ก (Iron)" else "Iron")
            )

            if (needs.isNotEmpty()) {
                item { Text(if (isThai) "สารอาหารที่ควรเพิ่ม" else "Nutrients to boost", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val needFoods = mapOf(
                            "protein" to (Icons.Filled.Egg to listOf(NutrientFood("ไข่ต้ม", "Boiled egg"), NutrientFood("เนื้อสัตว์บด", "Minced meat"))),
                            "carbohydrates" to (Icons.Filled.BakeryDining to listOf(NutrientFood("ข้าวสวย", "Steamed rice"), NutrientFood("มันฝรั่งบด", "Mashed potato"))),
                            "fat" to (Icons.Filled.WaterDrop to listOf(NutrientFood("อะโวคาโด", "Avocado"), NutrientFood("ไข่แดง", "Egg yolk"))),
                            "calcium" to (Icons.Filled.WaterDrop to listOf(NutrientFood("นมแพะ/นมวัว", "Goat/cow milk"), NutrientFood("ปลาตัวเล็กทอด", "Small fried fish"))),
                            "iron" to (Icons.Filled.Bloodtype to listOf(NutrientFood("ตับไก่บด", "Minced chicken liver"), NutrientFood("ผักโขม", "Spinach")))
                        )
                        needs.forEach { key ->
                            val (icon, foods) = needFoods[key] ?: return@forEach
                            NutrientBoostCard(
                                label = needLabels[key] ?: key,
                                icon = icon,
                                foods = foods,
                                isThai = isThai,
                                onAdd = { name -> vm.addFoodSmart(name, lang) }
                            )
                        }
                    }
                }
            }

            // Smart Refrigerator / Available Ingredients Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Kitchen, null, tint = Primary)
                                Text(
                                    text = if (isThai) "วัตถุดิบในตู้เย็น (${ui.fridgeIngredients.size})" else "Fridge Ingredients (${ui.fridgeIngredients.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            }
                            TextButton(
                                onClick = { navController.navigate(Screen.RefrigeratorScanner.route) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, null, tint = Primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isThai) "สแกน / เพิ่ม" else "Scan / Add", color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (ui.fridgeIngredients.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ui.fridgeIngredients.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = item.ingredientName,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isThai) "💡 AI นำวัตถุดิบเหล่านี้มาช่วยเลือกเมนูอาหารด้านล่างให้ตรงกับที่คุณมีในบ้าน" else "💡 AI uses these available ingredients to prioritize matching recipes below.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Outline
                            )
                        } else {
                            Text(
                                text = if (isThai) "ยังไม่มีวัตถุดิบที่บันทึกไว้ แตะ 'สแกน / เพิ่ม' เพื่อให้ AI แนะนำเมนูจากของในตู้เย็นได้ค่ะ" else "No fridge items logged. Tap 'Scan / Add' to get recipes tailored to what you have.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    RecommendedMenuCard(rec, isThai) {
                        vm.loadRecipe(rec.title, lang)
                        navController.navigate(Screen.RecipeDetail.route)
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientBoostCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    foods: List<NutrientFood>,
    isThai: Boolean,
    onAdd: (String) -> Unit
) {
    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
            foods.forEach { f ->
                val name = if (isThai) f.nameTh else f.nameEn
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Stitch shows + on boost cards; user previously removed from hub copy — keep on Recommended Food page per Stitch.
                    IconButton(onClick = { onAdd(name) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.AddCircle, if (isThai) "เพิ่ม" else "Add", tint = Primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedMenuCard(rec: SuggestionItem, isThai: Boolean, onOpen: () -> Unit) {
    val tags = nutrientTagSymbols(listOf(rec.subtitle) + rec.tags)
    val timeTag = rec.tags.firstOrNull { it.contains("min", ignoreCase = true) || it.contains("นาที") }
    val easyTag = rec.tags.firstOrNull { it.contains("easy", ignoreCase = true) || it.contains("ง่าย") }
    val inFridgeTag = rec.tags.any { it.contains("มีวัตถุดิบ", ignoreCase = true) || it.contains("fridge", ignoreCase = true) || it.contains("ในบ้าน", ignoreCase = true) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(140.dp).background(Primary.copy(0.08f))) {
                AsyncImage(
                    model = foodImageUrl(rec.title),
                    contentDescription = rec.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (timeTag != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(0.92f)
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Schedule, null, tint = Primary, modifier = Modifier.size(14.dp))
                                Text(timeTag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (inFridgeTag) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Primary
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Kitchen, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text(if (isThai) "มีวัตถุดิบ" else "In Fridge", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(rec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (easyTag != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF2E7D32).copy(0.12f)) {
                            Text(if (isThai) "ง่าย" else "Easy", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (rec.subtitle.isNotBlank()) {
                    Text(rec.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        tags.forEachIndexed { i, t ->
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(if (i % 2 == 0) Primary.copy(0.15f) else SecondaryContainer)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(t, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        }
                    }
                    Surface(onClick = onOpen, shape = RoundedCornerShape(12.dp), color = Primary) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(if (isThai) "ดูวิธีทำ" else "See recipe", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

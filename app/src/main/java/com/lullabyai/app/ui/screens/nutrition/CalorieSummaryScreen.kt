@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

private fun mealLabel(key: String, isThai: Boolean) = when (key) {
    "breakfast" -> if (isThai) "มื้อเช้า" else "Breakfast"
    "lunch" -> if (isThai) "มื้อกลางวัน" else "Lunch"
    "dinner" -> if (isThai) "มื้อเย็น" else "Dinner"
    "snack" -> if (isThai) "ของว่าง" else "Snack"
    else -> if (isThai) "อื่นๆ" else "Other"
}

@Composable
fun CalorieSummaryScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }
    // Estimate today's macros (cached by the food set).
    LaunchedEffect(ui.foods, lang) { vm.loadMacros(lang) }

    val intake = ui.summary?.intakeKcal ?: 0
    val burned = ui.summary?.burnedKcal ?: 0
    val net = intake - burned
    val target = ui.targetKcal.coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "สรุปแคลอรี่ประจำวัน" else "Daily Calorie Summary", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
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
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                        CircularProgressIndicator(
                            progress = { (intake.toFloat() / target).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(), color = Primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 12.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$intake", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                            Text((if (isThai) "จาก ${ui.targetKcal} แคลอรี่" else "of ${ui.targetKcal} kcal"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatPair(Icons.Filled.Restaurant, if (isThai) "ได้รับทั้งหมด" else "Intake", "$intake kcal")
                        StatPair(Icons.Filled.LocalFireDepartment, if (isThai) "เผาผลาญ" else "Burned", "$burned kcal")
                        StatPair(Icons.Filled.Balance, if (isThai) "สุทธิ" else "Net", "$net kcal")
                    }
                    // Macros (AI-estimated for today's foods)
                    ui.macros?.let { (carb, protein, fat) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            MacroPill(if (isThai) "คาร์บ" else "Carb", "${carb}g")
                            MacroPill(if (isThai) "โปรตีน" else "Protein", "${protein}g")
                            MacroPill(if (isThai) "ไขมัน" else "Fat", "${fat}g")
                        }
                    } ?: run {
                        if (ui.macrosLoading) Text(if (isThai) "กำลังประเมินสารอาหาร..." else "Estimating macros...", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isThai) "รายการอาหาร" else "Meals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.clickable { navController.navigate(Screen.EditFoodEntries.route) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(16.dp))
                    Text(if (isThai) "แก้ไข" else "Edit", color = Primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            // Grouped by meal type — matches the mockup (มื้อเช้า/มื้อเที่ยง/มื้อเย็น/ของว่าง),
            // was previously a flat list even though MealCard already existed for this.
            if (ui.foods.isEmpty()) {
                Text(if (isThai) "ยังไม่มีรายการอาหาร" else "No food items yet", style = MaterialTheme.typography.bodyMedium, color = Outline)
            } else {
                listOf("breakfast", "lunch", "dinner", "snack").forEach { mealType ->
                    val foods = ui.foods.filter { it.mealType == mealType }
                    if (foods.isNotEmpty()) MealCard(mealLabel(mealType, isThai), foods, Modifier.fillMaxWidth())
                }
                val other = ui.foods.filter { it.mealType !in setOf("breakfast", "lunch", "dinner", "snack") }
                if (other.isNotEmpty()) MealCard(mealLabel("other", isThai), other, Modifier.fillMaxWidth())
            }

            if (burned > 0) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.1f))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.DirectionsRun, null, tint = Primary)
                        Text(if (isThai) "การทำกิจกรรมวันนี้ (${ui.summary?.exerciseMinutes ?: 0} นาที)" else "Today's activity (${ui.summary?.exerciseMinutes ?: 0} min)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("-$burned kcal", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Sync with exercise — pulls today's burned cals and opens the Exercise page to plan
            OutlinedButton(
                onClick = { vm.refreshSummary(); navController.navigate(Screen.Exercise.route) },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(100.dp)
            ) {
                Icon(Icons.Filled.Sync, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (isThai) "ซิงค์กับข้อมูลออกกำลังกาย" else "Sync with exercise data")
            }

            Button(
                onClick = onBack, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text(if (isThai) "บันทึกข้อมูล" else "Save", fontWeight = FontWeight.Bold) }

            TextButton(onClick = { navController.navigate(Screen.EditFoodEntries.route) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (isThai) "แก้ไขรายการอาหาร" else "Edit food entries", color = Primary)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MealCard(label: String, foods: List<FoodLog>, modifier: Modifier = Modifier) {
    val kcal = foods.sumOf { it.calories ?: 0 }
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Primary.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Restaurant, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                Text("$kcal kcal", color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(foods.joinToString(", ") { it.itemName }.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun MacroPill(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Primary.copy(0.08f)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatPair(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

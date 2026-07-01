@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

/**
 * Parent-facing calorie check (design: 06.Re-Nutrition/calorie_check_ai_calculation).
 * Type any dish → AI estimates total kcal + macros + a tip, with a generated photo.
 */
@Composable
fun CalorieCheckScreen(vm: NutritionViewModel, lang: String, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    var food by remember { mutableStateOf("") }
    val r = ui.calorieCheck

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "ตรวจสอบแคลอรี่" else "Calorie check", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (isThai) "รายชื่ออาหาร" else "Food", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = food, onValueChange = { food = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (isThai) "เช่น ข้าวผัดกะเพราไข่ดาว" else "e.g. fried rice with egg", color = Outline) },
                trailingIcon = { Icon(Icons.Filled.Restaurant, null, tint = Outline) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
            )
            Button(
                onClick = { vm.runCalorieCheck(food, lang) },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                enabled = food.isNotBlank() && !ui.checkLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (ui.checkLoading) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White); Spacer(Modifier.width(8.dp)) }
                Text(if (isThai) "ให้ AI คำนวณ" else "Let AI calculate", fontWeight = FontWeight.Bold)
            }

            Text(if (isThai) "ผลลัพธ์การคำนวณ" else "Result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // Total energy card
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) "พลังงานทั้งหมด" else "Total energy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((r?.kcal?.toString() ?: "---") + " kcal", style = MaterialTheme.typography.headlineMedium, color = Primary, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Bolt, null, tint = Primary)
                    }
                }
            }

            // Macro boxes
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroBox(if (isThai) "คาร์บ" else "Carb", r?.carb, Modifier.weight(1f))
                MacroBox(if (isThai) "โปรตีน" else "Protein", r?.protein, Modifier.weight(1f))
                MacroBox(if (isThai) "ไขมัน" else "Fat", r?.fat, Modifier.weight(1f))
            }

            // AI tip
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Column {
                        Text("AI Tip", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                        Text(
                            r?.tip?.takeIf { it.isNotBlank() } ?: (if (isThai) "พิมพ์ชื่ออาหารด้านบน แล้วให้ AI วิเคราะห์สัดส่วนสารอาหารที่เหมาะสมสำหรับคุณ" else "Type a food above and let AI analyze its nutrition for you."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Generated photo of the checked dish
            if (r != null && ui.checkedFood.isNotBlank()) {
                val url = remember(ui.checkedFood) { foodImageUrl(ui.checkedFood) }
                Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Primary, Tertiary, SecondaryContainer)))) {
                    AsyncImage(model = url, contentDescription = ui.checkedFood, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MacroBox(label: String, grams: Int?, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((grams?.toString() ?: "--") + " g", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

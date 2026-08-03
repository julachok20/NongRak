@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Today's Full Food List — Stitch today_s_full_food_list (date badge, snack slots, summary + age badge). */
@Composable
fun EditFoodEntriesScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilkMl, lang) { vm.loadMacros(lang) }

    var editing by remember { mutableStateOf<FoodLog?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val intake = ui.summary?.intakeKcal ?: ui.foods.sumOf { it.calories ?: 0 }
    val target = ui.targetKcal.coerceAtLeast(1)
    val pct = (intake.toFloat() / target).coerceIn(0f, 1f)
    val remaining = (ui.targetKcal - intake).coerceAtLeast(0)
    val ageBadge = ageAppropriateBadge(intake, ui.targetKcal, isThai)

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "รายการอาหารวันนี้" else "Today's food list",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            if (isThai) "ติดตามการเติบโต" else "Growth tracking",
                            style = MaterialTheme.typography.labelSmall, color = Outline
                        )
                        Text(formatLogDateHeader(ui.logDate, isThai), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp), color = Primary.copy(0.1f)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text(if (isThai) "เปลี่ยนวันที่" else "Change date", color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                AIInsightCard(
                    text = vm.loggingInsightText(isThai),
                    isLoading = ui.macrosLoading && ui.macros == null
                )
            }

            if (ui.foods.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(if (isThai) "ไม่มีรายการอาหารสำหรับวันนี้" else "No food entries for this day", color = Outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(ui.foods, key = { it.id }) { f ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(56.dp).background(SecondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Restaurant, null, tint = Primary, modifier = Modifier.size(24.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(displayMealLabel(f, isThai), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                                    val time = com.lullabyai.app.util.TimeUtils.localTime(f.createdAt) ?: ""
                                    if (time.isNotBlank()) Text("$time " + if (isThai) "น." else "", style = MaterialTheme.typography.labelSmall, color = Outline)
                                }
                                Text(f.itemName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("${f.calories ?: "-"} kcal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editing = f }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.EditNote, null, tint = Outline, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isThai) "สรุปแคลอรี่รวมวันนี้" else "Today's total calories", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Surface(shape = RoundedCornerShape(100.dp), color = Color.White.copy(0.2f)) {
                                Text((if (isThai) "เป้าหมาย: " else "Target: ") + "${ui.targetKcal} kcal", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$intake", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(" / ${ui.targetKcal} kcal", color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodyMedium)
                        }
                        LinearProgressIndicator(
                            progress = { pct }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                            color = Color.White, trackColor = Color.White.copy(0.2f)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isThai) "เหลืออีก $remaining kcal" else "$remaining kcal left", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelSmall)
                            if (ageBadge != null) {
                                Surface(shape = RoundedCornerShape(8.dp), color = Color.White) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                        Text(ageBadge, color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { navController.navigate(Screen.CalorieCalculator.route) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(100.dp)) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "เพิ่มรายการอาหาร" else "Add food entry")
                }
            }
            item {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "บันทึกการเปลี่ยนแปลง" else "Save changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    editing?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.itemName) }
        var kcalText by remember(target.id) { mutableStateOf(target.calories?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isThai) "แก้ไขรายการอาหาร" else "Edit food", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(if (isThai) "ชื่ออาหาร" else "Food name") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = kcalText, onValueChange = { kcalText = it.filter { c -> c.isDigit() } }, singleLine = true,
                        label = { Text("kcal") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.editFood(target.id, name)
                    kcalText.toIntOrNull()?.let { vm.editFoodCalories(target.id, it) }
                    editing = null
                }) {
                    Text(if (isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (ui.isChildOwner) {
                        TextButton(onClick = { vm.deleteFood(target.id); editing = null }) {
                            Text(if (isThai) "ลบ" else "Delete", color = Error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") }
                }
            }
        )
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val current = runCatching { LocalDate.parse(ui.logDate) }.getOrDefault(LocalDate.now())
        val initialMillis = current.atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
                        vm.setLogDate(d, lang)
                    }
                    showDatePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

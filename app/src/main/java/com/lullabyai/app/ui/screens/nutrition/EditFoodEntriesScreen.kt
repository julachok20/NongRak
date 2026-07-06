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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.time.LocalDate

@Composable
fun EditFoodEntriesScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }

    var editing by remember { mutableStateOf<FoodLog?>(null) }
    val total = ui.foods.sumOf { it.calories ?: 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "แก้ไขรายการอาหาร" else "Edit Food Entries", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Total kcal card
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isThai) "แคลอรี่รวมวันนี้" else "Total calories today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$total " + (if (isThai) "แคลอรี่" else "kcal"), style = MaterialTheme.typography.headlineSmall, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.size(44.dp).background(Primary.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Restaurant, null, tint = Primary)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "รายการอาหารล่าสุด" else "Recent food entries", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                        Text(if (isThai) "วันนี้" else "Today", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                    }
                }
            }

            if (ui.foods.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(if (isThai) "ไม่มีรายการอาหารสำหรับวันนี้" else "No food entries for today", color = Outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(ui.foods, key = { it.id }) { f ->
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).background(SecondaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Restaurant, null, tint = Primary, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(f.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                val time = com.lullabyai.app.util.TimeUtils.localTime(f.createdAt) ?: ""
                                Text((if (time.isNotBlank()) "$time " + (if (isThai) "น." else "") + " • " else "") + "${f.calories ?: "-"} " + (if (isThai) "แคลอรี่" else "kcal"),
                                    style = MaterialTheme.typography.labelSmall, color = Outline)
                            }
                            IconButton(onClick = { editing = f }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { vm.deleteFood(f.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(18.dp))
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
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isThai) "แก้ไขรายการอาหาร" else "Edit food", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) vm.editFood(target.id, name); editing = null }) {
                    Text(if (isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

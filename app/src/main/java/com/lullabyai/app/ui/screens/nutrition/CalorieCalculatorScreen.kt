@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.io.ByteArrayOutputStream

@Composable
fun CalorieCalculatorScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var portion by remember { mutableStateOf("") }
    var spilled by remember { mutableStateOf(false) }
    var spillFraction by remember { mutableStateOf(0f) }
    var showExercise by remember { mutableStateOf(false) }
    var exName by remember { mutableStateOf("") }
    var exMinutes by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }

    LaunchedEffect(ui.photoAddFailed) {
        if (ui.photoAddFailed) {
            Toast.makeText(context, if (isThai) "ไม่พบอาหารในรูปภาพ ลองถ่ายใหม่อีกครั้ง" else "No food recognized in the photo — try again", Toast.LENGTH_SHORT).show()
        }
    }

    fun bitmapToJpeg(bmp: Bitmap): ByteArray = ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            vm.addFoodFromPhoto(bitmapToJpeg(bmp), lang, portion, spillFraction)
            portion = ""; spilled = false; spillFraction = 0f
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
        else Toast.makeText(context, if (isThai) "ต้องอนุญาตให้ใช้กล้องก่อน" else "Camera permission is needed", Toast.LENGTH_SHORT).show()
    }
    val openCamera = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val totalKcal = ui.foods.sumOf { it.calories ?: 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "เครื่องคำนวณแคลอรี่" else "Calorie Calculator", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
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
            // Total kcal card
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) "แคลอรี่รวมวันนี้" else "Total calories today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalKcal " + (if (isThai) "แคลอรี่" else "kcal"), style = MaterialTheme.typography.headlineSmall, color = Primary, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(44.dp).background(Primary.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Restaurant, null, tint = Primary) }
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (isThai) "บันทึกข้อมูลอาหาร" else "Log food", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Text(if (isThai) "ค้นหาหรือเพิ่มรายการอาหาร" else "Search or add a food item", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                            placeholder = { Text(if (isThai) "เช่น โจ๊ก, ข้าวมันไก่..." else "e.g. porridge, chicken rice...", color = Outline) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                        )
                        Button(
                            onClick = {
                                if (query.isNotBlank()) {
                                    vm.addFoodSmart(query, lang, portion, spillFraction)
                                    query = ""; portion = ""; spilled = false; spillFraction = 0f
                                }
                            },
                            shape = RoundedCornerShape(12.dp), enabled = !ui.calculating, colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(if (isThai) "เพิ่ม" else "Add", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = portion, onValueChange = { portion = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isThai) "ปริมาณที่ทาน" else "Portion eaten", color = Outline) },
                        placeholder = { Text(if (isThai) "เช่น 1 ถ้วย, 100 กรัม" else "e.g. 1 cup, 100g", color = Outline) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                    )

                    OutlinedButton(
                        onClick = { openCamera() },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), enabled = !ui.calculating
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (isThai) "ถ่ายรูปอาหาร ให้ AI คำนวณให้" else "Take a photo — AI calculates for you", fontWeight = FontWeight.Bold)
                    }

                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = Primary, modifier = Modifier.size(20.dp))
                                Text(if (isThai) "น้องทำหกหรือไม่?" else "Did your child spill it?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = spilled,
                                    onCheckedChange = { spilled = it; if (!it) spillFraction = 0f },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                                )
                            }
                            if (spilled) {
                                Text(if (isThai) "น้องทานหกหรือคายทิ้งประมาณเท่าไหร่?" else "About how much did they spill or spit out?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val options = listOf(
                                    Triple(0.15f, if (isThai) "เล็กน้อย" else "A little", if (isThai) "~15%" else "~15%"),
                                    Triple(0.5f, if (isThai) "ประมาณครึ่งหนึ่ง" else "About half", if (isThai) "~50%" else "~50%"),
                                    Triple(0.8f, if (isThai) "เกือบทั้งหมด" else "Almost all", if (isThai) "~80%" else "~80%")
                                )
                                options.forEach { (frac, label, pct) ->
                                    OutlinedButton(
                                        onClick = { spillFraction = frac },
                                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                        colors = if (spillFraction == frac) ButtonDefaults.outlinedButtonColors(containerColor = Primary.copy(0.1f), contentColor = Primary) else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(label); Text(pct, color = Outline, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isThai) "รายการที่เลือกแล้ว" else "Selected items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                            Text("${ui.foods.size} " + (if (isThai) "รายการ" else "items"), Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                    }
                    if (ui.calculating) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(if (isThai) "กำลังประเมินแคลอรี่..." else "Estimating calories...", style = MaterialTheme.typography.bodySmall, color = Outline)
                        }
                    }
                    ui.foods.forEach { f ->
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Restaurant, null, tint = Primary, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(f.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    val time = com.lullabyai.app.util.TimeUtils.localTime(f.createdAt) ?: ""
                                    Text((if (time.isNotBlank()) "$time " + (if (isThai) "น. • " else "• ") else "") + (if (isThai) "ประมาณ " else "approx. ") + "${f.calories ?: "-"} kcal", style = MaterialTheme.typography.labelSmall, color = Outline)
                                }
                                IconButton(onClick = { navController.navigate(Screen.EditFoodEntries.route) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(18.dp)) }
                                IconButton(onClick = { vm.deleteFood(f.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }

                    // Exercise toggle → inline exercise entry (no navigation)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.DirectionsRun, null, tint = Primary)
                        Text(if (isThai) "ออกกำลังกายวันนี้" else "Exercised today", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showExercise, onCheckedChange = { showExercise = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary))
                    }

                    if (showExercise) {
                        ui.exerciseLogs.forEach { e ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.FitnessCenter, null, tint = Primary, modifier = Modifier.size(20.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(e.activityName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${e.durationMin ?: "-"} " + (if (isThai) "นาที" else "min") + (e.caloriesBurned?.let { " • -$it kcal" } ?: ""), style = MaterialTheme.typography.labelSmall, color = Outline)
                                    }
                                    IconButton(onClick = { vm.deleteExercise(e.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                        // Add-exercise form
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (isThai) "บันทึกการออกกำลังกาย" else "Log exercise", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = exName, onValueChange = { exName = it }, modifier = Modifier.weight(1.6f), placeholder = { Text(if (isThai) "เช่น วิ่ง, ว่ายน้ำ..." else "e.g. running...", color = Outline) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary))
                                    OutlinedTextField(value = exMinutes, onValueChange = { exMinutes = it.filter { c -> c.isDigit() } }, modifier = Modifier.weight(1f), placeholder = { Text(if (isThai) "นาที" else "min", color = Outline) }, singleLine = true, shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary))
                                }
                                Button(
                                    onClick = {
                                        val mins = exMinutes.toIntOrNull() ?: 0
                                        if (exName.isNotBlank() && mins > 0) { vm.logExercise(exName.trim(), mins, lang); exName = ""; exMinutes = "" }
                                    },
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                ) {
                                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                    Text(if (isThai) "เพิ่มกิจกรรม" else "Add activity", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { vm.refreshSummary(); navController.navigate(Screen.CalorieSummary.route) },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(100.dp),
                enabled = !ui.calculating && ui.foods.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Calculate, null); Spacer(Modifier.width(8.dp))
                Text(if (isThai) "บันทึกและคำนวณแคลอรี่" else "Save & calculate calories", fontWeight = FontWeight.Bold)
            }

            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Primary, Tertiary, SecondaryContainer)))) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                    Text(if (isThai) "โภชนาการที่สมดุล" else "Balanced nutrition", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (isThai) "สร้างรากฐานที่ดีให้ลูกน้อย" else "builds a strong foundation for your child", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

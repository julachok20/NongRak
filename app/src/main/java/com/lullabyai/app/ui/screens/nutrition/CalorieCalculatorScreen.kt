@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.HubChildAvatar
import com.lullabyai.app.ui.screens.child.hubAgeLabel
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.DayNutrition
import com.lullabyai.app.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import java.io.File

internal val FoodGood = Color(0xFF00796B)
internal val FoodWatch = Color(0xFFEF6C00)

/** The portion buttons from the design; "กำหนดเอง" opens a free-text field instead. */
private val PORTION_PRESETS_TH = listOf("1 ถ้วยเล็ก", "100 กรัม", "3 ช้อนโต๊ะ")
private val PORTION_PRESETS_EN = listOf("1 small bowl", "100 g", "3 tbsp")

/**
 * Record Food — Stitch "AI Calorie & Nutrition Calculator". The parent types a dish or photographs
 * it, says how much went in, and the AI (Gemma 4 on the family's own box, cloud when that is
 * unreachable) turns it into a food_logs row. The list below is what THIS sitting logged; the whole
 * day is one tap away on รายการอาหารวันนี้.
 */
@Composable
fun CalorieCalculatorScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var portion by remember { mutableStateOf("") }
    var customPortion by remember { mutableStateOf(false) }
    var spilled by remember { mutableStateOf(false) }
    var spillFraction by remember { mutableStateOf(0f) }
    var editingFood by remember { mutableStateOf<FoodLog?>(null) }

    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilk, lang) { vm.loadDayNutrition(lang) }
    LaunchedEffect(ui.photoAddFailed) {
        if (ui.photoAddFailed) {
            snackbars.showSnackbar(
                if (isThai) "ไม่พบอาหารในรูป ลองถ่ายให้เห็นจานหรือชามชัดขึ้น" else "No food recognized — try a clearer shot of the plate"
            )
        }
    }

    fun clearForm() { query = ""; portion = ""; customPortion = false; spilled = false; spillFraction = 0f }

    // A file the camera app writes into: TakePicture keeps the full frame, unlike the preview
    // thumbnail — the same photo is stored on the row and shown in the day's timeline.
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (ok && uri != null) {
            val bytes = com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri)
            if (bytes != null) { vm.addFoodFromPhoto(bytes, lang, portion, spillFraction); clearForm() }
            else scope.launch { snackbars.showSnackbar(if (isThai) "อ่านไฟล์รูปไม่สำเร็จ" else "Could not read the photo file") }
        }
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "food_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else scope.launch { snackbars.showSnackbar(if (isThai) "ต้องอนุญาตให้ใช้กล้องก่อน" else "Camera permission is needed") }
    }
    val openCamera = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri)
            if (bytes != null) { vm.addFoodFromPhoto(bytes, lang, portion, spillFraction); clearForm() }
        }
    }

    val totals = ui.dayTotals
    val months = vm.ageMonths()
    val intake = vm.dayIntakeKcal() ?: ui.foods.sumOf { it.calories ?: 0 }
    val targetKcal = ui.dri?.energyKcal ?: ui.targetKcal
    val share = if (targetKcal > 0) intake.toFloat() / targetKcal else 0f
    val mealFoods = ui.foods.filter { it.id in ui.mealFoodIds }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "คำนวณโภชนาการด้วย AI" else "AI Calorie & Nutrition",
                onBack = onBack,
                navController = navController
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
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
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                EnergyHeader(
                    name = ui.child?.name.orEmpty(), avatarUrl = ui.childAvatarUrl,
                    age = hubAgeLabel(ui.child?.birthdate, isThai),
                    intake = intake, target = targetKcal, share = share, isThai = isThai
                )
            }

            item {
                RecordCard(
                    query = query, onQuery = { query = it },
                    portion = portion, onPortion = { portion = it },
                    customPortion = customPortion, onCustomPortion = { customPortion = it },
                    presets = if (isThai) PORTION_PRESETS_TH else PORTION_PRESETS_EN,
                    spilled = spilled, onSpilled = { spilled = it; if (!it) spillFraction = 0f },
                    spillFraction = spillFraction, onSpillFraction = { spillFraction = it },
                    busy = ui.calculating, isThai = isThai,
                    onCamera = openCamera,
                    onGallery = { galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCalculate = {
                        if (query.isNotBlank()) { vm.addFoodSmart(query, lang, portion, spillFraction); clearForm() }
                    }
                )
            }

            item {
                MealListCard(
                    foods = mealFoods, vm = vm, isThai = isThai, canDelete = ui.isChildOwner,
                    onEdit = { editingFood = it }, onDelete = { vm.deleteFood(it) },
                    onSeeDay = { navController.navigate(Screen.EditFoodEntries.route) }
                )
            }

            item {
                MacroCard(
                    totals = totals, isThai = isThai, months = months,
                    targetMacros = ui.targetMacros, targetCalciumMg = ui.targetCalciumMg, targetIronMg = ui.targetIronMg,
                    sodiumMax = ui.dri?.ranges?.get("sodium")?.endInclusive,
                    breastMilkOnly = ui.dri?.breastMilkCovers == true
                )
            }

            item {
                AdviceCard(
                    text = vm.loggingInsightText(isThai), allergies = ui.child?.allergies,
                    foods = ui.foods.map { it.itemName }, isThai = isThai
                )
            }

            item {
                Button(
                    onClick = {
                        vm.refreshSummary()
                        vm.clearMealSession()
                        clearForm()
                        scope.launch {
                            snackbars.showSnackbar(if (isThai) "บันทึกโภชนาการวันนี้เรียบร้อยแล้ว" else "Today's nutrition saved")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(100.dp),
                    enabled = !ui.calculating,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกข้อมูลโภชนาการวันนี้" else "Save today's nutrition", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    editingFood?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.itemName) }
        var kcalText by remember(target.id) { mutableStateOf(target.calories?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editingFood = null },
            title = { Text(if (isThai) "แก้ไขรายการอาหาร" else "Edit food", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(if (isThai) "ชื่ออาหาร" else "Food name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = kcalText, onValueChange = { kcalText = it.filter { c -> c.isDigit() } }, singleLine = true, label = { Text("kcal") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.editFood(target.id, name)
                    kcalText.toIntOrNull()?.let { vm.editFoodCalories(target.id, it) }
                    editingFood = null
                }) { Text(if (isThai) "ยืนยัน" else "Confirm", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editingFood = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

@Composable
private fun EnergyHeader(name: String, avatarUrl: String?, age: String, intake: Int, target: Int, share: Float, isThai: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubChildAvatar(avatarUrl, 48.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (age.isNotBlank()) Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                            Text(age, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Text(
                        if (isThai) "เป้าหมายพลังงาน %,d kcal/วัน".format(target) else "Target %,d kcal/day".format(target),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = Primary.copy(0.1f)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isThai) "ได้รับแล้ว" else "So far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(if (isThai) "พลังงานรวมวันนี้" else "Energy today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%,d / %,d kcal".format(intake, target), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
            }
            LinearProgressIndicator(
                progress = { share.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(100.dp)),
                color = Primary, trackColor = Primary.copy(0.15f)
            )
            val remaining = target - intake
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        target <= 0 -> if (isThai) "ยังไม่มีเกณฑ์พลังงานสำหรับวัยนี้" else "No energy target for this age"
                        remaining > 0 -> if (isThai) "คงเหลือที่ต้องการอีก %,d kcal".format(remaining) else "%,d kcal to go".format(remaining)
                        else -> if (isThai) "เกินเป้าหมาย %,d kcal".format(-remaining) else "%,d kcal over".format(-remaining)
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (target > 0) {
                    val ok = share in 0.7f..1.2f
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info, null, tint = if (ok) FoodGood else FoodWatch, modifier = Modifier.size(16.dp))
                        Text(
                            when {
                                ok -> if (isThai) "อยู่ในเกณฑ์ดี" else "On track"
                                share < 0.7f -> if (isThai) "ยังได้รับน้อย" else "Below target"
                                else -> if (isThai) "เกินเกณฑ์" else "Above target"
                            },
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (ok) FoodGood else FoodWatch
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    query: String, onQuery: (String) -> Unit,
    portion: String, onPortion: (String) -> Unit,
    customPortion: Boolean, onCustomPortion: (Boolean) -> Unit,
    presets: List<String>,
    spilled: Boolean, onSpilled: (Boolean) -> Unit,
    spillFraction: Float, onSpillFraction: (Float) -> Unit,
    busy: Boolean, isThai: Boolean,
    onCamera: () -> Unit, onGallery: () -> Unit, onCalculate: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AddCircleOutline, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(if (isThai) "บันทึกข้อมูลอาหารมื้อนี้" else "Log this meal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onCamera, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(Icons.Filled.PhotoCamera, null, tint = Primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (isThai) "ถ่ายภาพ AI" else "Snap with AI", color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }

            // The camera is the fast path, so the card says so in words instead of leaving an icon
            // to be discovered.
            Surface(onClick = onCamera, shape = RoundedCornerShape(16.dp), color = Primary.copy(0.07f), enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) "เปิดกล้องถ่ายจานอาหารตอนนี้" else "Open the camera and snap the plate", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (isThai) "AI อ่านรูปแล้วเติมชื่ออาหารและแคลอรี่ให้เอง" else "The AI reads the photo and fills in the dish and calories",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onGallery, enabled = !busy) {
                        Icon(Icons.Filled.PhotoLibrary, if (isThai) "เลือกรูปจากคลัง" else "Pick from gallery", tint = Primary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            OutlinedTextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                placeholder = { Text(if (isThai) "หรือพิมพ์ชื่ออาหาร เช่น โจ๊กหมูสับ" else "Or type a dish, e.g. rice porridge", color = Outline) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
            )

            Text(if (isThai) "ปริมาณที่ป้อนให้น้อง" else "How much was served", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    val selected = !customPortion && portion == p
                    FilterChip(
                        selected = selected,
                        onClick = { onCustomPortion(false); onPortion(if (selected) "" else p) },
                        label = { Text(p, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.15f), selectedLabelColor = Primary)
                    )
                }
                FilterChip(
                    selected = customPortion,
                    onClick = { onCustomPortion(!customPortion); onPortion("") },
                    label = { Text(if (isThai) "กำหนดเอง" else "Custom", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.15f), selectedLabelColor = Primary)
                )
            }
            if (customPortion) {
                OutlinedTextField(
                    value = portion, onValueChange = onPortion, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isThai) "เช่น ครึ่งถ้วย, 5 ช้อนชา" else "e.g. half a bowl, 5 tsp", color = Outline) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.WaterDrop, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isThai) "น้องทำหกหรือไม่?" else "Did your child spill it?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (isThai) "ปรับลดแคลอรี่และสารอาหารให้ตรงกับที่ทานจริง" else "Trims the calories and nutrients to what went in",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = spilled, onCheckedChange = onSpilled,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                        )
                    }
                    if (spilled) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                0f to (if (isThai) "ไม่หกเลย" else "None"),
                                0.1f to (if (isThai) "หกเล็กน้อย (-10%)" else "A little (-10%)"),
                                0.5f to (if (isThai) "หกครึ่งหนึ่ง (-50%)" else "Half (-50%)")
                            ).forEach { (frac, label) ->
                                val selected = spillFraction == frac
                                Surface(
                                    onClick = { onSpillFraction(frac) }, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Primary.copy(0.15f) else MaterialTheme.colorScheme.surface,
                                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Primary) else null
                                ) {
                                    Text(
                                        label, Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onCalculate,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                enabled = !busy && query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "กำลังให้ AI คำนวณ..." else "Asking the AI...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "คำนวณคุณค่าอาหารด้วย AI" else "Calculate with AI", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MealListCard(
    foods: List<FoodLog>, vm: NutritionViewModel, isThai: Boolean, canDelete: Boolean,
    onEdit: (FoodLog) -> Unit, onDelete: (String) -> Unit, onSeeDay: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isThai) "รายการอาหารมื้อนี้" else "This meal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                    Text(
                        "${foods.size} " + (if (isThai) "รายการ · " else "items · ") + "${foods.sumOf { it.calories ?: 0 }} kcal",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold
                    )
                }
            }
            if (foods.isEmpty()) {
                Text(
                    if (isThai) "ยังไม่ได้บันทึกอะไรในมื้อนี้ เริ่มจากถ่ายรูปหรือพิมพ์ชื่ออาหารด้านบน"
                    else "Nothing logged in this sitting yet — snap a photo or type a dish above",
                    style = MaterialTheme.typography.bodySmall, color = Outline
                )
            } else {
                foods.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FoodThumb(f, vm, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(f.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val time = com.lullabyai.app.util.TimeUtils.localTime(f.createdAt) ?: ""
                            Text(
                                listOfNotNull(
                                    time.takeIf { it.isNotBlank() }?.let { if (isThai) "$it น." else it },
                                    displayMealLabel(f, isThai),
                                    "${f.calories ?: "-"} kcal",
                                    eatenLabel(f.eatenPct, isThai)
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall, color = Outline
                            )
                        }
                        IconButton(onClick = { onEdit(f) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, if (isThai) "แก้ไข" else "Edit", tint = Primary, modifier = Modifier.size(18.dp))
                        }
                        if (canDelete) IconButton(onClick = { onDelete(f.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.DeleteOutline, if (isThai) "ลบ" else "Delete", tint = Error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Surface(onClick = onSeeDay, shape = RoundedCornerShape(14.dp), color = Primary.copy(0.08f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ListAlt, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(if (isThai) "ดูรายการอาหารทั้งวัน" else "See the whole day", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Primary)
                    Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The photo the parent took, or the meal's own symbol when there is none. */
@Composable
internal fun FoodThumb(food: FoodLog, vm: NutritionViewModel, size: Dp) {
    var url by remember(food.id, food.photoPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(food.photoPath) { url = food.photoPath?.let { vm.foodPhotoUrl(it) } }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)),
        contentAlignment = Alignment.Center
    ) {
        val link = url
        if (link != null) {
            coil.compose.AsyncImage(
                model = link, contentDescription = food.itemName,
                modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(mealIcon(food.mealType), null, tint = Primary, modifier = Modifier.size(size * 0.45f))
        }
    }
}

internal fun mealIcon(mealType: String): ImageVector = when (mealType) {
    "breakfast" -> Icons.Filled.FreeBreakfast
    "lunch" -> Icons.Filled.LunchDining
    "dinner" -> Icons.Filled.DinnerDining
    "snack" -> Icons.Filled.Cookie
    else -> Icons.Filled.Restaurant
}

@Composable
private fun MacroCard(
    totals: DayNutrition.Totals?, isThai: Boolean, months: Int,
    targetMacros: Triple<Int, Int, Int>, targetCalciumMg: Int, targetIronMg: Int,
    sodiumMax: Double?, breastMilkOnly: Boolean
) {
    val (targetCarb, targetProtein, targetFat) = targetMacros
    val fibreTarget = DayNutrition.fibreTargetG(months)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.PieChart, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(if (isThai) "สารอาหารหลัก 4 เสา" else "The four macronutrients", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(if (isThai) "เกณฑ์ DRI 2563" else "Thai DRI 2020", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (breastMilkOnly) {
                Text(
                    if (isThai) "0–5 เดือน: นมแม่ให้สารอาหารครบตามเกณฑ์ ยังไม่ต้องนับทีละตัว" else "0–5 months: milk covers it; nothing to count yet",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val cells = listOf(
                    Triple(if (isThai) "โปรตีน" else "Protein", "protein", targetProtein.toDouble()),
                    Triple(if (isThai) "คาร์โบไฮเดรต" else "Carbs", "carb", targetCarb.toDouble()),
                    Triple(if (isThai) "ไขมันดี" else "Fat", "fat", targetFat.toDouble()),
                    Triple(if (isThai) "ใยอาหาร" else "Fibre", "fibre", fibreTarget ?: 0.0)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.take(2).forEach { (label, key, target) -> MacroCell(Modifier.weight(1f), label, totals?.amount?.get(key), target, isThai) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.drop(2).forEach { (label, key, target) -> MacroCell(Modifier.weight(1f), label, totals?.amount?.get(key), target, isThai) }
                }
                if (fibreTarget == null) Text(
                    if (isThai) "ใยอาหาร: ยังไม่มีเกณฑ์สำหรับเด็กต่ำกว่า 2 ปี จึงแสดงเฉพาะปริมาณ" else "Fibre: no target below two years, so only the amount is shown",
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ca = totals?.amount?.get("calcium")
                    val fe = totals?.amount?.get("iron")
                    val mineralsOk = ca != null && fe != null && targetCalciumMg > 0 && targetIronMg > 0 &&
                        ca >= targetCalciumMg * 0.9 && fe >= targetIronMg * 0.9
                    MiniPill(
                        Modifier.weight(1f), Icons.Filled.Verified,
                        if (isThai) "แคลเซียม & ธาตุเหล็ก" else "Calcium & iron",
                        if (ca == null || fe == null) (if (isThai) "ยังไม่มีข้อมูล" else "No data") else "%.0f mg · %.1f mg".format(ca, fe),
                        if (mineralsOk) (if (isThai) "ครบถ้วน" else "Complete") else (if (isThai) "ยังไม่ครบเป้า" else "Below target"),
                        mineralsOk
                    )
                    val na = totals?.amount?.get("sodium")
                    val naOk = na != null && sodiumMax != null && na <= sodiumMax
                    MiniPill(
                        Modifier.weight(1f), Icons.Filled.Shield,
                        if (isThai) "โซเดียม" else "Sodium",
                        if (na == null) (if (isThai) "ยังไม่มีข้อมูล" else "No data") else "%.0f mg".format(na),
                        when {
                            sodiumMax == null -> if (isThai) "ไม่มีเกณฑ์" else "No limit given"
                            naOk -> (if (isThai) "ปลอดภัยต่อไตเด็ก < %.0f mg" else "Under %.0f mg").format(sodiumMax)
                            else -> (if (isThai) "เกินเกณฑ์ %.0f mg" else "Over %.0f mg").format(sodiumMax)
                        },
                        naOk
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroCell(modifier: Modifier, label: String, amount: Double?, target: Double, isThai: Boolean) {
    val share = if (target > 0 && amount != null) amount / target else null
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Primary.copy(0.06f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                amount?.let { if (it < 10) "%.1f g".format(it) else "%.0f g".format(it) } ?: "—",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary
            )
            Text(
                share?.let { "${(it * 100).toInt()}% " + (if (isThai) "เป้า" else "of target") } ?: (if (isThai) "ไม่มีเกณฑ์" else "no target"),
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
            LinearProgressIndicator(
                progress = { (share ?: 0.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
                color = Primary, trackColor = Primary.copy(0.15f)
            )
        }
    }
}

@Composable
private fun MiniPill(modifier: Modifier, icon: ImageVector, title: String, value: String, note: String, ok: Boolean) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = (if (ok) FoodGood else FoodWatch).copy(0.08f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = if (ok) FoodGood else FoodWatch, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdviceCard(text: String, allergies: String?, foods: List<String>, isThai: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.SmartToy, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(if (isThai) "คำแนะนำจาก AI" else "AI suggestion", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                    Text("NongRak AI", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)) {
                Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!allergies.isNullOrBlank()) {
                val hits = foods.mapNotNull { name -> allergyHit(name, allergies)?.let { name to it } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, null, tint = if (hits.isEmpty()) Outline else Error, modifier = Modifier.size(18.dp))
                    Text(
                        if (hits.isEmpty()) (if (isThai) "ประวัติแพ้ที่บันทึกไว้: $allergies" else "Recorded allergies: $allergies")
                        else (if (isThai) "ระวัง: " else "Careful: ") + hits.joinToString(", ") { "${it.first} (${it.second})" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Error,
                        fontWeight = if (hits.isEmpty()) FontWeight.Normal else FontWeight.Bold
                    )
                }
            }
        }
    }
}

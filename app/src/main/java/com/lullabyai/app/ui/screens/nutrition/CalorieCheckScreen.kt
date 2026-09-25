@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.repository.NutritionAiService
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.HubChildAvatar
import com.lullabyai.app.ui.screens.child.hubAgeLabel
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.DayNutrition
import com.lullabyai.app.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Checking calories — Stitch "ระบบวิเคราะห์อาหารเด็กเล็กด้วย Vision AI". Type a dish or photograph it,
 * and the page reports the portion, its energy against today's remaining target, the four macros
 * and the minerals that matter at this age.
 *
 * Where the numbers come from is stated rather than scored: when the dish matches the กรมอนามัย food
 * table the figures are the book's, otherwise they are the AI's estimate. No accuracy percentage is
 * shown, because a model's confidence in itself is not a measurement.
 */
@Composable
fun CalorieCheckScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var food by remember { mutableStateOf("") }
    val r = ui.calorieCheck

    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }
    LaunchedEffect(ui.checkedFood) { if (ui.checkedFood.isNotBlank()) food = ui.checkedFood }
    LaunchedEffect(ui.checkPhotoFailed) {
        if (ui.checkPhotoFailed) {
            snackbars.showSnackbar(if (isThai) "ไม่พบอาหารในรูป ลองถ่ายให้เห็นจานชัดขึ้น" else "No food recognized — try a clearer shot of the plate")
        }
    }

    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (ok && uri != null) {
            val bytes = com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri)
            if (bytes != null) vm.runCalorieCheckFromPhoto(bytes, lang)
            else scope.launch { snackbars.showSnackbar(if (isThai) "อ่านไฟล์รูปไม่สำเร็จ" else "Could not read the photo file") }
        }
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "check_${System.currentTimeMillis()}.jpg")
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
            if (bytes != null) vm.runCalorieCheckFromPhoto(bytes, lang)
        }
    }

    val months = vm.ageMonths()
    val targetKcal = ui.dri?.energyKcal ?: ui.targetKcal
    val eatenToday = vm.dayIntakeKcal() ?: ui.foods.sumOf { it.calories ?: 0 }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "ตรวจสอบอาหารและสารอาหาร" else "Check a dish",
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------- who this is for, and the day's target
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubChildAvatar(ui.child?.avatarUrl, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(ui.child?.name.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            hubAgeLabel(ui.child?.birthdate, isThai).ifBlank { if (isThai) "ยังไม่ได้ระบุวันเกิด" else "No birthday set" },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Bolt, null, tint = Primary, modifier = Modifier.size(15.dp))
                            Text(
                                if (targetKcal > 0) "%,d kcal/${if (isThai) "วัน" else "day"}".format(targetKcal)
                                else (if (isThai) "ไม่มีเกณฑ์" else "no target"),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary, maxLines = 1
                            )
                        }
                    }
                }
            }

            // ---------------- the input card
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Biotech, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            if (isThai) "วิเคราะห์อาหารเด็กเล็กด้วย Vision AI" else "Analyse a child's dish with Vision AI",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedTextField(
                        value = food, onValueChange = { food = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                        placeholder = { Text(if (isThai) "เช่น ข้าวผัดไข่ใส่ฟักทองและไก่บด (150 กรัม)" else "e.g. pumpkin and chicken fried rice (150 g)", color = Outline) },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                    )
                    Text(
                        if (isThai) "บอกปริมาณมาด้วยจะแม่นขึ้น เช่น 1 ชามเล็ก หรือ 150 กรัม" else "Say how much, e.g. one small bowl or 150 g, and the answer gets closer",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SourceButton(Modifier.weight(1f), Icons.Filled.CameraAlt, if (isThai) "สแกนกล้องสด AI" else "Scan with camera", !ui.checkLoading, openCamera)
                        SourceButton(
                            Modifier.weight(1f), Icons.Filled.PhotoLibrary, if (isThai) "อัปโหลดภาพอาหาร" else "Upload a photo", !ui.checkLoading
                        ) { galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }
                    Button(
                        onClick = { vm.runCalorieCheck(food, lang) },
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        enabled = food.isNotBlank() && !ui.checkLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (ui.checkLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "กำลังวิเคราะห์..." else "Analysing...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "วิเคราะห์สารอาหารทันที" else "Analyse now", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (ui.checkLoading) {
                        OutlinedButton(
                            onClick = { vm.cancelCalorieCheck() },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Error)
                        ) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "ยกเลิก" else "Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (r == null) {
                Surface(shape = RoundedCornerShape(18.dp), color = Primary.copy(0.06f)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, null, tint = Primary)
                        Text(
                            if (isThai) "พิมพ์ชื่ออาหารหรือถ่ายรูปจานอาหาร แล้วระบบจะบอกพลังงานและสารอาหารของมื้อนั้น เทียบกับที่ลูกควรได้รับต่อวัน"
                            else "Type a dish or photograph the plate, and this page reports its energy and nutrients against what your child needs in a day.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                ResultCard(r, targetKcal, eatenToday, months, ui.targetMacros, isThai)
                MineralCard(r, months, ui.targetCalciumMg, ui.targetIronMg, ui.dri?.ranges?.get("sodium")?.endInclusive, isThai)
                AdviceCard(r, ui.allergies, ui.child?.allergies, isThai)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SourceButton(modifier: Modifier, icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(14.dp), color = Primary.copy(0.07f)
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary, maxLines = 2)
        }
    }
}

@Composable
private fun ResultCard(
    r: NutritionAiService.CalorieCheck, targetKcal: Int, eatenToday: Int, months: Int,
    targetMacros: Triple<Int, Int, Int>, isThai: Boolean
) {
    val share = if (targetKcal > 0) r.kcal.toFloat() / targetKcal else 0f
    val remaining = targetKcal - eatenToday
    val targetCarb = targetMacros.first
    val targetProtein = targetMacros.second
    val targetFat = targetMacros.third

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Primary))
                    Text(
                        (if (isThai) "ตรวจพบ: " else "Detected: ") + mealLabel(r.meal ?: "other", isThai),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Where the numbers came from, instead of an invented accuracy score.
                Surface(shape = RoundedCornerShape(100.dp), color = (if (r.fromTable != null) FoodGood else Primary).copy(0.13f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (r.fromTable != null) Icons.Filled.MenuBook else Icons.Filled.AutoAwesome, null,
                            tint = if (r.fromTable != null) FoodGood else Primary, modifier = Modifier.size(13.dp)
                        )
                        Text(
                            if (r.fromTable != null) (if (isThai) "อ้างอิงตารางกรมอนามัย" else "From the food table")
                            else (if (isThai) "ประเมินโดย AI" else "AI estimate"),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (r.fromTable != null) FoodGood else Primary, maxLines = 1
                        )
                    }
                }
            }

            Text(r.itemName.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    r.portionText ?: r.portionGrams?.let { if (isThai) "ประมาณ $it กรัม" else "about $it g" },
                    r.fromTable?.let { (if (isThai) "เทียบกับ: " else "matched to: ") + it }
                ).joinToString(" • ").ifBlank { if (isThai) "ไม่ได้ระบุปริมาณ" else "No portion given" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(shape = RoundedCornerShape(16.dp), color = Primary.copy(0.06f)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (isThai) "พลังงานรวมในมื้อนี้" else "Energy in this dish", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("%,d".format(r.kcal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                            Text(" kcal", style = MaterialTheme.typography.bodyMedium, color = Primary)
                        }
                        Text(
                            if (targetKcal > 0) (if (isThai) "%.1f%% ของเป้าหมายประจำวัน" else "%.1f%% of the daily target").format(share * 100)
                            else (if (isThai) "ยังไม่มีเกณฑ์พลังงานสำหรับวัยนี้" else "No energy target for this age"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (targetKcal > 0) {
                        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { share.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(),
                                color = Primary, strokeWidth = 8.dp, trackColor = Primary.copy(0.18f)
                            )
                            Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (targetKcal > 0) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.DonutLarge, null, tint = Outline, modifier = Modifier.size(16.dp))
                        Text(if (isThai) "พลังงานคงเหลือวันนี้" else "Left for today", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (remaining > 0) (if (isThai) "เหลืออีก %,d kcal" else "%,d kcal left").format(remaining)
                            else (if (isThai) "เกินแล้ว %,d kcal" else "%,d kcal over").format(-remaining),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = if (remaining > 0) Primary else FoodWatch
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroCell(Modifier.weight(1f), Icons.Filled.Egg, if (isThai) "โปรตีน" else "Protein", r.protein.toDouble(), targetProtein.toDouble(), isThai, Primary)
                MacroCell(Modifier.weight(1f), Icons.Filled.BakeryDining, if (isThai) "คาร์บ" else "Carbs", r.carb.toDouble(), targetCarb.toDouble(), isThai, PrimaryContainer)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroCell(Modifier.weight(1f), Icons.Filled.WaterDrop, if (isThai) "ไขมันดี" else "Fat", r.fat.toDouble(), targetFat.toDouble(), isThai, FoodWatch)
                MacroCell(
                    Modifier.weight(1f), Icons.Filled.Grass, if (isThai) "ใยอาหาร" else "Fibre",
                    r.fibreG, DayNutrition.fibreTargetG(months) ?: 0.0, isThai, FoodGood
                )
            }
        }
    }
}

@Composable
private fun MacroCell(
    modifier: Modifier, icon: ImageVector, label: String, amount: Double?, target: Double,
    isThai: Boolean, tint: Color
) {
    val share = if (target > 0 && amount != null) amount / target else null
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = tint.copy(0.07f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    amount?.let { if (it < 10) "%.1f g".format(it) else "%.0f g".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { (share ?: 0.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
                color = tint, trackColor = tint.copy(0.2f)
            )
            Text(
                share?.let { "${(it * 100).roundToInt()}% " + (if (isThai) "ของที่ควรได้ต่อวัน" else "of the daily need") }
                    ?: (if (isThai) "ไม่มีข้อมูล" else "not known"),
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
        }
    }
}

@Composable
private fun MineralCard(
    r: NutritionAiService.CalorieCheck, months: Int, targetCalciumMg: Int, targetIronMg: Int,
    sodiumMax: Double?, isThai: Boolean
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Science, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(
                    if (isThai) "สารอาหารสำคัญสำหรับวัยนี้" else "Minerals that matter at this age",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
            }
            MineralRow(
                Icons.Filled.Bloodtype, if (isThai) "ธาตุเหล็ก (Iron)" else "Iron",
                if (isThai) "บำรุงเม็ดเลือดและสมอง" else "blood and brain",
                r.ironMg, targetIronMg.toDouble(), "mg", isThai
            )
            MineralRow(
                Icons.Filled.LocalDrink, if (isThai) "แคลเซียม (Calcium)" else "Calcium",
                if (isThai) "บำรุงกระดูกและฟันน้ำนม" else "bones and first teeth",
                r.calciumMg, targetCalciumMg.toDouble(), "mg", isThai
            )
            // Sodium is a ceiling, not a goal, so it is judged against the DRI's upper figure and
            // stays quiet when the dish's sodium is unknown rather than calling it low.
            val sodium = r.sodiumMg
            val ok = sodium != null && sodiumMax != null && sodium <= sodiumMax
            Surface(shape = RoundedCornerShape(14.dp), color = (if (ok) FoodGood else Outline).copy(0.09f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (ok) Icons.Filled.Verified else Icons.Filled.Info, null, tint = if (ok) FoodGood else Outline, modifier = Modifier.size(18.dp))
                    Column {
                        Text(
                            when {
                                sodium == null -> if (isThai) "ยังไม่ทราบปริมาณโซเดียมของเมนูนี้" else "Sodium for this dish is not known"
                                sodiumMax == null -> (if (isThai) "โซเดียม %.0f mg (ยังไม่มีเกณฑ์สำหรับวัยนี้)" else "Sodium %.0f mg (no limit for this age)").format(sodium)
                                ok -> (if (isThai) "โซเดียม %.0f mg อยู่ในเกณฑ์ของวัยนี้" else "Sodium %.0f mg, within the range for this age").format(sodium)
                                else -> (if (isThai) "โซเดียม %.0f mg สูงกว่าเกณฑ์ %.0f mg" else "Sodium %.0f mg is above the %.0f mg limit").format(sodium, sodiumMax)
                            },
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = if (ok) FoodGood else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (isThai) "ไตของเด็กเล็กขับโซเดียมได้น้อยกว่าผู้ใหญ่ ควรเลี่ยงการเติมเกลือและน้ำตาล"
                            else "A small child's kidneys clear sodium less well than an adult's; avoid adding salt or sugar",
                            style = MaterialTheme.typography.labelSmall, color = Outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MineralRow(icon: ImageVector, title: String, why: String, amount: Double?, target: Double, unit: String, isThai: Boolean) {
    val share = if (target > 0 && amount != null) amount / target else null
    val badge: String
    val badgeColor: Color
    when {
        share == null -> { badge = if (isThai) "ไม่มีข้อมูล" else "not known"; badgeColor = Outline }
        share >= 0.9 -> { badge = if (isThai) "ครบตามวัน" else "a full day"; badgeColor = FoodGood }
        share >= 0.3 -> { badge = if (isThai) "พอเหมาะ" else "a good share"; badgeColor = FoodGood }
        else -> { badge = if (isThai) "ได้เล็กน้อย" else "a little"; badgeColor = Outline }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Primary.copy(0.05f)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                amount?.let { (if (it < 10) "%.1f" else "%.0f").format(it) + " $unit" } ?: "—",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.14f)) {
                Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AdviceCard(
    r: NutritionAiService.CalorieCheck,
    allergyRecords: List<Allergy>,
    profileAllergies: String?,
    isThai: Boolean
) {
    val dish = r.itemName.orEmpty()
    val hits = (allergyRecords.map { it.name }.filter { it.isNotBlank() && dish.contains(it, ignoreCase = true) } +
        listOfNotNull(allergyHit(dish, profileAllergies))).distinct()
    val known = allergyRecords.isNotEmpty() || !profileAllergies.isNullOrBlank()

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SmartToy, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(if (isThai) "คำแนะนำจาก NongRak AI" else "NongRak AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (isThai) "สำหรับเมนูนี้โดยเฉพาะ" else "About this dish", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (r.tip.isNotBlank()) {
                Surface(shape = RoundedCornerShape(14.dp), color = Primary.copy(0.06f)) {
                    Text(r.tip, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // States what the check actually did — matched the dish NAME against this child's own
            // records — instead of declaring the food free of allergens.
            Surface(shape = RoundedCornerShape(12.dp), color = (if (hits.isEmpty()) Outline else Error).copy(0.09f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (hits.isEmpty()) Icons.Filled.Shield else Icons.Filled.Warning, null, tint = if (hits.isEmpty()) Outline else Error, modifier = Modifier.size(17.dp))
                    Text(
                        when {
                            hits.isNotEmpty() ->
                                (if (isThai) "ระวัง: ชื่อเมนูตรงกับประวัติแพ้ " else "Careful: this dish matches a recorded allergy — ") + hits.joinToString(", ")
                            !known -> if (isThai) "ยังไม่มีประวัติแพ้ในระบบ จึงยังตรวจสอบให้ไม่ได้" else "No allergy record yet, so nothing could be checked"
                            else -> if (isThai) "ชื่อเมนูไม่ตรงกับประวัติแพ้ที่บันทึกไว้ (ตรวจจากชื่อเท่านั้น ไม่ได้ตรวจส่วนผสมทั้งหมด)"
                            else "The dish name does not match any recorded allergy (name only — not every ingredient)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Error,
                        fontWeight = if (hits.isEmpty()) FontWeight.Normal else FontWeight.Bold
                    )
                }
            }
        }
    }
}

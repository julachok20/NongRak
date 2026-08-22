@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.io.ByteArrayOutputStream

/**
 * Parent-facing calorie check (design: 06.Re-Nutrition/calorie_check_ai_calculation).
 * Type any dish → AI estimates total kcal + macros + a tip, with a generated photo.
 */
@Composable
fun CalorieCheckScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val context = LocalContext.current
    var food by remember { mutableStateOf("") }
    val r = ui.calorieCheck

    // Photo-check fills the food field with the AI-identified dish name, same as a typed check.
    LaunchedEffect(ui.checkedFood) { if (ui.checkedFood.isNotBlank()) food = ui.checkedFood }
    LaunchedEffect(ui.checkPhotoFailed) {
        if (ui.checkPhotoFailed) {
            Toast.makeText(context, if (isThai) "ไม่พบอาหารในรูปภาพ ลองถ่ายใหม่อีกครั้ง" else "No food recognized in the photo — try again", Toast.LENGTH_SHORT).show()
        }
    }

    fun bitmapToJpeg(bmp: Bitmap): ByteArray = ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray() }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) vm.runCalorieCheckFromPhoto(bitmapToJpeg(bmp), lang)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri)
            if (bytes != null) {
                vm.runCalorieCheckFromPhoto(bytes, lang)
            }
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

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "ตรวจสอบแคลอรี่" else "Calorie check",
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { openCamera() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(100.dp),
                    enabled = !ui.checkLoading
                ) {
                    Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "ถ่ายรูปอาหาร" else "Take photo", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(100.dp),
                    enabled = !ui.checkLoading
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "เลือกรูปภาพ" else "Upload photo", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            // Explicit Cancel for the in-flight photo/AI check — user asked for a real button
            // rather than relying on the back button while a capture/upload is processing.
            if (ui.checkLoading) {
                OutlinedButton(
                    onClick = { vm.cancelCalorieCheck() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Error)
                ) {
                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "ยกเลิก" else "Cancel", fontWeight = FontWeight.Bold)
                }
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

            AIInsightCard(
                text = r?.tip?.takeIf { it.isNotBlank() }
                    ?: (if (isThai) "พิมพ์ชื่ออาหารด้านบน แล้วให้ AI วิเคราะห์สัดส่วนสารอาหารที่เหมาะสมสำหรับคุณ"
                    else "Type a food above and let AI analyze its nutrition for you."),
                isLoading = ui.checkLoading
            )

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

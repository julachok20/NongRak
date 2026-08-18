@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.io.ByteArrayOutputStream

@Composable
fun RefrigeratorScannerScreen(
    vm: NutritionViewModel,
    lang: String,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = lang.startsWith("th")
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()
    val child = ui.child

    LaunchedEffect(child?.id) {
        vm.loadFridgeIngredients()
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var newIngredientInput by remember { mutableStateOf("") }
    var showSyncDialog by remember { mutableStateOf(false) }

    // Helper to read bytes from Uri
    fun uriToBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            output.toByteArray()
        }
    }.getOrNull()

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val bytes = uriToBytes(uri)
            if (bytes != null) {
                vm.scanFridgePhoto(bytes, lang) { detected ->
                    if (detected.isNotEmpty()) {
                        Toast.makeText(context, if (isThai) "ตรวจพบวัตถุดิบ ${detected.size} รายการ" else "Detected ${detected.size} ingredients", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (isThai) "ไม่พบวัตถุดิบที่ระบุได้ชัดเจน สามารถพิมพ์เพิ่มเองได้ค่ะ" else "No clear ingredients detected. You can add them manually.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Camera Capture
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            val bytes = output.toByteArray()
            vm.scanFridgePhoto(bytes, lang) { detected ->
                if (detected.isNotEmpty()) {
                    Toast.makeText(context, if (isThai) "ตรวจพบวัตถุดิบ ${detected.size} รายการ" else "Detected ${detected.size} ingredients", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (isThai) "ไม่พบวัตถุดิบที่ระบุได้ชัดเจน สามารถพิมพ์เพิ่มเองได้ค่ะ" else "No clear ingredients detected. You can add them manually.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "arPulse")
    val pulseBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBorderAlpha"
    )

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ตู้เย็นอัจฉริยะ" else "Smart Refrigerator",
                onBack = onBack,
                actions = {
                    if (ui.siblingChildren.isNotEmpty()) {
                        IconButton(onClick = { showSyncDialog = true }) {
                            Icon(Icons.Filled.SyncAlt, contentDescription = "ซิงค์กับพี่น้อง", tint = Primary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    PrimaryButton(
                        text = if (isThai) "แนะนำเมนูจากวัตถุดิบเหล่านี้" else "Get AI Recipes from Ingredients",
                        enabled = ui.fridgeIngredients.isNotEmpty() && !ui.fridgeScanning,
                        onClick = {
                            vm.ensureMenuRecommendations(lang)
                            navController.navigate(Screen.RecommendedFood.route)
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Scanner / AR Viewfinder Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "ภาพตู้เย็น",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Simulated high-tech camera preview with AR overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                    )
                                )
                        )
                    }

                    // AR Tags Overlay Simulation / Display
                    if (ui.fridgeScanning) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                                Text(
                                    if (isThai) "AI กำลังสแกนและวิเคราะห์วัตถุดิบ..." else "AI is analyzing refrigerator ingredients...",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        // AR Bounding Tags (sample overlays matching Stitch design)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 24.dp, top = 28.dp)
                        ) {
                            ArTagItem("บรอกโคลี", Icons.Filled.Eco, pulseBorderAlpha)
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 24.dp, top = 36.dp)
                        ) {
                            ArTagItem("นมสด", Icons.Filled.WaterDrop, pulseBorderAlpha)
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 32.dp, bottom = 48.dp)
                        ) {
                            ArTagItem("แครอท", Icons.Filled.Grass, pulseBorderAlpha)
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 28.dp, bottom = 40.dp)
                        ) {
                            ArTagItem("ไข่ไก่ / ผลไม้", Icons.Filled.Egg, pulseBorderAlpha)
                        }
                    }

                    // Shutter / Actions Overlay at the bottom of Viewfinder
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Gallery Button
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = "เลือกรูปภาพ", tint = Color.White)
                        }

                        // Shutter Button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.35f))
                                .clickable { takePhotoLauncher.launch(null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }

                        // Retake / Clear
                        IconButton(
                            onClick = {
                                selectedImageUri = null
                                takePhotoLauncher.launch(null)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "ถ่ายรูปใหม่", tint = Color.White)
                        }
                    }
                }
            }

            // Quick Manual Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isThai) "เพิ่มวัตถุดิบด้วยตนเอง" else "Add Ingredients Manually",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newIngredientInput,
                            onValueChange = { newIngredientInput = it },
                            placeholder = { Text(if (isThai) "เช่น อกไก่, ฟักทอง, เต้าหู้" else "e.g. Chicken breast, Pumpkin") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newIngredientInput.isNotBlank()) {
                                    vm.addFridgeIngredient(newIngredientInput.trim(), lang = lang)
                                    newIngredientInput = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "เพิ่ม")
                        }
                    }
                }
            }

            // Available Fridge Inventory List & Chips
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Kitchen, contentDescription = null, tint = Primary)
                            Text(
                                text = if (isThai) "วัตถุดิบที่มีในตู้เย็น (${ui.fridgeIngredients.size})" else "Ingredients in Fridge (${ui.fridgeIngredients.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (ui.fridgeIngredients.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isThai) "ยังไม่มีวัตถุดิบในรายการ ถ่ายรูปตู้เย็นหรือพิมพ์เพิ่มด้านบนได้เลยค่ะ" else "No ingredients listed yet. Take a fridge photo or type to add.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Flow row of ingredient chips
                        OptInFlowRow(
                            items = ui.fridgeIngredients,
                            onDelete = { id -> vm.removeFridgeIngredient(id, lang = lang) }
                        )
                    }
                }
            }
        }
    }

    // Sibling Sync Dialog
    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = {
                Text(
                    text = if (isThai) "ซิงค์วัตถุดิบไปยังพี่น้อง" else "Sync Ingredients to Sibling",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isThai) "เลือกโปรไฟล์ลูกที่ต้องการแชร์รายการวัตถุดิบในตู้เย็นชุดนี้:" else "Select child to copy these fridge ingredients to:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    ui.siblingChildren.forEach { sib ->
                        Surface(
                            onClick = {
                                vm.syncFridgeToSibling(sib.id, lang = lang) {
                                    Toast.makeText(context, if (isThai) "ซิงค์วัตถุดิบไปยัง ${sib.name} สำเร็จ" else "Synced to ${sib.name}", Toast.LENGTH_SHORT).show()
                                    showSyncDialog = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.ChildCare, null, tint = Primary)
                                Text(sib.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }) {
                    Text(if (isThai) "ปิด" else "Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ArTagItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, pulseAlpha: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF5A78D1).copy(alpha = 0.9f))
            .border(
                BorderStroke(1.5.dp, Color.White.copy(alpha = pulseAlpha)),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OptInFlowRow(
    items: List<com.lullabyai.app.data.model.FridgeIngredient>,
    onDelete: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.ingredientName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onDelete(item.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "ลบ",
                                    tint = Primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.shadow
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
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Stitch 49.Diary: Child Recognition Setup (ตั้งค่าการจดจำใบหน้าลูกน้อย)
 * Reference photo enrollment for NongRak AI face recognition and auto-tagging.
 */
@Composable
fun ChildRecognitionSetupScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val isThai = s.isThai
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }

    var primaryUri by remember { mutableStateOf<String?>(null) }
    var refUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        primaryUri = settingsManager.childRecognitionPrimary.first()
        refUris = settingsManager.childRecognitionRefs.first()
    }

    val primaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { primaryUri = it.toString() }
    }

    val refPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            if (refUris.size < 5) {
                refUris = refUris + it.toString()
            }
        }
    }

    val totalPhotos = (if (primaryUri != null) 1 else 0) + refUris.size
    val maxPhotos = 5
    val progressFraction = (totalPhotos.toFloat() / maxPhotos.toFloat()).coerceIn(0f, 1f)
    val progressPercent = (progressFraction * 100).toInt()

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ตั้งค่าการจดจำใบหน้า" else "Child Recognition Setup",
                onBack = onBack,
                navController = navController,
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.GalleryAiSettings.route) }) {
                        Icon(Icons.Filled.Settings, if (isThai) "ตั้งค่า" else "Settings", tint = Primary)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            if (primaryUri == null && refUris.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    if (isThai) "กรุณาอัปโหลดรูปถ่ายใบหน้าอย่างน้อย 1 รูป" else "Please upload at least 1 face photo",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            scope.launch {
                                isSaving = true
                                settingsManager.setChildRecognitionPrimary(primaryUri)
                                settingsManager.setChildRecognitionRefs(refUris)
                                settingsManager.setGalleryAutoCheck(true)
                                isSaving = false
                                Toast.makeText(
                                    context,
                                    if (isThai) "บันทึกข้อมูลและเปิดใช้งาน AI Auto-Check เรียบร้อยแล้ว ✨" else "Child recognition saved and AI Auto-Check enabled ✨",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onBack()
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(8.dp, RoundedCornerShape(100.dp), spotColor = Primary),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Save, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isThai) "บันทึกและเริ่มใช้งาน" else "Save and Start Using",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Hero Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFE1E0FF), Color(0xFFFBF8FF), Color(0xFFDEE0FF))
                            )
                        )
                        .border(2.dp, Color.White, CircleShape)
                        .shadow(6.dp, CircleShape, spotColor = Primary.copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Face,
                        null,
                        tint = Primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = if (isThai) "NongRak AI ต้องการภาพใบหน้า" else "NongRak AI Needs a Face",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isThai)
                        "เพื่อให้ AI ร้อยเรียงเรื่องราวและแท็กรูปภาพของลูกน้อยได้อย่างแม่นยำ กรุณาอัปโหลดรูปถ่ายอ้างอิงที่เห็นใบหน้าชัดเจน 3-5 รูป"
                    else
                        "To weave your child's stories with precision, our AI requires a few clear reference photos. This helps in auto-tagging memories accurately across the archive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            // Primary Upload Area
            Card(
                onClick = { primaryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.25f)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFBF8FF),
                                    Color(0xFFECECFF).copy(alpha = 0.5f),
                                    Color(0xFFDEE0FF).copy(alpha = 0.4f)
                                )
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (primaryUri != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(2.dp, Primary, RoundedCornerShape(20.dp))
                            ) {
                                AsyncImage(
                                    model = Uri.parse(primaryUri),
                                    contentDescription = "Primary Child Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    color = Primary,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(16.dp))
                                Text(
                                    if (isThai) "แตะเพื่อเปลี่ยนรูปหลัก (Primary Photo)" else "Tap to change primary photo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CloudUpload,
                                    null,
                                    tint = Primary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            Text(
                                if (isThai) "อัปโหลดรูปถ่ายหลัก (Primary Photo)" else "Upload Primary Photo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Text(
                                "JPEG OR PNG, MAX 10MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = Outline,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Reference Photos Gallery
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isThai) "แกลเลอรีอ้างอิง (Reference Gallery)" else "Reference Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            text = if (isThai) "อัปโหลดรูปที่เห็นหน้าชัดเจน 3-5 รูป" else "Upload 3-5 clear face photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = PrimaryContainer
                    ) {
                        Text(
                            text = "$totalPhotos/$maxPhotos UPLOADED",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Gallery Grid (4 slots)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (i in 0 until 4) {
                        val uri = refUris.getOrNull(i)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = if (uri != null) 1.5.dp else 1.dp,
                                    color = if (uri != null) Primary.copy(0.4f) else OutlineVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    if (uri == null) {
                                        refPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uri != null) {
                                AsyncImage(
                                    model = Uri.parse(uri),
                                    contentDescription = "Reference Photo $i",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Delete button
                                IconButton(
                                    onClick = {
                                        refUris = refUris.filterIndexed { index, _ -> index != i }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .background(Color.Black.copy(0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Surface(
                                    color = Primary,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.padding(2.dp))
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Add, null, tint = Primary.copy(0.5f), modifier = Modifier.size(24.dp))
                                    Text(
                                        if (isThai) "เพิ่มรูป" else "Add",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Outline,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Progress Indicator
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(100.dp)),
                        color = Primary,
                        trackColor = SurfaceContainerHigh
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (progressPercent >= 60) (if (isThai) "ความแม่นยำอยู่ในเกณฑ์ดีเยี่ยม" else "High accuracy achieved")
                            else (if (isThai) "เพิ่มอีก ${maxPhotos - totalPhotos} รูปเพื่อความแม่นยำสูงสุด" else "Add ${maxPhotos - totalPhotos} more photos for best accuracy"),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = "$progressPercent% COMPLETE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Secure & Private Info Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.04f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isThai) "ปลอดภัยและเป็นส่วนตัว (Secure & Private)" else "Secure & Private",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            text = if (isThai)
                                "รูปภาพอ้างอิงของลูกน้อยจะถูกประมวลผลอย่างปลอดภัยเพื่อสร้างโมเดลจดจำส่วนบุคคล ไม่มีการนำไปเผยแพร่หรือใช้เพื่อวัตถุประสงค์อื่นนอกเหนือจากการจัดหมวดหมู่ความทรงจำในครอบครัวของคุณ"
                            else
                                "Your child's reference photos are processed locally to create a private recognition model. They are never shared publicly or used for any purpose other than curating your personal family archive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DiaryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Stitch 49.Diary: Gallery AI Settings (ตั้งค่าแกลเลอรี AI)
 * Controls auto-check with face recognition prerequisite, real Gallery & Drive sync, and preferences.
 */
@Composable
fun GalleryAiSettingsScreen(
    navController: NavController,
    onBack: () -> Unit,
    diaryVm: DiaryViewModel = viewModel()
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val isThai = s.isThai
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }

    var autoCheck by remember { mutableStateOf(false) }
    var autoMood by remember { mutableStateOf(true) }
    var detectFirstSteps by remember { mutableStateOf(true) }
    var smartHighlights by remember { mutableStateOf(false) }

    var hasFaceSetup by remember { mutableStateOf(false) }
    var facePhotoCount by remember { mutableStateOf(0) }
    var showFaceRequiredDialog by remember { mutableStateOf(false) }

    var syncCameraRoll by remember { mutableStateOf(true) }
    var syncCloudDrive by remember { mutableStateOf(true) }
    var customSources by remember { mutableStateOf<List<String>>(emptyList()) }

    var isScanning by remember { mutableStateOf(false) }
    var scanProgressCurrent by remember { mutableStateOf(0) }
    var scanProgressTotal by remember { mutableStateOf(0) }
    var scanSourceLabel by remember { mutableStateOf("") }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceNameInput by remember { mutableStateOf("") }

    // Load states
    LaunchedEffect(Unit) {
        val primary = settingsManager.childRecognitionPrimary.first()
        val refs = settingsManager.childRecognitionRefs.first()
        facePhotoCount = (if (!primary.isNullOrBlank()) 1 else 0) + refs.size
        hasFaceSetup = facePhotoCount > 0

        val storedAutoCheck = settingsManager.galleryAutoCheck.first()
        // If face is not set up, autoCheck cannot be true
        autoCheck = storedAutoCheck && hasFaceSetup
        if (storedAutoCheck && !hasFaceSetup) {
            settingsManager.setGalleryAutoCheck(false)
        }

        autoMood = settingsManager.galleryAutoMood.first()
        detectFirstSteps = settingsManager.galleryDetectFirstSteps.first()
        smartHighlights = settingsManager.gallerySmartHighlights.first()

        syncCameraRoll = settingsManager.syncCameraRoll.first()
        syncCloudDrive = settingsManager.syncCloudDrive.first()
        customSources = settingsManager.syncCustomSources.first()
    }

    // Photo Gallery Scanner launcher
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            isScanning = true
            scanProgressCurrent = 0
            scanProgressTotal = uris.size
            scanSourceLabel = if (isThai) "คลังรูปภาพในเครื่อง" else "Device Gallery"

            diaryVm.importFromSync(
                uris = uris,
                sourceName = scanSourceLabel,
                context = context,
                isThai = isThai,
                onProgress = { cur, tot ->
                    scanProgressCurrent = cur
                    scanProgressTotal = tot
                },
                onDone = { count ->
                    isScanning = false
                    Toast.makeText(
                        context,
                        if (isThai) "ซิงก์สำเร็จ $count ความทรงจำ ✨" else "Synced $count memories successfully ✨",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // Google Drive / Cloud files picker launcher
    val drivePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            isScanning = true
            scanProgressCurrent = 0
            scanProgressTotal = uris.size
            scanSourceLabel = if (isThai) "Google Drive ครอบครัว" else "Family Google Drive"

            diaryVm.importFromSync(
                uris = uris,
                sourceName = scanSourceLabel,
                context = context,
                isThai = isThai,
                onProgress = { cur, tot ->
                    scanProgressCurrent = cur
                    scanProgressTotal = tot
                },
                onDone = { count ->
                    isScanning = false
                    Toast.makeText(
                        context,
                        if (isThai) "ซิงก์จาก Drive สำเร็จ $count ไฟล์ ✨" else "Synced $count files from Drive ✨",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun handleToggleAutoCheck(enabled: Boolean) {
        if (enabled && !hasFaceSetup) {
            showFaceRequiredDialog = true
            return
        }
        autoCheck = enabled
        scope.launch { settingsManager.setGalleryAutoCheck(enabled) }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ตั้งค่าแกลเลอรี AI" else "Gallery Settings",
                onBack = onBack,
                navController = navController
            )
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
            // Header Description
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isThai) "ตั้งค่าแกลเลอรี AI (Gallery AI Settings)" else "Gallery Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    text = if (isThai)
                        "จัดการวิธีที่ NongRak AI ช่วยดูแล บันทึก และจัดระเบียบช่วงเวลาอันแสนวิเศษของครอบครัวคุณ"
                    else
                        "Manage how NongRak AI preserves and categorizes your family's magical moments.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            }

            // Master Toggle Card (Hero)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.25f)),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFBF8FF),
                                    Color(0xFFECECFF).copy(0.7f),
                                    Color(0xFFDEE0FF).copy(0.5f)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (isThai) "ตรวจเช็กอัตโนมัติ (Auto-Check)" else "Auto-Check Gallery",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                    Text(
                                        text = if (hasFaceSetup) (if (isThai) "พร้อมใช้งานระบบ AI" else "AI Ready")
                                        else (if (isThai) "จำเป็นต้องตั้งค่าใบหน้าก่อน" else "Face Setup Required"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasFaceSetup) Primary else Color(0xFFE65100),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Switch(
                                checked = autoCheck,
                                onCheckedChange = { handleToggleAutoCheck(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Primary,
                                    uncheckedThumbColor = Outline,
                                    uncheckedTrackColor = SurfaceContainerHigh
                                )
                            )
                        }

                        Text(
                            text = if (isThai)
                                "อนุญาตให้ NongRak AI ตรวจสอบรูปภาพล่าสุดเพื่อระบุและแท็กก้าวสำคัญของลูกน้อยโดยอัตโนมัติ ไม่พลาดทุกเสี้ยววินาทีแห่งความสุข"
                            else
                                "Allow NongRak AI to gently review your recent photos, automatically identifying and tagging precious milestones so no memory is lost to the scroll.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Child Face Recognition Link Card
            Card(
                onClick = { navController.navigate(Screen.ChildRecognitionSetup.route) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (hasFaceSetup) Primary.copy(0.2f) else Color(0xFFFFA000)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (hasFaceSetup) SecondaryContainer else Color(0xFFFFF3E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Face,
                            null,
                            tint = if (hasFaceSetup) Primary else Color(0xFFE65100),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isThai) "การจดจำใบหน้าลูก (Face Recognition)" else "Child Face Recognition",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }

                        Text(
                            text = if (hasFaceSetup)
                                (if (isThai) "ตั้งค่าแล้ว ($facePhotoCount รูปภาพอ้างอิง)" else "Configured ($facePhotoCount reference photos)")
                            else
                                (if (isThai) "⚠️ ยังไม่ได้ตั้งค่า — แตะเพื่อเพิ่มรูปภาพ" else "⚠️ Not configured — tap to setup"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasFaceSetup) Primary else Color(0xFFE65100),
                            fontWeight = if (hasFaceSetup) FontWeight.Normal else FontWeight.Bold
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (isThai) "จัดการ" else "Manage",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary)
                    }
                }
            }

            // Secondary AI Features Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isThai) "ฟีเจอร์ AI อัจฉริยะ (Smart AI Features)" else "Smart AI Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )

                // Sub-setting 1: Auto-tag moods
                FeatureToggleRow(
                    icon = Icons.Filled.SentimentSatisfied,
                    title = if (isThai) "แท็กอารมณ์อัตโนมัติ (Auto-Tagging Moods)" else "Auto-Tagging Moods",
                    subtitle = if (isThai) "ตรวจจับรอยยิ้มและอารมณ์ความรู้สึก" else "Detect emotional resonance",
                    checked = autoMood,
                    onCheckedChange = { value ->
                        autoMood = value
                        scope.launch { settingsManager.setGalleryAutoMood(value) }
                    }
                )

                // Sub-setting 2: Detect first steps
                FeatureToggleRow(
                    icon = Icons.Filled.DirectionsWalk,
                    title = if (isThai) "ตรวจจับก้าวแรก (Detect First Steps)" else "Detect First Steps",
                    subtitle = if (isThai) "ให้ความสำคัญพิเศษกับเหตุการณ์สำคัญ" else "Special priority for key events",
                    checked = detectFirstSteps,
                    onCheckedChange = { value ->
                        detectFirstSteps = value
                        scope.launch { settingsManager.setGalleryDetectFirstSteps(value) }
                    }
                )

                // Sub-setting 3: Smart highlights
                FeatureToggleRow(
                    icon = Icons.Filled.AutoStories,
                    title = if (isThai) "สร้างไฮไลต์รายเดือน (Smart Highlight Creation)" else "Smart Highlight Creation",
                    subtitle = if (isThai) "รวบรวมเรื่องราวและสมุดภาพรายเดือน" else "Compile monthly storybooks",
                    checked = smartHighlights,
                    onCheckedChange = { value ->
                        smartHighlights = value
                        scope.launch { settingsManager.setGallerySmartHighlights(value) }
                    }
                )
            }

            // Scan Preferences & Real Syncing Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isThai) "แหล่งที่มาและการซิงก์จริง (Real Scan Sources)" else "Scan & Sync Sources",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Camera Roll Card with Real Sync
                    RealSourceCard(
                        icon = Icons.Filled.PhotoLibrary,
                        title = if (isThai) "คลังรูปภาพ" else "Camera Roll",
                        subtitle = if (isThai) "แตะเพื่อสแกนรูป" else "Tap to sync",
                        active = syncCameraRoll,
                        isThai = isThai,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!hasFaceSetup) {
                                showFaceRequiredDialog = true
                            } else {
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                        }
                    )

                    // Cloud Drive Card with Real Sync
                    RealSourceCard(
                        icon = Icons.Filled.CloudDone,
                        title = if (isThai) "Google Drive" else "Cloud Drive",
                        subtitle = if (isThai) "แตะเพื่อนำเข้า" else "Tap to import",
                        active = syncCloudDrive,
                        isThai = isThai,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!hasFaceSetup) {
                                showFaceRequiredDialog = true
                            } else {
                                drivePicker.launch("*/*")
                            }

                        }
                    )

                    // Add Custom Source Card
                    AddSourceCard(
                        title = if (isThai) "เพิ่มแหล่ง" else "Add Source",
                        subtitle = if (isThai) "เชื่อมต่อไดรฟ์" else "New storage",
                        isThai = isThai,
                        modifier = Modifier.weight(1f),
                        onClick = { showAddSourceDialog = true }
                    )
                }

                // Custom Synced Sources List
                if (customSources.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isThai) "แหล่งข้อมูลที่เชื่อมต่อเพิ่มเติม:" else "Custom Connected Sources:",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                        customSources.forEach { sourceName ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Folder, null, tint = Primary, modifier = Modifier.size(20.dp))
                                        Text(sourceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                settingsManager.removeCustomSource(sourceName)
                                                customSources = customSources.filter { it != sourceName }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, null, tint = Outline, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Face Recognition Required Warning Dialog
    if (showFaceRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showFaceRequiredDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF3E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Face, null, tint = Color(0xFFE65100), modifier = Modifier.size(32.dp))
                }
            },
            title = {
                Text(
                    if (isThai) "จำเป็นต้องตั้งค่าใบหน้าก่อน" else "Face Setup Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = if (isThai)
                        "ก่อนเปิดใช้งานระบบ AI Auto-Check เพื่อค้นหาและคัดแยกโมเมนต์ของลูกน้อยโดยอัตโนมัติ กรุณาตั้งค่ารูปภาพอ้างอิงใบหน้าของลูกน้อย (3-5 รูป) ก่อนนะคะ เพื่อให้ NongRak AI ระบุตัวตนได้อย่างแม่นยำ"
                    else
                        "Before enabling AI Auto-Check to automatically curate your child's memories, please set up reference photos (3-5 images) so NongRak AI can accurately identify your child.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFaceRequiredDialog = false
                        navController.navigate(Screen.ChildRecognitionSetup.route)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(Icons.Filled.Face, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "ตั้งค่าใบหน้าตอนนี้" else "Set Up Face Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFaceRequiredDialog = false }) {
                    Text(if (isThai) "ไว้ภายหลัง" else "Later", color = Outline)
                }
            }
        )
    }

    // Add Custom Source Dialog
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = { Text(if (isThai) "เพิ่มแหล่งความทรงจำใหม่" else "Add Sync Source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isThai) "ระบุชื่อโฟลเดอร์หรือไดรฟ์ที่ต้องการเชื่อมต่อ (เช่น NAS ครอบครัว, iCloud Drive):"
                        else "Enter folder or drive name to connect:",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newSourceNameInput,
                        onValueChange = { newSourceNameInput = it },
                        placeholder = { Text(if (isThai) "เช่น NAS Family Photos" else "e.g. NAS Family Photos") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSourceNameInput.isNotBlank()) {
                            val trimmed = newSourceNameInput.trim()
                            scope.launch {
                                settingsManager.addCustomSource(trimmed)
                                customSources = (customSources + trimmed).distinct()
                            }
                            newSourceNameInput = ""
                        }
                        showAddSourceDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(if (isThai) "เพิ่มแหล่งข้อมูล" else "Add Source")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    // Scanning & Processing Progress Modal
    if (isScanning) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = Primary,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = if (isThai) "กำลังสแกนและประมวลผล..." else "Scanning & Curating...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Text(
                        text = if (isThai)
                            "NongRak AI กำลังวิเคราะห์ใบหน้าลูกน้อยและคัดแยกเหตุการณ์สำคัญจาก $scanSourceLabel ($scanProgressCurrent/$scanProgressTotal)"
                        else
                            "NongRak AI is analyzing child facial features and detecting milestones from $scanSourceLabel ($scanProgressCurrent/$scanProgressTotal)",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    LinearProgressIndicator(
                        progress = { if (scanProgressTotal > 0) scanProgressCurrent.toFloat() / scanProgressTotal else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(100.dp)),
                        color = Primary,
                        trackColor = SurfaceContainerHigh
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.7f)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Primary.copy(0.08f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Primary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = Outline,
                    uncheckedTrackColor = SurfaceContainerHigh
                )
            )
        }
    }
}

@Composable
private fun RealSourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    isThai: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.3f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontSize = 10.sp, maxLines = 1)
            if (active) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Primary
                ) {
                    Text(
                        if (isThai) "ซิงก์เลย" else "SYNC",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSourceCard(
    title: String,
    subtitle: String,
    isThai: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.3f)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Primary.copy(0.08f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, null, tint = Primary, modifier = Modifier.size(24.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontSize = 10.sp)
        }
    }
}

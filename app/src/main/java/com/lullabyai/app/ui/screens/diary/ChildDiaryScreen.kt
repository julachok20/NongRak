@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.DiaryMemory
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DiaryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Stitch 49.Diary: Enchanted Memories Hub & Special Memories (บันทึกความทรงจำ)
 * Upgraded with AI Auto-Check Gallery banner, Year-End Rewind, Story Reels carousel,
 * Milestones journey, and Daily Chapters timeline.
 */
@Composable
fun ChildDiaryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val isThai = s.isThai
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val state by vm.uiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var autoCheckEnabled by remember { mutableStateOf(false) }
    var hasFaceSetup by remember { mutableStateOf(false) }
    var showFaceRequiredDialog by remember { mutableStateOf(false) }
    var selectedReelMemory by remember { mutableStateOf<DiaryMemory?>(null) }

    val year = Calendar.getInstance().get(Calendar.YEAR)
    val buddhistYear = year + 543

    LaunchedEffect(Unit) {
        vm.load()
        val primary = settingsManager.childRecognitionPrimary.first()
        val refs = settingsManager.childRecognitionRefs.first()
        hasFaceSetup = !primary.isNullOrBlank() || refs.isNotEmpty()
        val storedAutoCheck = settingsManager.galleryAutoCheck.first()
        autoCheckEnabled = storedAutoCheck && hasFaceSetup
    }

    val filtered = remember(state.memories, query) {
        if (query.isBlank()) state.memories
        else state.memories.filter {
            listOfNotNull(it.title, it.description, it.milestoneTag)
                .any { t -> t.contains(query, ignoreCase = true) }
        }
    }

    // Story Reels: Video & Photo memories
    val storyReels = remember(filtered) {
        filtered.filter { it.mediaType == "video" || it.mediaType == "photo" || it.isFavorite }
            .ifEmpty { filtered.take(4) }
    }

    // Milestones
    val milestones = remember(filtered) {
        filtered.filter { it.isMilestone || !it.milestoneTag.isNullOrBlank() }
    }

    // Timeline notes
    val dailyChapters = remember(filtered) {
        filtered.filter { it.mediaType == "note" || (!it.isMilestone && it.mediaType != "video") }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "บันทึกความทรงจำ" else "Special Memories",
                onBack = onBack,
                navController = navController,
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(Icons.Filled.Notifications, if (isThai) "แจ้งเตือน" else "Notifications", tint = Primary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddMemory.route) },
                containerColor = Primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .shadow(12.dp, CircleShape, spotColor = Primary)
            ) {
                Icon(Icons.Filled.Add, if (isThai) "เพิ่ม" else "Add", modifier = Modifier.size(28.dp))
            }
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
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
        if (state.isLoading && state.memories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            // Header Title
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isThai) "ความทรงจำพิเศษ (Special Memories)" else "Special Memories",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Text(
                        text = if (isThai) "บันทึกและรวบรวมทุกช่วงเวลาอันล้ำค่าของลูกน้อย" else "Capture and cherish every precious moment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isThai) "ค้นหาความทรงจำ..." else "Search memories...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    trailingIcon = {
                        IconButton(onClick = { navController.navigate(Screen.SpecialMemoriesGallery.route) }) {
                            Icon(Icons.Filled.Tune, null, tint = Primary)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            // AI Auto-Check Gallery Banner Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                    elevation = CardDefaults.cardElevation(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF1A1B41),
                                        Color(0xFF3757AE)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(0.15f))
                                        .border(1.dp, Color.White.copy(0.3f), CircleShape),
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
                                        text = if (isThai) "AI Auto-Check Gallery" else "AI Auto-Check Gallery",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isThai) "Enchanted Archive: คัดแยกโมเมนต์อัตโนมัติ" else "Enchanted Archive: Smart sorting enabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(0.85f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = autoCheckEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked && !hasFaceSetup) {
                                            showFaceRequiredDialog = true
                                        } else {
                                            autoCheckEnabled = checked
                                            scope.launch { settingsManager.setGalleryAutoCheck(checked) }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Primary,
                                        checkedTrackColor = Color.White,
                                        uncheckedThumbColor = Color.White.copy(0.7f),
                                        uncheckedTrackColor = Color.White.copy(0.2f)
                                    )
                                )
                                IconButton(
                                    onClick = { navController.navigate(Screen.GalleryAiSettings.route) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(0.15f))
                                ) {
                                    Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Year-End Rewind Magical Hero Banner
            item {
                Card(
                    onClick = { navController.navigate(Screen.RewindMemory.route) },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF020229),
                                        Color(0xFF1A1B41),
                                        Color(0xFF5270C9)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        // Background glow orb
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 20.dp, y = (-20).dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE1E0FF).copy(alpha = 0.15f))
                        )

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                                    Text(
                                        "Special Feature",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isThai) "สรุปความทรงจำรายปี $buddhistYear" else "Year-End Rewind $year",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isThai)
                                        "ย้อนดูการเดินทางอันแสนวิเศษและช่วงเวลาที่งดงามที่สุดของลูกน้อยในปีนี้"
                                    else
                                        "A magical journey through the most precious moments collected this year.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color.White,
                                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(100.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            if (isThai) "ดูสรุปเลย" else "Explore",
                                            color = Primary,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Icon(Icons.Filled.ArrowForward, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Compare Memory (Then vs Now) Card Banner
            item {


                Card(
                    onClick = { navController.navigate(Screen.CompareMemories.route) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.3f)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Primary.copy(0.12f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Compare, null, tint = Primary, modifier = Modifier.size(26.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    if (isThai) "ย้อนดูความเปลี่ยนแปลง" else "Then vs Now",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xFFFFD54F).copy(0.25f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFD54F))
                                ) {
                                    Text(
                                        "NEW",
                                        color = Color(0xFFC78100),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                if (isThai) "ดูว่าลูกน้อยตัวเล็กแค่ไหนในวันแรก สู่ความน่ารักในวันนี้ 🥰"
                                else "See how little they were on Day 1 vs today!",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary)
                    }
                }
            }

            // Story Reels Section

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.VideoLibrary, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            if (isThai) "สตอรี่และวิดีโอ (Story Reels)" else "Story Reels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    TextButton(onClick = { navController.navigate(Screen.SpecialMemoriesGallery.route) }) {
                        Text(if (isThai) "ดูทั้งหมด" else "View All", color = Primary, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (storyReels.isEmpty()) {
                item {
                    EmptyHint(if (isThai) "ยังไม่มีสตอรี่หรือรูปภาพ — แตะปุ่ม + ด้านล่างเพื่อเพิ่ม" else "No story reels yet — tap + to add")
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(storyReels, key = { "reel-${it.id}" }) { m ->
                            StoryReelCard(
                                memory = m,
                                signedUrl = state.signedUrls[m.mediaPath],
                                isThai = isThai,
                                onClick = { selectedReelMemory = m }
                            )
                        }
                    }
                }
            }

            // Milestones (ก้าวสำคัญ) Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Timeline, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            if (isThai) "ก้าวสำคัญ (Milestones)" else "Milestones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    TextButton(onClick = { navController.navigate(Screen.SpecialMemoriesGallery.route) }) {
                        Text(if (isThai) "ดูทั้งหมด" else "View All", color = Primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (milestones.isEmpty()) {
                item {
                    EmptyHint(if (isThai) "ยังไม่มีก้าวสำคัญที่บันทึก — บันทึกก้าวแรกหรือคำแรกได้เลย" else "No milestones recorded yet")
                }
            } else {
                items(milestones.take(5), key = { "ms-${it.id}" }) { m ->
                    MilestoneCard(
                        memory = m,
                        signedUrl = state.signedUrls[m.mediaPath],
                        isThai = isThai,
                        onClick = { selectedReelMemory = m }
                    )
                }
            }

            // Daily Chapters (ไทม์ไลน์บันทึก) Section
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AutoStories, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(
                        if (isThai) "บันทึกรายวัน (Daily Chapters)" else "Daily Chapters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            if (dailyChapters.isEmpty()) {
                item {
                    EmptyHint(if (isThai) "ยังไม่มีบันทึกเรื่องราวรายวัน" else "No daily chapters yet")
                }
            } else {
                items(dailyChapters.take(15), key = { "dc-${it.id}" }) { m ->
                    DailyChapterTimelineRow(memory = m, isThai = isThai)
                }
            }
        }
    }

    // Detail / Preview Dialog
    selectedReelMemory?.let { memory ->
        MemoryDetailDialog(
            memory = memory,
            signedUrl = state.signedUrls[memory.mediaPath],
            isThai = isThai,
            onDismiss = { selectedReelMemory = null },
            onToggleFavorite = { vm.toggleFavorite(memory) },
            onCompare = {
                selectedReelMemory = null
                navController.navigate(Screen.CompareMemories.route)
            }
        )
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
}


@Composable
private fun StoryReelCard(
    memory: DiaryMemory,
    signedUrl: String?,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .height(240.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!signedUrl.isNullOrBlank()) {
                AsyncImage(
                    model = signedUrl,
                    contentDescription = memory.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF5270C9), Color(0xFF1A1B41))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(40.dp))
                }
            }

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Play Icon if Video
            if (memory.mediaType == "video") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.6f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }

            // NongRak AI Logo Watermark Badge
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color(0xFF020229).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F).copy(0.7f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.lullabyai.app.R.drawable.ic_nongrak_logo),
                        contentDescription = "NongRak Logo",
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                    )
                    Text("น้องรัก AI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }


            // Favorite Badge
            if (memory.isFavorite) {
                Surface(
                    color = Color(0xFFFFD54F),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                ) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFF020229), modifier = Modifier.padding(4.dp))
                }
            }


            // Text Info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatMemoryDate(memory.memoryDate, isThai),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
                Text(
                    text = memory.title ?: DiaryViewModel.tagLabel(memory.milestoneTag ?: "", isThai).ifBlank {
                        if (isThai) "ความทรงจำพิเศษ" else "Special moment"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MilestoneCard(
    memory: DiaryMemory,
    signedUrl: String?,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.7f)),
        elevation = CardDefaults.cardElevation(1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Primary.copy(0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (!signedUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = signedUrl,
                        contentDescription = memory.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        milestoneIcon(memory.milestoneTag),
                        null,
                        tint = Primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Details
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatMemoryDate(memory.memoryDate, isThai),
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = SecondaryContainer
                    ) {
                        Text(
                            text = if (isThai) "สำคัญ" else "Major",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = memory.title ?: DiaryViewModel.tagLabel(memory.milestoneTag ?: "milestone", isThai),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )

                if (!memory.description.isNullOrBlank()) {
                    Text(
                        text = memory.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyChapterTimelineRow(memory: DiaryMemory, isThai: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Vertical Timeline Indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Primary)
                    .border(2.dp, SecondaryContainer, CircleShape)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Primary, Primary.copy(0.1f))
                        )
                    )
            )
        }

        // Note Card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatMemoryDate(memory.memoryDate, isThai),
                style = MaterialTheme.typography.labelSmall,
                color = Outline,
                fontWeight = FontWeight.Bold
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.6f)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!memory.title.isNullOrBlank()) {
                        Text(
                            text = memory.title!!,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    Text(
                        text = memory.description ?: (if (isThai) "ไม่มีรายละเอียด" else "No description"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryDetailDialog(
    memory: DiaryMemory,
    signedUrl: String?,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCompare: () -> Unit
) {

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                var showStudio by remember { mutableStateOf(false) }

                if (!signedUrl.isNullOrBlank()) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        AsyncImage(
                            model = signedUrl,
                            contentDescription = memory.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // NongRak Watermark Badge
                        NongRakWatermarkBadge(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(Color.Black.copy(0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, null, tint = Color.White)
                        }
                    }
                }

                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMemoryDate(memory.memoryDate, isThai),
                            style = MaterialTheme.typography.labelMedium,
                            color = Outline
                        )
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (memory.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                null,
                                tint = if (memory.isFavorite) Color(0xFFE91E63) else Outline
                            )
                        }
                    }

                    Text(
                        text = memory.title ?: DiaryViewModel.tagLabel(memory.milestoneTag ?: "", isThai).ifBlank {
                            if (isThai) "ความทรงจำพิเศษ" else "Special memory"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )

                    if (!memory.description.isNullOrBlank()) {
                        Text(
                            text = memory.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }

                    if (memory.mediaType == "video" && !signedUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { showStudio = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.4f))
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isThai) "เปิดใน NongRak Magic Video Studio" else "Open in NongRak Video Studio",
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Compare Growth Button
                    OutlinedButton(
                        onClick = onCompare,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.4f))
                    ) {
                        Icon(Icons.Filled.Compare, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isThai) "ย้อนดูความเปลี่ยนแปลง (Then vs Now)" else "Compare Growth (Then vs Now)",
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Social Export (TikTok, Instagram, YouTube Shorts with NongRak License)

                    SocialExportSection(
                        title = memory.title,
                        description = memory.description,
                        date = memory.memoryDate,
                        mediaUri = signedUrl?.let { Uri.parse(it) },
                        mimeType = if (memory.mediaType == "video") "video/mp4" else "image/jpeg",
                        isThai = isThai,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(if (isThai) "ปิด" else "Close", fontWeight = FontWeight.Bold)
                    }
                }

                if (showStudio && !signedUrl.isNullOrBlank()) {
                    NongRakVideoStudioDialog(
                        videoUri = Uri.parse(signedUrl),
                        title = memory.title,
                        description = memory.description,
                        memoryDate = memory.memoryDate,
                        isThai = isThai,
                        onDismiss = { showStudio = false },
                        onApply = { _, _, _ -> showStudio = false }
                    )
                }
            }

        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.5f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Info, null, tint = Outline)
            Text(text, color = Outline, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


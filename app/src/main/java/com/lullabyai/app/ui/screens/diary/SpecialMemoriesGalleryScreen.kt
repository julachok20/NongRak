@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DiaryViewModel

private enum class GalleryCategoryFilter { ALL, VIDEO, PHOTO, MILESTONE, FAMILY }

/**
 * Stitch 49.Diary: Special Memories with Sharing & Stars (ความทรงจำพิเศษ)
 * Search, category tabs, masonry-like grid with milestone badges, favorite stars, and social sharing.
 */
@Composable
fun SpecialMemoriesGalleryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by vm.uiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf(GalleryCategoryFilter.ALL) }
    var selectedMemory by remember { mutableStateOf<DiaryMemory?>(null) }

    LaunchedEffect(Unit) { vm.load() }

    val filtered = remember(state.memories, query, categoryFilter) {
        state.memories.asSequence()
            .filter { m ->
                when (categoryFilter) {
                    GalleryCategoryFilter.ALL -> true
                    GalleryCategoryFilter.VIDEO -> m.mediaType == "video"
                    GalleryCategoryFilter.PHOTO -> m.mediaType == "photo"
                    GalleryCategoryFilter.MILESTONE -> m.isMilestone || !m.milestoneTag.isNullOrBlank()
                    GalleryCategoryFilter.FAMILY -> m.category == "family_trip" || m.title?.contains("trip", true) == true
                }
            }
            .filter { m ->
                query.isBlank() || listOfNotNull(m.title, m.description, m.milestoneTag)
                    .any { it.contains(query, ignoreCase = true) }
            }
            .toList()
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ความทรงจำพิเศษ" else "Special Memories",
                onBack = onBack,
                navController = navController,
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddMemory.route) }) {
                        Icon(Icons.Filled.AddAPhoto, if (isThai) "เพิ่มรูป" else "Add photo", tint = Primary)
                    }
                }
            )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // Search Bar + Filter Tune Button
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (isThai) "ค้นหาความทรงจำ..." else "Search memories...") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                trailingIcon = {
                    IconButton(onClick = {
                        // Toggle to next filter
                        categoryFilter = when (categoryFilter) {
                            GalleryCategoryFilter.ALL -> GalleryCategoryFilter.VIDEO
                            GalleryCategoryFilter.VIDEO -> GalleryCategoryFilter.PHOTO
                            GalleryCategoryFilter.PHOTO -> GalleryCategoryFilter.MILESTONE
                            GalleryCategoryFilter.MILESTONE -> GalleryCategoryFilter.FAMILY
                            GalleryCategoryFilter.FAMILY -> GalleryCategoryFilter.ALL
                        }
                    }) {
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

            Spacer(Modifier.height(12.dp))

            // Categories Filter Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                val chips = listOf(
                    GalleryCategoryFilter.ALL to (if (isThai) "ทั้งหมด" else "All"),
                    GalleryCategoryFilter.VIDEO to (if (isThai) "วิดีโอ" else "Videos"),
                    GalleryCategoryFilter.PHOTO to (if (isThai) "รูปภาพ" else "Photos"),
                    GalleryCategoryFilter.MILESTONE to (if (isThai) "เหตุการณ์สำคัญ" else "Milestones"),
                    GalleryCategoryFilter.FAMILY to (if (isThai) "ทริปครอบครัว" else "Family trips")
                )
                items(chips) { (key, label) ->
                    FilterChip(
                        selected = categoryFilter == key,
                        onClick = { categoryFilter = key },
                        label = { Text(label, fontWeight = if (categoryFilter == key) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = OnSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = categoryFilter == key,
                            borderColor = if (categoryFilter == key) Primary else OutlineVariant
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (state.isLoading && filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Primary.copy(0.08f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PhotoLibrary, null, tint = Primary, modifier = Modifier.size(36.dp))
                            }
                        }
                        Text(
                            if (isThai) "ยังไม่มีความทรงจำในหมวดหมู่นี้" else "No memories in this category",
                            color = Outline,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = { navController.navigate(Screen.AddMemory.route) },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isThai) "เพิ่มความทรงจำใหม่" else "Add New Memory")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { m ->
                        GalleryMemoryCard(
                            memory = m,
                            signedUrl = state.signedUrls[m.mediaPath],
                            isThai = isThai,
                            onToggleFavorite = { vm.toggleFavorite(m) },
                            onClick = { selectedMemory = m }
                        )
                    }
                }
            }
        }
    }

    // Interactive Fullscreen / Detail Modal
    selectedMemory?.let { m ->
        SpecialMemoryDetailDialog(
            memory = m,
            signedUrl = state.signedUrls[m.mediaPath],
            isThai = isThai,
            onDismiss = { selectedMemory = null },
            onToggleFavorite = { vm.toggleFavorite(m) },
            onShare = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "✨ NongRak AI ความทรงจำลูกน้อย: ${m.title ?: "ความทรงจำพิเศษ"}\n${m.description.orEmpty()}")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, if (isThai) "แชร์ความทรงจำ" else "Share memory")
                context.startActivity(shareIntent)
            }
        )
    }
}

@Composable
private fun GalleryMemoryCard(
    memory: DiaryMemory,
    signedUrl: String?,
    isThai: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
        ) {
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
                    Icon(
                        if (memory.mediaType == "audio") Icons.Filled.Mic else Icons.Filled.Photo,
                        null,
                        tint = Color.White.copy(0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Dark gradient bottom overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(0.2f),
                                Color.Transparent,
                                Color.Black.copy(0.8f)
                            )
                        )
                    )
            )

            // Top Badges (Milestone & Favorite Star)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF020229).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFD54F).copy(0.7f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(com.lullabyai.app.R.drawable.ic_nongrak_logo),
                            contentDescription = "NongRak Logo",
                            modifier = Modifier.size(14.dp).clip(CircleShape)
                        )
                        Text(
                            "น้องรัก AI",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }



                // Favorite Star Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        if (memory.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        null,
                        tint = if (memory.isFavorite) Color(0xFFFFD54F) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Play Icon if Video
            if (memory.mediaType == "video") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // Bottom Info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatMemoryDate(memory.memoryDate, isThai),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.85f),
                    fontSize = 10.sp
                )
                Text(
                    text = memory.title ?: DiaryViewModel.tagLabel(memory.milestoneTag ?: "", isThai).ifBlank {
                        if (isThai) "ความทรงจำ" else "Memory"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpecialMemoryDetailDialog(
    memory: DiaryMemory,
    signedUrl: String?,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                var showStudio by remember { mutableStateOf(false) }

                if (!signedUrl.isNullOrBlank()) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
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

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = SecondaryContainer
                        ) {
                            Text(
                                text = formatMemoryDate(memory.memoryDate, isThai),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    if (memory.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    null,
                                    tint = if (memory.isFavorite) Color(0xFFFFD54F) else Outline
                                )
                            }
                            IconButton(onClick = onShare) {
                                Icon(Icons.Filled.Share, null, tint = Primary)
                            }
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
                        Text(if (isThai) "ตกลง" else "Done", fontWeight = FontWeight.Bold)
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


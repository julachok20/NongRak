@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.DiaryMemory
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DiaryViewModel

private enum class GalleryFilter { ALL, VIDEO, PHOTO, MILESTONE, FAMILY }

/** Stitch: Special Memories Gallery (ความทรงจำพิเศษ). */
@Composable
fun SpecialMemoriesGalleryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by vm.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }

    LaunchedEffect(Unit) { vm.load() }

    val filtered = remember(state.memories, query, filter) {
        state.memories.asSequence()
            .filter { m ->
                when (filter) {
                    GalleryFilter.ALL -> true
                    GalleryFilter.VIDEO -> m.mediaType == "video"
                    GalleryFilter.PHOTO -> m.mediaType == "photo"
                    GalleryFilter.MILESTONE -> m.isMilestone || !m.milestoneTag.isNullOrBlank()
                    GalleryFilter.FAMILY -> m.category == "family_trip" || m.title?.contains("trip", true) == true
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
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (isThai) "ค้นหาความทรงจำ..." else "Search memories...") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                trailingIcon = {
                    IconButton(onClick = { /* filter chips below */ }) {
                        Icon(Icons.Filled.Tune, null, tint = Primary)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = listOf(
                    GalleryFilter.ALL to (if (isThai) "ทั้งหมด" else "All"),
                    GalleryFilter.VIDEO to (if (isThai) "วิดีโอ" else "Video"),
                    GalleryFilter.PHOTO to (if (isThai) "รูปภาพ" else "Photos"),
                    GalleryFilter.MILESTONE to (if (isThai) "เหตุการณ์สำคัญ" else "Milestones"),
                    GalleryFilter.FAMILY to (if (isThai) "ทริปครอบครัว" else "Family trips")
                )
                items(chips) { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.isLoading && filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = Outline, modifier = Modifier.size(48.dp))
                        Text(if (isThai) "ยังไม่มีความทรงจำในหมวดนี้" else "No memories in this filter", color = Outline)
                        Button(onClick = { navController.navigate(Screen.AddMemory.route) }) {
                            Text(if (isThai) "เพิ่มความทรงจำ" else "Add memory")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { m ->
                        GalleryCard(
                            memory = m,
                            url = m.mediaPath?.let { state.signedUrls[it] },
                            isThai = isThai,
                            onFavorite = { vm.toggleFavorite(m) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryCard(memory: DiaryMemory, url: String?, isThai: Boolean, onFavorite: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (memory.isMilestone || memory.mediaType == "video") 3f / 4f else 1f)
            ) {
                if (!url.isNullOrBlank() && memory.mediaType != "audio" && memory.mediaType != "note") {
                    AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(
                            when (memory.mediaType) {
                                "audio" -> Icons.Filled.Mic
                                "video" -> Icons.Filled.Videocam
                                else -> Icons.Filled.Notes
                            },
                            null, tint = Primary, modifier = Modifier.size(36.dp)
                        )
                    }
                }
                if (memory.isMilestone) {
                    Surface(
                        Modifier.padding(8.dp).align(Alignment.TopStart),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFC107).copy(0.9f)
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Milestone", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (memory.mediaType == "video") {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(40.dp))
                    }
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.15f)))
                    )
                )
            }
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        memory.title
                            ?: DiaryViewModel.tagLabel(memory.milestoneTag ?: "", isThai).ifBlank {
                                DiaryViewModel.mediaLabel(memory.mediaType, isThai)
                            },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFavorite, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (memory.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                            tint = if (memory.isFavorite) Primary else Outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(formatMemoryDate(memory.memoryDate, isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
            }
        }
    }
}

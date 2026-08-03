@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.Calendar

/** Stitch: Child Diary — Special Memories (บันทึกความทรงจำ). Hub into Rewind / Gallery / Add. */
@Composable
fun ChildDiaryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by vm.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val buddhistYear = year + 543

    LaunchedEffect(Unit) { vm.load() }

    val filtered = remember(state.memories, query) {
        if (query.isBlank()) state.memories
        else state.memories.filter {
            listOfNotNull(it.title, it.description, it.milestoneTag)
                .any { t -> t.contains(query, ignoreCase = true) }
        }
    }
    val special = filtered.filter { it.isFavorite || it.mediaType == "video" || it.isMilestone }.ifEmpty { filtered.take(1) }
    val milestones = filtered.filter { it.isMilestone || !it.milestoneTag.isNullOrBlank() }
    val timeline = filtered.filter { it.mediaType == "note" || (!it.isMilestone && it.mediaType != "video") }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "บันทึกความทรงจำ" else "Child Diary",
                onBack = onBack,
                navController = navController
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddMemory.route) },
                containerColor = Primary,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Filled.Add, if (isThai) "เพิ่ม" else "Add") }
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
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 88.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isThai) "ค้นหาความทรงจำ..." else "Search memories...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
            item {
                Card(
                    onClick = { navController.navigate(Screen.RewindMemory.route) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryContainer)
                ) {
                    Box(Modifier.fillMaxWidth().padding(20.dp)) {
                        Icon(
                            Icons.Filled.AutoAwesome, null,
                            tint = Color.White.copy(0.25f),
                            modifier = Modifier.size(72.dp).align(Alignment.TopEnd)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                                Text("Special Feature", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                if (isThai) "สรุปความทรงจำรายปี $buddhistYear" else "Rewind Memory of $year",
                                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isThai) "ย้อนดูช่วงเวลาที่สวยงามที่สุดของลูกในปีที่ผ่านมา"
                                else "Relive your child's most beautiful moments this year",
                                color = Color.White.copy(0.9f), style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(100.dp), color = Color.White) {
                                Text(
                                    if (isThai) "ดูสรุปเลย" else "View summary",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "ความทรงจำที่แสนพิเศษ" else "Special memories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.SpecialMemoriesGallery.route) }) {
                        Text(if (isThai) "ดูทั้งหมด" else "See all", color = Primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (special.isEmpty()) {
                item {
                    EmptyHint(if (isThai) "ยังไม่มีความทรงจำ — แตะ + เพื่อเพิ่ม" else "No memories yet — tap + to add")
                }
            } else {
                item {
                    val m = special.first()
                    FeaturedMemoryCard(m, state.signedUrls[m.mediaPath], isThai) {
                        navController.navigate(Screen.SpecialMemoriesGallery.route)
                    }
                }
            }
            item {
                Text(if (isThai) "ก้าวสำคัญของลูก" else "Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (milestones.isEmpty()) {
                item { EmptyHint(if (isThai) "ยังไม่มีก้าวสำคัญที่บันทึก" else "No milestone memories yet") }
            } else {
                items(milestones.take(6), key = { it.id }) { m ->
                    MilestoneRow(m, state.signedUrls[m.mediaPath], isThai)
                }
            }
            item {
                Text(if (isThai) "ไทม์ไลน์บันทึก" else "Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(timeline.take(20), key = { "t-${it.id}" }) { m ->
                TimelineRow(m, isThai)
            }
        }
    }
}

@Composable
private fun FeaturedMemoryCard(m: DiaryMemory, url: String?, isThai: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (!url.isNullOrBlank()) {
                AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Primary.copy(0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Photo, null, tint = Primary, modifier = Modifier.size(48.dp))
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))
                )
            )
            if (m.mediaType == "video") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color.White.copy(0.9f)) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Primary, modifier = Modifier.padding(14.dp).size(32.dp))
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFFFFC107).copy(0.2f)) {
                    Text(
                        if (isThai) "ช่วงเวลาแห่งความสุข" else "Moments of Joy",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color(0xFFFFC107), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    m.title ?: DiaryViewModel.tagLabel(m.milestoneTag ?: "", isThai).ifBlank {
                        if (isThai) "ความทรงจำพิเศษ" else "Special memory"
                    },
                    color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                )
                Text(
                    formatMemoryDate(m.memoryDate, isThai),
                    color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MilestoneRow(m: DiaryMemory, url: String?, isThai: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(SecondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(milestoneIcon(m.milestoneTag), null, tint = Primary, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    m.title ?: DiaryViewModel.tagLabel(m.milestoneTag ?: "milestone", isThai),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall
                )
                if (!m.description.isNullOrBlank()) {
                    Text(m.description!!, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, maxLines = 2)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.CalendarToday, null, tint = Outline, modifier = Modifier.size(14.dp))
                    Text(formatMemoryDate(m.memoryDate, isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceContainerLow), contentAlignment = Alignment.Center) {
                if (!url.isNullOrBlank() && m.mediaType == "photo") {
                    AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(
                        if (m.mediaType == "audio") Icons.Filled.Mic else Icons.Filled.Image,
                        null, tint = Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(m: DiaryMemory, isThai: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Primary))
            Box(Modifier.width(2.dp).height(56.dp).background(OutlineVariant))
        }
        Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
            Text(
                formatMemoryDate(m.memoryDate, isThai),
                style = MaterialTheme.typography.labelSmall, color = Outline, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(
                    m.description ?: m.title ?: DiaryViewModel.mediaLabel(m.mediaType, isThai),
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(text, Modifier.padding(16.dp), color = Outline, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun milestoneIcon(tag: String?) = when (tag) {
    "first_step" -> Icons.Filled.DirectionsWalk
    "first_word" -> Icons.Filled.RecordVoiceOver
    "first_smile" -> Icons.Filled.SentimentSatisfied
    "first_tooth" -> Icons.Filled.Star
    else -> Icons.Filled.AutoAwesome
}

internal fun formatMemoryDate(iso: String?, isThai: Boolean): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val p = iso.take(10).split("-")
        val day = p[2].toInt(); val month = p[1].toInt() - 1; val year = p[0].toInt()
        val th = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
        val en = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        if (isThai) "$day ${th[month]} ${year + 543}" else "$day ${en[month]} $year"
    } catch (_: Exception) { iso }
}

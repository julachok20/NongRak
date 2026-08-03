@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DiaryViewModel
import java.util.Calendar

/** Stitch: Rewind Memory of the Year (สรุปความทรงจำรายปี). */
@Composable
fun RewindMemoryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by vm.uiState.collectAsState()
    val year = state.selectedYear
    val displayYear = if (isThai) year + 543 else year
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load()
        vm.loadYearRewind(Calendar.getInstance().get(Calendar.YEAR), isThai)
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ย้อนรอยความทรงจำ $displayYear" else "Rewind $displayYear",
                onBack = onBack,
                navController = navController
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
        if (state.yearLoading && state.yearStats == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }
        val stats = state.yearStats
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (isThai) "ย้อนรอยความทรงจำ $displayYear" else "Rewind Memory of $displayYear",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary
            )
            Text(
                if (isThai) "บันทึกช่วงเวลาแห่งความรัก การเติบโต และความสุขที่คุณได้แบ่งปันกับเจ้าตัวเล็กในปีนี้"
                else "A record of love, growth, and joy you shared with your little one this year",
                style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant
            )

            // Featured card
            val featured = stats?.featured
            Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(3.dp)) {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    val url = featured?.mediaPath?.let { state.signedUrls[it] }
                    if (!url.isNullOrBlank()) {
                        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize().background(Primary.copy(0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(64.dp))
                        }
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)))
                        )
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary) {
                            Text(
                                if (isThai) "ก้าวสำคัญ" else "Milestone",
                                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color.White, style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            featured?.title ?: (if (isThai) "ปีแห่งความทรงจำ" else "A year of memories"),
                            color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            featured?.description?.take(120)
                                ?: formatMemoryDate(featured?.memoryDate, isThai).ifBlank {
                                    if (isThai) "เริ่มบันทึกเพื่อเติมเรื่องราวปีนี้" else "Add memories to fill this year's story"
                                },
                            color = Color.White.copy(0.85f), style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(
                        Icons.Filled.Favorite, null, tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                            .background(Color.White.copy(0.2f), CircleShape).padding(10.dp).size(22.dp)
                    )
                }
            }

            Text(
                if (isThai) "ปีนี้ในตัวเลข" else "This year in numbers",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Filled.AutoAwesome, "${stats?.milestoneCount ?: 0}", if (isThai) "ก้าวสำคัญ" else "Milestones", Primary, Modifier.weight(1f))
                StatTile(Icons.Filled.Favorite, "${stats?.sweetMoments ?: 0}", if (isThai) "ช่วงเวลาหวาน" else "Sweet moments", Secondary, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Filled.Movie, "${stats?.videoCount ?: 0}", if (isThai) "คลิปวิดีโอ" else "Videos", Tertiary, Modifier.weight(1f))
                val growth = stats?.growthCm?.let { "%.0f".format(it) + if (isThai) " ซม." else " cm" } ?: "—"
                StatTile(Icons.Filled.Straighten, growth, if (isThai) "การเจริญเติบโต" else "Growth", Error, Modifier.weight(1f))
            }

            Text(
                if (isThai) "3 ความทรงจำที่สุดของปี" else "Top 3 memories of the year",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            val tops = stats?.topFavorites.orEmpty()
            if (tops.isEmpty()) {
                Text(
                    if (isThai) "ยังไม่มีรายการติดดาว — กดหัวใจในแกลเลอรี่ได้เลย" else "No favorites yet — heart memories in the gallery",
                    color = Outline, style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(tops) { index, m ->
                        Column(Modifier.width(200.dp)) {
                            Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                                    val url = m.mediaPath?.let { state.signedUrls[it] }
                                    if (!url.isNullOrBlank()) {
                                        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Box(Modifier.fillMaxSize().background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Photo, null, tint = Primary)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (isThai) "อันดับ ${index + 1}" else "Rank ${index + 1}",
                                color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium
                            )
                            Text(m.title ?: formatMemoryDate(m.memoryDate, isThai), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Psychology, null, tint = Primary)
                        Text(if (isThai) "สรุปจาก NongRak AI" else "NongRak AI insight", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        stats?.aiInsight
                            ?: if (isThai) "กำลังสรุปความทรงจำ..." else "Summarizing memories...",
                        style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant
                    )
                    Text(
                        if (isThai) "วิเคราะห์จาก ${stats?.sweetMoments ?: 0} รายการ"
                        else "Based on ${stats?.sweetMoments ?: 0} entries",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                }
            }

            Button(
                onClick = {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            buildString {
                                append(if (isThai) "สรุปความทรงจำปี $displayYear\n" else "Memory rewind $displayYear\n")
                                append(stats?.aiInsight ?: "")
                            }
                        )
                    }
                    context.startActivity(android.content.Intent.createChooser(share, if (isThai) "แชร์สรุปปี" else "Share year summary"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "แชร์สรุปปี" else "Share year summary", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { navController.navigate(Screen.SpecialMemoriesGallery.route) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, null, tint = Primary)
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "เปิดแกลเลอรี่ความทรงจำ" else "Open memories gallery", fontWeight = FontWeight.Bold, color = Primary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Outline, textAlign = TextAlign.Center)
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DiaryViewModel
import java.util.Calendar

/**
 * Stitch 49.Diary: Rewind Memory of the Year (ย้อนรอยความทรงจำรายปี)
 * Upgraded with Bento stats tiles, featured milestone hero, AI year insight narrative, and sharing.
 */
@Composable
fun RewindMemoryScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: DiaryViewModel = viewModel()
) {
    val s = LocalStrings.current
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
                navController = navController,
                actions = {
                    IconButton(onClick = {
                        val shareText = "🌟 ย้อนรอยความทรงจำปี $displayYear ของลูกน้อย กับ NongRak AI 💕\n" +
                                "✨ บันทึก ${state.yearStats?.milestoneCount ?: 0} ก้าวสำคัญ และ ${state.yearStats?.sweetMoments ?: 0} ช่วงเวลาแสนหวาน!\n" +
                                (state.yearStats?.aiInsight.orEmpty())
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, if (isThai) "แชร์สรุปความทรงจำรายปี" else "Share Year Rewind")
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Filled.Share, if (isThai) "แชร์" else "Share", tint = Primary)
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
        if (state.yearLoading && state.yearStats == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        val stats = state.yearStats
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Title Section
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isThai) "ย้อนรอยความทรงจำ $displayYear" else "Rewind Memory of $displayYear",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    text = if (isThai)
                        "บันทึกช่วงเวลาแห่งความรัก การเติบโต และความสุขที่คุณได้แบ่งปันกับเจ้าตัวเล็กในปีนี้"
                    else
                        "A record of love, growth, and joy you shared with your little one this year.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    lineHeight = 22.sp
                )
            }

            // Featured Milestone Hero Card
            val featured = stats?.featured
            Card(
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    val url = featured?.mediaPath?.let { state.signedUrls[it] }
                    if (!url.isNullOrBlank()) {
                        AsyncImage(
                            model = url,
                            contentDescription = featured.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1A1B41), Color(0xFF5270C9))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(64.dp))
                        }
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Favorite Button top right
                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Favorite,
                                null,
                                tint = Color(0xFFFF4081),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Content bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Primary
                        ) {
                            Text(
                                if (isThai) "ก้าวสำคัญ" else "Milestone",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = featured?.title ?: (if (isThai) "ก้าวแรกสู่ความอิสระ" else "First Step to Freedom"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        val defaultDesc = if (isThai) "บ่ายวันมหัศจรรย์ที่เท้าคู่น้อยๆ เริ่มทรงตัวได้ด้วยตัวเองเป็นครั้งแรก"
                        else "A magical afternoon when those tiny feet took their first steady steps."
                        val dateFormatted = formatMemoryDate(featured?.memoryDate, isThai)
                        Text(
                            text = featured?.description?.take(140) ?: if (dateFormatted.isNotBlank()) dateFormatted else defaultDesc,
                            color = Color.White.copy(0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Stats Grid (Bento Style 4 Tiles)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Analytics, null, tint = Primary, modifier = Modifier.size(22.dp))
                    Text(
                        if (isThai) "ปีนี้ในตัวเลข (This Year in Numbers)" else "This Year in Numbers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoStatTile(
                        icon = Icons.Filled.AutoAwesome,
                        count = "${stats?.milestoneCount ?: 0}",
                        label = if (isThai) "ก้าวสำคัญ" else "Milestones",
                        accentColor = Primary,
                        modifier = Modifier.weight(1f)
                    )
                    BentoStatTile(
                        icon = Icons.Filled.Favorite,
                        count = "${stats?.sweetMoments ?: 0}",
                        label = if (isThai) "ช่วงเวลาที่แสนหวาน" else "Sweet Moments",
                        accentColor = Color(0xFFE91E63),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoStatTile(
                        icon = Icons.Filled.CalendarMonth,
                        count = "365",
                        label = if (isThai) "วันแห่งความรัก" else "Days of Love",
                        accentColor = Color(0xFF007CB1),
                        modifier = Modifier.weight(1f)
                    )
                    BentoStatTile(
                        icon = Icons.Filled.PhotoLibrary,
                        count = "${(stats?.sweetMoments ?: 0) + (stats?.videoCount ?: 0) * 2 + 12}",
                        label = if (isThai) "รูปภาพและวิดีโอ" else "Photos & Videos",
                        accentColor = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // AI Year Insight Narrative Card
            stats?.aiInsight?.let { insight ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.04f)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.2f)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text(
                                if (isThai) "สรุปความทรงจำจาก AI" else "AI Year Insight",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Highlights Carousel (Top Favorites)
            val topMemories = stats?.topFavorites.orEmpty()
            if (topMemories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isThai) "โมเมนต์ยอดประทับใจ (Top Highlights)" else "Top Highlights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        itemsIndexed(topMemories) { index, m ->
                            Card(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(200.dp),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val url = m.mediaPath?.let { state.signedUrls[it] }
                                    if (!url.isNullOrBlank()) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = m.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Primary.copy(0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.Star, null, tint = Primary, modifier = Modifier.size(36.dp))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(0.8f))
                                                )
                                            )
                                    )
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "#${index + 1} ${formatMemoryDate(m.memoryDate, isThai)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(0.8f),
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = m.title ?: DiaryViewModel.tagLabel(m.milestoneTag ?: "", isThai).ifBlank { "ช่วงเวลาพิเศษ" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Social Export Section (TikTok, Instagram, YouTube Shorts with NongRak License)
            SocialExportSection(
                title = if (isThai) "ย้อนรอยความทรงจำประจำปี $displayYear" else "Year-End Rewind $displayYear",
                description = "🌟 สรุปความทรงจำ: ${stats?.milestoneCount ?: 0} ก้าวสำคัญ และ ${stats?.sweetMoments ?: 0} ช่วงเวลาแสนหวาน!\n${stats?.aiInsight.orEmpty()}",
                date = null,
                isThai = isThai
            )

            // General Share Button
            Button(
                onClick = {
                    exportMemoryToSocial(
                        context = context,
                        platform = "general",
                        title = if (isThai) "ย้อนรอยความทรงจำประจำปี $displayYear" else "Year-End Rewind $displayYear",
                        description = "🌟 บันทึก ${stats?.milestoneCount ?: 0} ก้าวสำคัญ และ ${stats?.sweetMoments ?: 0} ช่วงเวลาแสนหวาน!\n${stats?.aiInsight.orEmpty()}",
                        date = null,
                        isThai = isThai
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(8.dp, RoundedCornerShape(100.dp), spotColor = Primary),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isThai) "แชร์สรุปความทรงจำรายปี $displayYear" else "Share Year-End Rewind",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }


            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BentoStatTile(
    icon: ImageVector,
    count: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.6f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                text = count,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

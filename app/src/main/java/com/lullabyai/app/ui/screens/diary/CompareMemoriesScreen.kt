@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.DiaryMemory
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DiaryViewModel

/**
 * Compare Memory Screen:
 * Allows parents to compare their baby's growth and transformation side-by-side (Then vs Now),
 * with an interactive wipe slider, AI growth transformation summary, and 1-tap export to social media.
 */
@Composable
fun CompareMemoriesScreen(
    navController: NavController,
    vm: DiaryViewModel,
    isThai: Boolean = true
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load()
    }

    val memoriesWithMedia = remember(state.memories) {
        state.memories.filter { !it.mediaPath.isNullOrBlank() }
    }

    // Default Memory A (oldest / earliest) and Memory B (latest / newest)
    var selectedEarlyMemory by remember(memoriesWithMedia) {
        mutableStateOf(memoriesWithMedia.minByOrNull { it.memoryDate } ?: memoriesWithMedia.firstOrNull())
    }
    var selectedRecentMemory by remember(memoriesWithMedia) {
        mutableStateOf(memoriesWithMedia.maxByOrNull { it.memoryDate } ?: memoriesWithMedia.lastOrNull())
    }

    // Slider position (0.0f = full early, 1.0f = full recent, 0.5f = half-half)
    var sliderRatio by remember { mutableFloatStateOf(0.5f) }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ย้อนดูความเปลี่ยนแปลง" else "Compare Growth",
                onBack = { navController.popBackStack() },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (memoriesWithMedia.size < 2) {
                // Empty hint if not enough media memories
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Compare, null, tint = Primary, modifier = Modifier.size(48.dp))
                        Text(
                            if (isThai) "ต้องการความทรงจำรูปภาพหรือวิดีโออย่างน้อย 2 รายการ"
                            else "Need at least 2 photo/video memories to compare",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            if (isThai) "บันทึกภาพถ่ายแรกคลอด และภาพถ่ายปัจจุบันเพื่อดูความเปลี่ยนแปลงอันแสนอบอุ่น"
                            else "Record baby photos across different milestones to compare their growth.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 1. Interactive Comparison Viewport (Wipe Slider)
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E29)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .shadow(12.dp, RoundedCornerShape(24.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val earlyUrl = selectedEarlyMemory?.let { state.signedUrls[it.mediaPath] }
                        val recentUrl = selectedRecentMemory?.let { state.signedUrls[it.mediaPath] }

                        // Base layer: Early Memory ("Then / วันวาน")
                        if (!earlyUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = earlyUrl,
                                contentDescription = "Then",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFF1E214D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Photo, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(48.dp))
                            }
                        }

                        // Top Layer with Dynamic Clip Width: Recent Memory ("Now / วันนี้")
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (1f - sliderRatio).coerceIn(0f, 1f))
                                .align(Alignment.CenterEnd)
                        ) {
                            if (!recentUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = recentUrl,
                                    contentDescription = "Now",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.CenterEnd
                                )
                            }
                        }

                        // Draggable Slider Divider Handle
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(40.dp)
                                .align(Alignment.CenterStart)
                                .offset(x = ((LocalConfiguration.current.screenWidthDp - 32) * sliderRatio - 20).dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val totalWidth = size.width * 10
                                        sliderRatio = (sliderRatio + dragAmount.x / 300f).coerceIn(0.05f, 0.95f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Vertical Dividing Line
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFFD54F), Color.White, Color(0xFF5270C9))
                                        )
                                    )
                            )

                            // Circular Grip Handle
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF020229),
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD54F)),
                                modifier = Modifier.size(36.dp).shadow(8.dp, CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.CompareArrows,
                                        null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Top-Left Badge: "วันวาน (Then)"
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFF020229).copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F).copy(0.7f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Filled.History, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(13.dp))
                                Text(
                                    text = if (isThai) "วันวาน: ${formatMemoryDate(selectedEarlyMemory?.memoryDate, true)}"
                                    else "Then: ${formatMemoryDate(selectedEarlyMemory?.memoryDate, false)}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Top-Right Badge: "วันนี้ (Now)"
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFF020229).copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5270C9).copy(0.7f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFF81D4FA), modifier = Modifier.size(13.dp))
                                Text(
                                    text = if (isThai) "วันนี้: ${formatMemoryDate(selectedRecentMemory?.memoryDate, true)}"
                                    else "Now: ${formatMemoryDate(selectedRecentMemory?.memoryDate, false)}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Bottom-Right Official NongRak Watermark Logo
                        NongRakProminentWatermark(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        )
                    }
                }

                // 2. AI Growth & Transformation Summary Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.6f)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFFF7941E), modifier = Modifier.size(22.dp))
                            Text(
                                if (isThai) "AI วิเคราะห์พัฒนาการความเติบโต" else "AI Growth Transformation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }

                        Text(
                            text = if (isThai)
                                "✨ จากก้าวเล็กๆ ในวันแรก สู่ก้าวที่มั่นคงในวันนี้ ลูกน้อยเติบโตขึ้นอย่างงดงามด้วยความรักและการดูแลของคุณพ่อคุณแม่ 🥰"
                            else
                                "✨ From tiny beginnings to confident strides today, your baby has grown beautifully surrounded by love.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            lineHeight = 22.sp
                        )

                        HorizontalDivider(color = OutlineVariant.copy(0.4f))

                        // Stats Quick Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(if (isThai) "ภาพอดีต" else "Earlier Memory", style = MaterialTheme.typography.labelSmall, color = Outline)
                                Text(
                                    selectedEarlyMemory?.title ?: (if (isThai) "ความทรงจำวัยแรกเริ่ม" else "Early moment"),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(Icons.Filled.ArrowForward, null, tint = Primary, modifier = Modifier.align(Alignment.CenterVertically))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (isThai) "ภาพปัจจุบัน" else "Recent Memory", style = MaterialTheme.typography.labelSmall, color = Outline)
                                Text(
                                    selectedRecentMemory?.title ?: (if (isThai) "ความทรงจำปัจจุบัน" else "Recent moment"),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 3. Memory Pickers: Select Memory A & Memory B
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isThai) "เลือกภาพอดีต (Select Earlier Memory):" else "Select Earlier Memory:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(memoriesWithMedia) { m ->
                            val isSel = selectedEarlyMemory?.id == m.id
                            MemoryThumbChoiceCard(
                                memory = m,
                                signedUrl = state.signedUrls[m.mediaPath],
                                selected = isSel,
                                isThai = isThai,
                                onClick = { selectedEarlyMemory = m }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isThai) "เลือกภาพปัจจุบัน (Select Recent Memory):" else "Select Recent Memory:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(memoriesWithMedia) { m ->
                            val isSel = selectedRecentMemory?.id == m.id
                            MemoryThumbChoiceCard(
                                memory = m,
                                signedUrl = state.signedUrls[m.mediaPath],
                                selected = isSel,
                                isThai = isThai,
                                onClick = { selectedRecentMemory = m }
                            )
                        }
                    }
                }

                // 4. Social Export Section
                SocialExportSection(
                    title = if (isThai) "ย้อนดูความเปลี่ยนแปลงของลูกน้อย (Then vs Now)" else "Baby Growth Transformation (Then vs Now)",
                    description = "👶 วันวาน: ${formatMemoryDate(selectedEarlyMemory?.memoryDate, isThai)}\n✨ วันนี้: ${formatMemoryDate(selectedRecentMemory?.memoryDate, isThai)}\nดูสิว่าลูกน้อยตัวเล็กแค่ไหนในวันนั้น!",
                    date = null,
                    isThai = isThai
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun MemoryThumbChoiceCard(
    memory: DiaryMemory,
    signedUrl: String?,
    selected: Boolean,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) Primary else OutlineVariant
        ),
        modifier = Modifier.width(100.dp).height(120.dp)
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
                Box(modifier = Modifier.fillMaxSize().background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Image, null, tint = Primary)
                }
            }

            // Date overlay
            Surface(
                color = Color.Black.copy(0.7f),
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            ) {
                Text(
                    text = formatMemoryDate(memory.memoryDate, isThai),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = Primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.padding(3.dp))
                }
            }
        }
    }
}

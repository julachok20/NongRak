package com.lullabyai.app.ui.screens.child

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.util.HealthBook
import com.lullabyai.app.util.HealthBookBuilder
import com.lullabyai.app.util.HealthBookPageRenderer
import com.lullabyai.app.util.HealthBookPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Cache for rendered page bitmaps to avoid redundant rasterization and OOM.
 */
private val sheetRenderCache = object : android.util.LruCache<String, Bitmap>(6) {}

/**
 * The child's health record as an interactive book.
 * Fits perfectly on any screen size/ratio without clipping or distortion.
 */
@Composable
fun HealthBookScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var book by remember { mutableStateOf<HealthBook?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val childId = ChildSelection.current()
        val children = com.lullabyai.app.data.repository.ChildRepository().getChildren().getOrDefault(emptyList())
        val child = (if (childId != null) children.firstOrNull { it.id == childId } else null) ?: children.firstOrNull()
        if (child != null) {
            book = runCatching { HealthBookBuilder.build(child, isThai) }.getOrNull()
        }
        loading = false
    }

    val current = book

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "สมุดบันทึกสุขภาพเด็ก" else "Health Child Record",
                onBack = onBack,
                navController = navController,
                actions = {
                    androidx.compose.material3.TextButton(
                        onClick = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.InfoAsk.route) }
                    ) {
                        Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isThai) "กรอกข้อมูล" else "Fill info",
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }

            current == null || current.pages.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding), Alignment.Center
            ) {
                Text(
                    if (isThai) "ยังไม่มีข้อมูลลูก" else "No child selected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { current.pages.size })

                Column(Modifier.fillMaxSize().padding(padding)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFE8EEF9), Color(0xFFD7E1F2))
                                )
                            )
                    ) {
                        if (zoomed) {
                            ZoomedSheet(current, pagerState.currentPage) { zoomed = false }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                pageSpacing = 16.dp,
                                beyondViewportPageCount = 1
                            ) { pageIndex ->
                                Sheet(
                                    book = current,
                                    index = pageIndex,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { zoomed = true }
                                        )
                                )
                            }

                            if (pagerState.currentPage > 0) {
                                PageArrow(Alignment.CenterStart, isThai) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            }
                            if (pagerState.currentPage < current.pages.lastIndex) {
                                PageArrow(Alignment.CenterEnd, isThai) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            message?.let {
                                Text(
                                    it,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                                    Text(
                                        text = if (isThai) "หน้า ${pagerState.currentPage + 1} / ${current.pages.size}"
                                        else "Page ${pagerState.currentPage + 1} of ${current.pages.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = current.pages.getOrNull(pagerState.currentPage)?.chapter ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.FilledTonalIconButton(
                                        onClick = { zoomed = !zoomed },
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            if (zoomed) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                                            contentDescription = if (isThai) "ขยาย" else "Zoom",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                message = if (isThai) "กำลังสร้างไฟล์ PDF..." else "Generating PDF..."
                                                runCatching { HealthBookPdf.export(context, current, isThai) }
                                                    .onSuccess { file ->
                                                        message = null
                                                        HealthBookPdf.share(
                                                            context, file, current.childName, isThai
                                                        )
                                                    }
                                                    .onFailure { e ->
                                                        message = (if (isThai) "สร้าง PDF ไม่สำเร็จ: " else "Could not create PDF: ") +
                                                            (e.message ?: "unknown")
                                                    }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (isThai) "พิมพ์ / ส่งออก PDF" else "Export PDF",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a single book sheet inside the allocated screen area.
 * Dynamically computes aspect ratio and fits either height or width so the sheet is NEVER clipped.
 */
@Composable
private fun Sheet(book: HealthBook, index: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val pageRatio = HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H // 595 / 842 = ~0.70665f
        val containerRatio = maxWidth / maxHeight

        // Dynamic constraint: if container is wider than standard page ratio, fit to height so top/bottom never cut off!
        // If container is narrower, fit to width so sides never cut off!
        val fitModifier = if (containerRatio > pageRatio) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(pageRatio, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(pageRatio, matchHeightConstraintsFirst = false)
        }

        val bmp = rememberSheetBitmap(book, index)
        if (bmp == null) {
            Box(
                modifier = fitModifier
                    .shadow(6.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp), color = Primary)
            }
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = book.pages.getOrNull(index)?.chapter,
                contentScale = ContentScale.Fit,
                modifier = fitModifier
                    .shadow(6.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
            )
        }
    }
}

/**
 * Renders a sheet off the main thread with LRU caching.
 */
@Composable
private fun rememberSheetBitmap(book: HealthBook, index: Int): Bitmap? {
    val context = LocalContext.current
    val cacheKey = "${book.childName}_${book.generatedAt}_$index"
    var bitmap by remember(cacheKey) { mutableStateOf(sheetRenderCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap != null) return@LaunchedEffect
        val rendered = withContext(Dispatchers.Default) {
            runCatching {
                // High-DPI scale (1.6x) ensures crisp text while staying safely within memory limits
                val scale = 1.6f
                Bitmap.createBitmap(
                    (HealthBookPageRenderer.PAGE_W * scale).roundToInt(),
                    (HealthBookPageRenderer.PAGE_H * scale).roundToInt(),
                    Bitmap.Config.ARGB_8888
                ).also { bmp ->
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.scale(scale, scale)
                    HealthBookPageRenderer.draw(canvas, context, book, index)
                }
            }.getOrNull()
        }
        if (rendered != null) {
            sheetRenderCache.put(cacheKey, rendered)
            bitmap = rendered
        }
    }
    return bitmap
}

/**
 * Detailed zoom view that supports pan and scroll, with a prominent close button.
 */
@Composable
private fun ZoomedSheet(book: HealthBook, index: Int, onClose: () -> Unit) {
    val bmp = rememberSheetBitmap(book, index)
    val screenW = LocalConfiguration.current.screenWidthDp.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = book.pages.getOrNull(index)?.chapter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(screenW * 2f)
                        .aspectRatio(HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H)
                        .padding(16.dp)
                        .shadow(12.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }

        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun BoxScope.PageArrow(
    align: Alignment,
    isThai: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .align(align)
            .padding(horizontal = 4.dp)
            .size(38.dp),
        shape = CircleShape,
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            if (align == Alignment.CenterStart) Icons.AutoMirrored.Filled.ArrowBack
            else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = if (align == Alignment.CenterStart) {
                if (isThai) "หน้าก่อนหน้า" else "Previous page"
            } else {
                if (isThai) "หน้าถัดไป" else "Next page"
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

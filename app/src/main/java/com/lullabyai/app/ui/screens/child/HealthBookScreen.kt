package com.lullabyai.app.ui.screens.child

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.absoluteValue

/**
 * The child's health record as a book you turn, not a page you scroll.
 *
 * Every sheet is drawn by [HealthBookPageRenderer] onto a bitmap — the exact code that draws the
 * exported PDF. That is the point: the parent reads the same sheet they hand to a doctor, down to
 * the ruled lines, rather than a screen layout that merely resembles it.
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
        // ChildSelection holds the id; the book needs the whole row for the cover and profile.
        val childId = ChildSelection.current()
        val child = if (childId == null) null else {
            com.lullabyai.app.data.repository.ChildRepository().getChildren()
                .getOrDefault(emptyList())
                .firstOrNull { it.id == childId }
        }
        if (child != null) {
            book = runCatching { HealthBookBuilder.build(child, isThai) }.getOrNull()
        }
        loading = false
    }

    val current = book
    val pagerState = rememberPagerState(pageCount = { current?.pages?.size ?: 0 })

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "สมุดบันทึกสุขภาพเด็ก" else "Health Child Record",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            current == null || current.pages.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding), Alignment.Center
            ) {
                Text(
                    if (isThai) "ยังไม่มีข้อมูลลูก" else "No child selected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 8.dp,
                        // A zoomed sheet scrolls inside itself; a horizontal drag there means
                        // "look further right on this page", never "turn to the next one".
                        userScrollEnabled = !zoomed
                    ) { index ->
                        val offset =
                            (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction

                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = if (zoomed) 0.dp else 14.dp, vertical = 10.dp)
                                .graphicsLayer {
                                    val f = offset.coerceIn(-1f, 1f)
                                    rotationY = f * 18f
                                    cameraDistance = 14f * density
                                    transformOrigin = TransformOrigin(if (f > 0f) 1f else 0f, 0.5f)
                                    alpha = 1f - (f.absoluteValue * 0.25f)
                                }
                        ) {
                            FormSheet(
                                book = current,
                                index = index,
                                zoomed = zoomed,
                                onToggleZoom = { zoomed = !zoomed }
                            )
                        }
                    }

                    if (!zoomed && pagerState.currentPage > 0) {
                        PageArrow(Alignment.CenterStart, isThai) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    }
                    if (!zoomed && pagerState.currentPage < current.pages.lastIndex) {
                        PageArrow(Alignment.CenterEnd, isThai) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
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
                            Column {
                                Text(
                                    text = if (isThai) "หน้า ${pagerState.currentPage + 1} / ${current.pages.size}"
                                    else "Page ${pagerState.currentPage + 1} of ${current.pages.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    current.pages[pagerState.currentPage].chapter,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.FilledTonalIconButton(
                                    onClick = { zoomed = !zoomed },
                                    modifier = Modifier.size(38.dp),
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
                                            message =
                                                if (isThai) "กำลังสร้างไฟล์ PDF..." else "Generating PDF..."
                                            runCatching { HealthBookPdf.export(context, current, isThai) }
                                                .onSuccess { file ->
                                                    message = null
                                                    HealthBookPdf.share(
                                                        context, file, current.childName, isThai
                                                    )
                                                }
                                                .onFailure { e ->
                                                    message = (if (isThai) "สร้าง PDF ไม่สำเร็จ: "
                                                    else "Could not create PDF: ") +
                                                        (e.message ?: "unknown")
                                                }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
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

/**
 * One rendered sheet.
 *
 * The bitmap is produced off the main thread and keyed on the page index, so Compose disposes it
 * with the page when the pager scrolls past — an A4 raster is several megabytes and holding every
 * sheet of a long book would be the whole heap.
 */
@Composable
private fun FormSheet(
    book: HealthBook,
    index: Int,
    zoomed: Boolean,
    onToggleZoom: () -> Unit
) {
    val context = LocalContext.current
    // 2× A4 at 72dpi: enough that 7pt form text stays legible when zoomed, without the cost of a
    // print-resolution raster the screen could never show anyway.
    val scale = 2f

    val bitmap by produceState<Bitmap?>(initialValue = null, index, book) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                Bitmap.createBitmap(
                    (HealthBookPageRenderer.PAGE_W * scale).toInt(),
                    (HealthBookPageRenderer.PAGE_H * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                ).also { bmp ->
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.scale(scale, scale)
                    HealthBookPageRenderer.draw(canvas, context, book, index)
                }
            }.getOrNull()
        }
    }

    val sheet = bitmap
    if (sheet == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            Alignment.Center
        ) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp)) }
        return
    }

    if (zoomed) {
        // Scrolls both ways at natural size, the way you'd move a sheet of paper under your eyes.
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Image(
                bitmap = sheet.asImageBitmap(),
                contentDescription = book.pages.getOrNull(index)?.chapter,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width((HealthBookPageRenderer.PAGE_W * 1.55f).dp)
                    .aspectRatio(HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleZoom
                    )
            )
        }
    } else {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Image(
                bitmap = sheet.asImageBitmap(),
                contentDescription = book.pages.getOrNull(index)?.chapter,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H)
                    .shadow(6.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleZoom
                    )
            )
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
            .size(42.dp),
        shape = CircleShape,
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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
            modifier = Modifier.size(22.dp)
        )
    }
}

package com.lullabyai.app.ui.screens.child

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The child's health record as a book you turn.
 *
 * Every sheet is the designed artwork with the child's data written onto it, drawn by
 * [HealthBookPageRenderer] — the exact code that draws the exported PDF, so the parent reads the
 * same sheet they hand to a doctor.
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
    var page by remember { mutableIntStateOf(0) }

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
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // A book sits on something; the desk makes the turning leaf read as paper.
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFE8EEF9), Color(0xFFD7E1F2))
                            )
                        )
                ) {
                    if (zoomed) {
                        ZoomedSheet(current, page) { zoomed = false }
                    } else {
                        FlipBook(
                            book = current,
                            page = page,
                            onPageChange = { page = it },
                            onTap = { zoomed = true },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (page > 0) {
                            PageArrow(Alignment.CenterStart, isThai) { page-- }
                        }
                        if (page < current.pages.lastIndex) {
                            PageArrow(Alignment.CenterEnd, isThai) { page++ }
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
                                    text = if (isThai) "หน้า ${page + 1} / ${current.pages.size}"
                                    else "Page ${page + 1} of ${current.pages.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    current.pages[page].chapter,
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
 * A leaf of paper that swings on its spine.
 *
 * A real book does not slide sideways: a single sheet lifts, rotates about the binding, and lands
 * face-down on the other side, revealing what was underneath. That is modelled literally — the
 * turning leaf is one plane rotating 0°→−180° about its left edge, showing the current page while
 * it faces the reader and the *next* page once it has passed edge-on (because that is the reverse
 * of the same physical sheet). Backwards turns run the same motion in reverse about the same edge.
 *
 * Depth comes from `cameraDistance` and from a shadow that peaks when the leaf is edge-on, which
 * is when a real page is most nearly perpendicular to the light.
 */
@Composable
private fun FlipBook(
    book: HealthBook,
    page: Int,
    onPageChange: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // 0 = flat; +1 = fully turned forwards; −1 = fully turned backwards.
    val turn = remember { Animatable(0f) }

    val last = book.pages.lastIndex

    fun settle(target: Int) {
        scope.launch {
            turn.animateTo(target.toFloat(), tween(durationMillis = 420))
            // The leaf has landed: adopt the new page and reset, so the next turn starts flat.
            if (target != 0) onPageChange((page + target).coerceIn(0, last))
            turn.snapTo(0f)
        }
    }

    Box(
        modifier
            .pointerInput(page, last) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(page, last) {
                val width = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // Past a third of the way, the page falls the rest of the way on its own —
                        // the same commitment point a thumb feels on real paper.
                        settle(
                            when {
                                turn.value > 0.33f -> 1
                                turn.value < -0.33f -> -1
                                else -> 0
                            }
                        )
                    },
                    onDragCancel = { settle(0) }
                ) { _, dragAmount ->
                    // Drag left = turn forwards. Clamped at both covers: there is no page to lift
                    // before the first sheet or after the last.
                    val lo = if (page <= 0) 0f else -1f
                    val hi = if (page >= last) 0f else 1f
                    val next = (turn.value - dragAmount / width).coerceIn(lo, hi)
                    scope.launch { turn.snapTo(next) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val t = turn.value

        // What stays put while the leaf swings: turning forwards reveals the next page beneath;
        // turning backwards, the leaf lands on top of the page we are on, which stays visible.
        val underneath = when {
            t > 0f -> (page + 1).coerceAtMost(last)
            t < 0f -> page
            else -> page
        }
        Sheet(book, underneath, Modifier.fillMaxSize())

        if (t != 0f) {
            // The physical sheet: front is the page being turned, back is its reverse.
            val leafFront = if (t > 0f) page else (page - 1).coerceAtLeast(0)
            val leafBack = if (t > 0f) (page + 1).coerceAtMost(last) else page
            val angle = -180f * t
            val faceUp = abs(angle) < 90f

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = angle
                        // The spine: forwards turns hinge on the left edge, backwards on the right.
                        transformOrigin = TransformOrigin(if (t > 0f) 0f else 1f, 0.5f)
                        cameraDistance = 26f * density
                    }
            ) {
                Sheet(
                    book,
                    if (faceUp) leafFront else leafBack,
                    Modifier
                        .fillMaxSize()
                        // Un-mirror the reverse face: without this the back of the leaf reads
                        // backwards, which no sheet of paper does.
                        .graphicsLayer { if (!faceUp) rotationY = 180f }
                        .drawWithContent {
                            drawContent()
                            // Paper darkens as it turns away from the light, deepest edge-on.
                            val shade = (1f - abs(abs(angle) - 90f) / 90f) * 0.42f
                            drawRect(Color.Black.copy(alpha = shade))
                        }
                )
            }
        }
    }
}

/** One rendered sheet, at the size it will actually be shown. */
@Composable
private fun Sheet(book: HealthBook, index: Int, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        val bmp = rememberSheetBitmap(book, index)
        if (bmp == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H)
                    .shadow(8.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White),
                Alignment.Center
            ) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp)) }
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = book.pages.getOrNull(index)?.chapter,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H)
                    .shadow(8.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
            )
        }
    }
}

/**
 * Renders a sheet off the main thread, keyed on its index.
 *
 * Keyed rather than cached wholesale: an A4 raster is several megabytes and holding every sheet of
 * a long book would be the whole heap, so Compose disposing them with the composable is the point.
 */
@Composable
private fun rememberSheetBitmap(book: HealthBook, index: Int): Bitmap? {
    val context = LocalContext.current
    // 2× A4 at 72dpi: enough that the form's small print survives zooming, without the cost of a
    // print-resolution raster the screen could never show anyway.
    val scale = 2f
    val bitmap by produceState<Bitmap?>(initialValue = null, index, book) {
        value = withContext(Dispatchers.Default) {
            runCatching {
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
    }
    return bitmap
}

/** The sheet at natural size, scrolled the way you'd move paper under your eyes. */
@Composable
private fun ZoomedSheet(book: HealthBook, index: Int, onClose: () -> Unit) {
    val bmp = rememberSheetBitmap(book, index)
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = book.pages.getOrNull(index)?.chapter,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width((HealthBookPageRenderer.PAGE_W * 1.55f).dp)
                    .aspectRatio(HealthBookPageRenderer.PAGE_W / HealthBookPageRenderer.PAGE_H)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
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
            .padding(horizontal = 2.dp)
            .size(40.dp),
        shape = CircleShape,
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
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

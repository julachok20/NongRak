package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.util.BookBlock
import com.lullabyai.app.util.BookPage
import com.lullabyai.app.util.HealthBook
import com.lullabyai.app.util.HealthBookBuilder
import com.lullabyai.app.util.HealthBookPdf
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * The child's health record as a book you turn, not a page you scroll.
 *
 * Sheet 0 is the cover, sheet 1 the contents, then one chapter page per sheet. Turning is a
 * horizontal pager with a rotation applied per page, so a swipe reads as paper moving rather
 * than a list sliding sideways.
 *
 * The pages come from [HealthBookBuilder] — the same structure [HealthBookPdf] renders — so what
 * a parent reads here and what they hand to a doctor cannot drift apart.
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
    // Cover + contents + one sheet per content page.
    val pagerState = rememberPagerState(pageCount = { (current?.pages?.size ?: 0) + 2 })

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "สมุดสุขภาพ" else "Health Book",
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

            current == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(
                    if (isThai) "ยังไม่มีข้อมูลลูก" else "No child selected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    pageSpacing = 8.dp
                ) { index ->
                    val offset =
                        (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction

                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .graphicsLayer {
                                // The turning sheet pivots on its spine and tips away, which is
                                // what makes a swipe feel like paper rather than a carousel.
                                val f = offset.coerceIn(-1f, 1f)
                                rotationY = f * 18f
                                cameraDistance = 14f * density
                                transformOrigin = TransformOrigin(if (f > 0f) 1f else 0f, 0.5f)
                                alpha = 1f - (f.absoluteValue * 0.25f)
                            }
                    ) {
                        when (index) {
                            0 -> CoverSheet(current, isThai)
                            1 -> ContentsSheet(current, isThai)
                            else -> ContentSheet(
                                page = current.pages[index - 2],
                                pageNumber = index - 1,
                                totalPages = current.pages.size
                            )
                        }
                    }
                }

                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (pagerState.currentPage <= 1) ""
                        else "${pagerState.currentPage - 1} / ${current.pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        scope.launch {
                            message = if (isThai) "กำลังสร้างไฟล์ PDF..." else "Creating PDF..."
                            runCatching { HealthBookPdf.export(context, current, isThai) }
                                .onSuccess {
                                    message = null
                                    HealthBookPdf.share(context, it, current.childName, isThai)
                                }
                                // Named, not swallowed: a silent no-op on Export is exactly the
                                // failure a parent reports as "the button does nothing".
                                .onFailure { e ->
                                    message = (if (isThai) "สร้าง PDF ไม่สำเร็จ: "
                                    else "Could not create PDF: ") + (e.message ?: "unknown")
                                }
                        }
                    }) {
                        Icon(Icons.Filled.IosShare, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isThai) "บันทึกเป็น PDF" else "Export PDF")
                    }
                }
            }
        }
    }
}

/** Paper. A slight warmth separates the sheet from the app chrome behind it. */
@Composable
private fun Sheet(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxSize(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFFFDF8),
        shadowElevation = 10.dp
    ) { content() }
}

@Composable
private fun CoverSheet(book: HealthBook, isThai: Boolean) {
    Sheet {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF2743C4)).padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isThai) "สมุดสุขภาพประจำตัว" else "PERSONAL HEALTH RECORD",
                color = Color(0xFFC3CDF5),
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                book.childName,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(book.ageText, color = Color(0xFFC3CDF5), fontSize = 15.sp)
            Spacer(Modifier.height(36.dp))
            CoverRow(if (isThai) "วันเกิด" else "Date of birth", book.birthdate)
            CoverRow(if (isThai) "กรุ๊ปเลือด" else "Blood group", book.bloodGroup)
            CoverRow(if (isThai) "ออกเมื่อ" else "Issued", book.generatedAt)
            Spacer(Modifier.height(40.dp))
            Text(
                if (isThai) "ปัดไปทางซ้ายเพื่อเปิดอ่าน" else "Swipe left to open",
                color = Color(0xFF8FA3E8),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CoverRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF8FA3E8), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ContentsSheet(book: HealthBook, isThai: Boolean) {
    // Chapters, not pages: a contents list with "Immunisation (cont.)" in it is a page index
    // pretending to be a table of contents.
    val chapters = book.pages.mapIndexed { i, p -> p.chapter to (i + 1) }
        .groupBy({ it.first }, { it.second })
        .map { (chapter, numbers) -> chapter to numbers.min() }

    Sheet {
        Column(Modifier.fillMaxSize().padding(26.dp).verticalScroll(rememberScrollState())) {
            Text(
                if (isThai) "สารบัญ" else "Contents",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2559)
            )
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color(0x332743C4))
            Spacer(Modifier.height(12.dp))
            chapters.forEach { (chapter, page) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(chapter, fontSize = 14.sp, color = Color(0xFF1B2559))
                    Text("$page", fontSize = 13.sp, color = Color(0xFF6B7280))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (isThai)
                    "สมุดเล่มนี้สรุปจากข้อมูลที่บันทึกไว้ในแอปน้องรัก ไม่ใช่การวินิจฉัยทางการแพทย์"
                else
                    "Compiled from records kept in the NongRak app. Not a medical diagnosis.",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF)
            )
        }
    }
}

@Composable
private fun ContentSheet(page: BookPage, pageNumber: Int, totalPages: Int) {
    Sheet {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                page.chapter.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = Color(0xFF2743C4),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                page.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2559)
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0x332743C4))
            Spacer(Modifier.height(10.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                page.blocks.forEach { BlockView(it) }
            }

            HorizontalDivider(color = Color(0x1A2743C4))
            Text(
                "$pageNumber / $totalPages",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BlockView(block: BookBlock) {
    when (block) {
        is BookBlock.Heading -> Text(
            block.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B2559),
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )

        is BookBlock.Field -> Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                block.label,
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.weight(1f)
            )
            Text(
                block.value,
                fontSize = 13.sp,
                fontWeight = if (block.alert) FontWeight.Bold else FontWeight.Medium,
                color = if (block.alert) Color(0xFFB26A00) else Color(0xFF1B2559),
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.End
            )
        }

        is BookBlock.Paragraph -> Text(
            block.text,
            fontSize = 13.sp,
            color = Color(0xFF4B5563),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        is BookBlock.Row -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            block.cells.forEach { cell ->
                Text(
                    cell,
                    fontSize = 12.sp,
                    fontWeight = if (block.header) FontWeight.Bold else FontWeight.Normal,
                    color = if (block.header) Color(0xFF1B2559) else Color(0xFF4B5563),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is BookBlock.Note -> Text(
            block.text,
            fontSize = 13.sp,
            color = Color(0xFF059669),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        BookBlock.Divider -> HorizontalDivider(
            color = Color(0x142743C4),
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

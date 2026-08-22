@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.MedicalDocument
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.SurfaceContainer
import com.lullabyai.app.ui.theme.SurfaceContainerHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Unified source representation for documents to be viewed inside the app.
 */
sealed interface DocumentViewSource {
    val displayTitle: String
    val mime: String?

    data class Remote(
        val url: String,
        override val displayTitle: String,
        override val mime: String? = null
    ) : DocumentViewSource

    data class Local(
        val file: File,
        override val displayTitle: String,
        override val mime: String? = null
    ) : DocumentViewSource

    data class Content(
        val uri: Uri,
        override val displayTitle: String,
        override val mime: String? = null
    ) : DocumentViewSource

    data class Bytes(
        val bytes: ByteArray,
        override val displayTitle: String,
        override val mime: String? = null
    ) : DocumentViewSource

    data class Doc(
        val medicalDoc: MedicalDocument,
        val signedUrl: String? = null
    ) : DocumentViewSource {
        override val displayTitle: String get() = medicalDoc.fileName
        override val mime: String? get() = medicalDoc.mimeType
    }
}

/**
 * Fullscreen In-App Document Viewer Dialog.
 *
 * Renders PDFs and images directly inside the app without opening web browsers or external apps.
 */
@Composable
fun InAppDocumentViewerDialog(
    source: DocumentViewSource?,
    onDismiss: () -> Unit,
    isThai: Boolean = true
) {
    if (source == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        InAppDocumentViewer(
            source = source,
            onClose = onDismiss,
            isThai = isThai,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * In-App Document Viewer View Component.
 */
@Composable
fun InAppDocumentViewer(
    source: DocumentViewSource,
    onClose: () -> Unit,
    isThai: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loading by remember(source) { mutableStateOf(true) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var pdfPages by remember(source) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var localPdfFile by remember(source) { mutableStateOf<File?>(null) }
    var imageFileOrUrl by remember(source) { mutableStateOf<Any?>(null) }
    var isPdf by remember(source) { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Zoom & Pan state
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        if (newScale > 1f) {
            val maxOffsetX = (newScale - 1f) * 500f
            val maxOffsetY = (newScale - 1f) * 800f
            offset = Offset(
                x = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    LaunchedEffect(source, reloadTrigger) {
        loading = true
        error = null
        pdfPages = emptyList()
        localPdfFile = null
        imageFileOrUrl = null
        scale = 1f
        offset = Offset.Zero

        withContext(Dispatchers.IO) {
            try {
                when (source) {
                    is DocumentViewSource.Local -> {
                        val file = source.file
                        if (!file.exists()) {
                            error = if (isThai) "ไม่พบไฟล์ในเครื่อง" else "File not found on device"
                            loading = false
                            return@withContext
                        }
                        val mime = source.mime ?: guessMimeType(file.name)
                        if (mime.contains("pdf", ignoreCase = true) || file.name.endsWith(".pdf", ignoreCase = true)) {
                            isPdf = true
                            localPdfFile = file
                            pdfPages = renderPdfPages(context, file)
                        } else {
                            isPdf = false
                            imageFileOrUrl = file
                        }
                    }

                    is DocumentViewSource.Content -> {
                        val bytes = runCatching {
                            context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
                        }.getOrNull()

                        if (bytes == null || bytes.isEmpty()) {
                            error = if (isThai) "ไม่สามารถอ่านไฟล์ได้" else "Could not read file data"
                            loading = false
                            return@withContext
                        }

                        val mime = source.mime ?: context.contentResolver.getType(source.uri) ?: guessMimeType(source.displayTitle)
                        if (mime.contains("pdf", ignoreCase = true) || isPdfHeader(bytes)) {
                            isPdf = true
                            val tempFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
                            tempFile.outputStream().use { it.write(bytes) }
                            localPdfFile = tempFile
                            pdfPages = renderPdfPages(context, tempFile)
                        } else {
                            isPdf = false
                            imageFileOrUrl = bytes
                        }
                    }

                    is DocumentViewSource.Bytes -> {
                        val bytes = source.bytes
                        val mime = source.mime ?: guessMimeType(source.displayTitle)
                        if (mime.contains("pdf", ignoreCase = true) || isPdfHeader(bytes)) {
                            isPdf = true
                            val tempFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
                            tempFile.outputStream().use { it.write(bytes) }
                            localPdfFile = tempFile
                            pdfPages = renderPdfPages(context, tempFile)
                        } else {
                            isPdf = false
                            imageFileOrUrl = bytes
                        }
                    }

                    is DocumentViewSource.Remote -> {
                        val urlStr = source.url
                        val mime = source.mime ?: guessMimeType(source.displayTitle)

                        val tempFile = File(context.cacheDir, "remote_${System.currentTimeMillis()}.${if (mime.contains("pdf", true)) "pdf" else "tmp"}")
                        downloadFile(urlStr, tempFile)

                        if (tempFile.exists() && tempFile.length() > 0) {
                            val isPdfFile = mime.contains("pdf", ignoreCase = true) ||
                                source.displayTitle.endsWith(".pdf", ignoreCase = true) ||
                                isPdfFile(tempFile)

                            if (isPdfFile) {
                                isPdf = true
                                localPdfFile = tempFile
                                pdfPages = renderPdfPages(context, tempFile)
                            } else {
                                isPdf = false
                                imageFileOrUrl = tempFile
                            }
                        } else {
                            error = if (isThai) "ไม่สามารถโหลดเอกสารได้" else "Could not download document"
                        }
                    }

                    is DocumentViewSource.Doc -> {
                        val doc = source.medicalDoc
                        val urlStr = source.signedUrl

                        if (urlStr.isNullOrBlank()) {
                            error = if (isThai) "ไม่พบที่อยู่เอกสาร" else "Document URL not available"
                            loading = false
                            return@withContext
                        }

                        val mime = doc.mimeType ?: guessMimeType(doc.fileName)
                        val tempFile = File(context.cacheDir, "doc_${doc.id}_${System.currentTimeMillis()}.${if (mime.contains("pdf", true)) "pdf" else "tmp"}")
                        downloadFile(urlStr, tempFile)

                        if (tempFile.exists() && tempFile.length() > 0) {
                            val isPdfFile = mime.contains("pdf", ignoreCase = true) ||
                                doc.fileName.endsWith(".pdf", ignoreCase = true) ||
                                isPdfFile(tempFile)

                            if (isPdfFile) {
                                isPdf = true
                                localPdfFile = tempFile
                                pdfPages = renderPdfPages(context, tempFile)
                            } else {
                                isPdf = false
                                imageFileOrUrl = tempFile
                            }
                        } else {
                            error = if (isThai) "ไม่สามารถโหลดเอกสารได้" else "Could not download document"
                        }
                    }
                }
            } catch (e: Exception) {
                error = (if (isThai) "เกิดข้อผิดพลาดในการเปิดเอกสาร: " else "Failed to open document: ") + (e.localizedMessage ?: "")
            } finally {
                loading = false
            }
        }
    }

    val listState = rememberLazyListState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF1E1E1E)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top Bar with Controls
            Surface(
                color = Color(0xFF2D2D2D),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = if (isThai) "ปิด" else "Close",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = source.displayTitle.ifBlank { if (isThai) "เอกสาร" else "Document" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (isPdf) Color(0xFFE53935) else Primary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isPdf) "PDF" else "IMAGE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            if (isPdf && pdfPages.isNotEmpty()) {
                                Text(
                                    text = if (isThai) "${pdfPages.size} หน้า" else "${pdfPages.size} pages",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    // Reset zoom button if zoomed
                    if (scale > 1.05f) {
                        IconButton(onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        }) {
                            Icon(
                                Icons.Filled.ZoomOutMap,
                                contentDescription = if (isThai) "ขนาดปกติ" else "Reset zoom",
                                tint = Color.White
                            )
                        }
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            localPdfFile?.let { shareFile(context, it, isThai) }
                                ?: run {
                                    Toast.makeText(
                                        context,
                                        if (isThai) "เตรียมไฟล์สำหรับแชร์…" else "Preparing file to share…",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        },
                        enabled = !loading && error == null && localPdfFile != null
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = if (isThai) "แชร์เอกสาร" else "Share",
                            tint = Color.White
                        )
                    }

                    // Save to device downloads
                    IconButton(
                        onClick = {
                            localPdfFile?.let { saveToDownloads(context, it, source.displayTitle, isThai) }
                        },
                        enabled = !loading && error == null && localPdfFile != null
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = if (isThai) "บันทึกลงเครื่อง" else "Save to device",
                            tint = Color.White
                        )
                    }
                }
            }

            // Main Viewer Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .transformable(state = transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.5f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isThai) "กำลังเปิดเอกสาร…" else "Opening document…",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = error ?: "",
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { reloadTrigger++ },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isThai) "ลองใหม่อีกครั้ง" else "Try again")
                            }
                        }
                    }

                    isPdf && pdfPages.isNotEmpty() -> {
                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(pdfPages) { index, pageBitmap ->
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(8.dp, RoundedCornerShape(4.dp))
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Image(
                                                bitmap = pageBitmap.asImageBitmap(),
                                                contentDescription = if (isThai) "หน้า ${index + 1}" else "Page ${index + 1}",
                                                modifier = Modifier.fillMaxWidth(),
                                                contentScale = ContentScale.FillWidth
                                            )
                                        }
                                    }
                                }
                            }

                            // Floating page indicator badge
                            if (pdfPages.size > 1) {
                                val currentPage = (listState.firstVisibleItemIndex + 1).coerceAtMost(pdfPages.size)
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp)
                                ) {
                                    Text(
                                        text = if (isThai) "หน้า $currentPage / ${pdfPages.size}" else "Page $currentPage of ${pdfPages.size}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    !isPdf && imageFileOrUrl != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageFileOrUrl,
                                contentDescription = source.displayTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = if (isThai) "ไม่สามารถแสดงตัวอย่างไฟล์นี้ได้" else "Preview not available for this format",
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Helper Functions for PDF Rendering & File Operations
// ------------------------------------------------------------------------------------------------

private fun guessMimeType(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".heic") -> "image/heic"
        lower.endsWith(".txt") -> "text/plain"
        else -> "application/octet-stream"
    }
}

private fun isPdfHeader(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    return bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() // %PDF
}

private fun isPdfFile(file: File): Boolean {
    if (!file.exists() || file.length() < 4) return false
    return try {
        file.inputStream().use {
            val buf = ByteArray(4)
            val read = it.read(buf)
            read == 4 && isPdfHeader(buf)
        }
    } catch (e: Exception) {
        false
    }
}

private fun downloadFile(urlStr: String, destFile: File) {
    val url = URL(urlStr)
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 20000
    conn.instanceFollowRedirects = true
    conn.connect()

    if (conn.responseCode in 200..299) {
        conn.inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } else {
        throw IllegalStateException("HTTP ${conn.responseCode}: ${conn.responseMessage}")
    }
}

private fun renderPdfPages(context: Context, pdfFile: File): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)

    try {
        val density = context.resources.displayMetrics.density
        val pageCount = renderer.pageCount

        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            // Render at high DPI for crisp viewing on modern high-res screens
            val renderScale = (density * 1.5f).coerceIn(1.5f, 3.0f)
            val width = (page.width * renderScale).toInt().coerceIn(720, 2400)
            val height = (page.height * renderScale).toInt().coerceIn(720, 3400)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pages.add(bitmap)
        }
    } finally {
        renderer.close()
        pfd.close()
    }
    return pages
}

private fun shareFile(context: Context, file: File, isThai: Boolean) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (isPdfFile(file)) "application/pdf" else "image/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("document", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, if (isThai) "แชร์เอกสาร" else "Share document")
        )
    }.onFailure {
        Toast.makeText(
            context,
            if (isThai) "ไม่สามารถแชร์ไฟล์ได้" else "Could not share file",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun saveToDownloads(context: Context, sourceFile: File, title: String, isThai: Boolean) {
    runCatching {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val nongRakDir = File(downloadsDir, "NongRak").apply { if (!exists()) mkdirs() }
        val ext = if (isPdfFile(sourceFile)) ".pdf" else ""
        val safeName = title.ifBlank { "document" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val finalName = if (safeName.endsWith(".pdf", ignoreCase = true) || ext.isEmpty()) safeName else "$safeName$ext"
        val destFile = File(nongRakDir, finalName)

        sourceFile.copyTo(destFile, overwrite = true)
        Toast.makeText(
            context,
            if (isThai) "บันทึกในโฟลเดอร์ Downloads/NongRak เรียบร้อย" else "Saved to Downloads/NongRak",
            Toast.LENGTH_LONG
        ).show()
    }.onFailure {
        Toast.makeText(
            context,
            if (isThai) "บันทึกลงเครื่องไม่สำเร็จ: ${it.localizedMessage}" else "Failed to save: ${it.localizedMessage}",
            Toast.LENGTH_LONG
        ).show()
    }
}

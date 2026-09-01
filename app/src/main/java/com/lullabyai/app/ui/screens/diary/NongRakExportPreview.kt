package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the finished file actually is.
 *
 * Every figure is read off the rendered output rather than predicted from the settings. The design
 * shows "1080P · 30FPS · 24.5 MB"; printing those as constants would make them a caption rather
 * than a spec, and the size in particular cannot be known until the encoder has finished.
 */
data class NongRakExportSpecs(
    val widthPx: Int,
    val heightPx: Int,
    /** Null when the platform will not say - frame count became readable at API 28. */
    val fps: Float?,
    val bytes: Long,
    val durationMs: Long
) {
    /** "1080P" from the short edge, because an upright 1080x1920 clip is a 1080p clip. */
    val resolutionLabel: String
        get() = when (val shortEdge = minOf(widthPx, heightPx)) {
            0 -> "—"
            in 2000..Int.MAX_VALUE -> "4K"
            in 1400..1999 -> "1440P"
            in 1000..1399 -> "1080P"
            in 700..999 -> "720P"
            else -> "${shortEdge}P"
        }

    val sizeLabel: String
        get() = if (bytes <= 0) "—" else "%.1f MB".format(bytes / 1024.0 / 1024.0)

    val fpsLabel: String? get() = fps?.let { "%.0f FPS".format(it) }
}

/** Reads the real numbers off [uri]. Blocking, so it runs off the main thread. */
suspend fun readExportSpecs(context: Context, uri: Uri): NongRakExportSpecs =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)

            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rawWidth = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            // A portrait phone video is stored landscape with rotation 90; reporting it unrotated
            // would tell the parent their upright clip is 1920x1080.
            val swap = rotation == 90 || rotation == 270
            val width = if (swap) rawHeight else rawWidth
            val height = if (swap) rawWidth else rawHeight

            val fps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && durationMs > 0) {
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toIntOrNull()
                    ?.let { frames -> frames * 1000f / durationMs }
            } else {
                // Older platforms cannot tell us. Showing nothing beats showing a plausible guess.
                null
            }

            val bytes = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull() ?: 0L

            NongRakExportSpecs(width, height, fps, bytes, durationMs)
        } catch (e: Exception) {
            NongRakExportSpecs(0, 0, null, 0L, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

/**
 * The last look before the memory is kept.
 *
 * Sits after the render rather than before it, because the size and the frame rate do not exist
 * until the encoder has finished. "แก้ไขเพิ่มเติม" returns to the editor with the edit intact.
 */
@Composable
fun NongRakExportPreviewDialog(
    videoUri: Uri,
    specs: NongRakExportSpecs,
    isThai: Boolean,
    onSave: () -> Unit,
    onEditMore: () -> Unit
) {
    Dialog(
        onDismissRequest = onEditMore,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorCanvas)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = EditorSurface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onEditMore) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = EditorInk)
                        }
                        Text(
                            if (isThai) "ตรวจก่อนบันทึก" else "Review before saving",
                            color = EditorInk,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(48.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = BorderStroke(1.dp, Color.White.copy(0.6f)),
                        modifier = Modifier
                            .aspectRatio(
                                if (specs.widthPx > 0 && specs.heightPx > 0) {
                                    specs.widthPx.toFloat() / specs.heightPx
                                } else {
                                    9f / 16f
                                }
                            )
                            .fillMaxHeight()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(videoUri)
                                    // A real controller here, unlike the editor: this is playback
                                    // of a finished file, not a surface being drawn over.
                                    setMediaController(
                                        MediaController(ctx).also { it.setAnchorView(this) }
                                    )
                                    setOnPreparedListener {
                                        it.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SpecChip(specs.resolutionLabel, Modifier.weight(1f))
                        specs.fpsLabel?.let { SpecChip(it, Modifier.weight(1f)) }
                        SpecChip(specs.sizeLabel, Modifier.weight(1f))
                        SpecChip(formatClock(specs.durationMs), Modifier.weight(1f))
                    }

                    Text(
                        if (isThai) "ดูให้แน่ใจก่อนเก็บไว้ในไดอารี่ ถ้ายังไม่ถูกใจ กลับไปแก้ต่อได้"
                        else "Check it before it goes into the diary. Not right yet? Go back and keep editing.",
                        color = EditorMuted,
                        fontSize = 12.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onEditMore,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                null,
                                tint = EditorInk,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isThai) "แก้ไขเพิ่มเติม" else "Edit more", color = EditorInk)
                        }
                        Button(
                            onClick = onSave,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EditorInk),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.SaveAlt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isThai) "บันทึกและส่งออก" else "Save",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = EditorTrack.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Text(
            label,
            color = EditorInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp)
        )
    }
}

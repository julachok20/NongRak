package com.lullabyai.app.ui.screens.diary

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lullabyai.app.ui.mascot.Mascot
import com.lullabyai.app.ui.mascot.MascotMood
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// The Stitch design's own ink, which is nearly black rather than the app's Primary blue. An
// editor is judged against the picture inside it, so the chrome is deliberately quieter than the
// rest of the app.
internal val EditorInk = Color(0xFF020229)
internal val EditorCanvas = Color(0xFFFBF8FF)
internal val EditorSurface = Color(0xFFFFFFFF)
internal val EditorTrack = Color(0xFFDEE0FF)
internal val EditorOutline = Color(0xFFC8C5CF)
internal val EditorMuted = Color(0xFF47464E)

/** Playback speeds, as the Speed Adjustment popup lists them. */
internal val SPEED_STEPS = listOf(0.5f, 1f, 1.5f, 2f)

/** How many frames the filmstrip shows. Enough to recognise the clip, few enough to decode fast. */
private const val THUMBNAIL_COUNT = 8

/** A clip shorter than this is not a memory, it is a mis-drag. */
internal const val MIN_CLIP_MS = 500L

/**
 * One tool, one popup.
 *
 * The design puts every control behind a modal rather than in a docked tab strip, and on a phone
 * that turns out to matter: the preview keeps its full height, because no panel ever sits under it.
 */
internal enum class EditorTool(
    val icon: ImageVector,
    val titleTh: String,
    val titleEn: String
) {
    TRIM(Icons.Filled.ContentCut, "ตัดขอบ", "Trim"),
    SPEED(Icons.Filled.Speed, "ความเร็ว", "Speed"),
    FILTER(Icons.Filled.AutoAwesome, "ฟิลเตอร์", "Filter"),
    EFFECT(Icons.Filled.AutoFixHigh, "เอฟเฟกต์", "Effects"),
    RATIO(Icons.Filled.AspectRatio, "ปรับขนาด", "Resize"),
    MASCOT(Icons.Filled.Face, "มาสคอต", "Mascot"),
    TEXT(Icons.Filled.ClosedCaption, "ข้อความ", "Text")
}

/**
 * The Enchanted Editor.
 *
 * Replaces the previous studio, which collected choices and threw them away: every call site
 * discarded the chosen effect and showed a success toast, so บันทึก left the original file
 * untouched. Everything this screen offers now goes through
 * [NongRakMediaWatermarkEngine.renderStudioEdit] and comes back as a real clip.
 *
 * Three controls were removed rather than restyled, because none of them did anything: the time
 * capsule (collected a letter and a target date that were never stored), the outro toggle (the
 * outro is appended unconditionally at export), and the music picker (the project ships no audio
 * files to play).
 *
 * @param onReRecord non-null only where re-shooting makes sense - the Add Memory flow. A viewer
 *   looking at a saved memory has nothing to re-record, so the button is hidden rather than
 *   offered and then unable to work.
 * @param onApply receives the rendered clip and the edit that produced it. The URI is a
 *   FileProvider path into the cache, so a caller that wants to keep it must copy or upload it.
 */
@Composable
fun NongRakVideoStudioDialog(
    videoUri: Uri,
    title: String?,
    description: String?,
    memoryDate: String?,
    isThai: Boolean = true,
    childName: String? = null,
    onDismiss: () -> Unit,
    onReRecord: (() -> Unit)? = null,
    onApply: (Uri, NongRakStudioEdit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The edit IS the state. Undo and redo are then a cursor over a list, rather than a dozen
    // separate snapshots that can fall out of step with each other.
    val history = remember {
        mutableStateListOf(
            NongRakStudioEdit(
                captionText = title,
                stampStyle = NongRakStampStyle.PASTEL_CLOUD
            )
        )
    }
    var cursor by remember { mutableIntStateOf(0) }
    val edit = history[cursor]

    fun push(next: NongRakStudioEdit) {
        if (next == edit) return
        while (history.lastIndex > cursor) history.removeAt(history.lastIndex)
        history.add(next)
        cursor = history.lastIndex
    }

    // Speed opens first, as the design specifies. Null once the parent dismisses it.
    var activeTool by remember { mutableStateOf<EditorTool?>(EditorTool.SPEED) }

    var player by remember { mutableStateOf<VideoView?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isRendering by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableFloatStateOf(0f) }
    var isSuggesting by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<NongRakAutoEditor.Suggestion?>(null) }

    // The rendered file waiting to be approved, and what it measured.
    var pendingExport by remember { mutableStateOf<Pair<Uri, NongRakExportSpecs>?>(null) }

    // Keyed by the edit, so "แก้ไขเพิ่มเติม" and straight back does not re-encode identical work.
    // Going back and actually changing something invalidates it, because the key stops matching.
    var lastRender by remember { mutableStateOf<Pair<NongRakStudioEdit, Uri>?>(null) }

    val trimEnd = edit.trimEndMs ?: durationMs

    LaunchedEffect(videoUri) {
        thumbnails = withContext(Dispatchers.IO) { extractThumbnails(context, videoUri) }
    }

    // Polling rather than a listener: VideoView exposes no position callback, and 20 ticks a
    // second is cheap next to decoding the video itself.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let { positionMs = it.currentPosition.toLong() }
            if (durationMs > 0 && positionMs >= trimEnd) {
                player?.seekTo(edit.trimStartMs.toInt())
            }
            delay(50)
        }
    }

    fun seekTo(ms: Long) {
        positionMs = ms
        player?.seekTo(ms.toInt())
    }

    fun togglePlay() {
        player?.let { view ->
            if (isPlaying) {
                view.pause()
            } else {
                if (positionMs < edit.trimStartMs || positionMs >= trimEnd) {
                    view.seekTo(edit.trimStartMs.toInt())
                }
                view.start()
            }
            isPlaying = !isPlaying
        }
    }

    Dialog(
        onDismissRequest = { if (!isRendering) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorCanvas)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                EditorTopBar(
                    isThai = isThai,
                    busy = isRendering,
                    canUndo = cursor > 0,
                    canRedo = cursor < history.lastIndex,
                    onUndo = { if (cursor > 0) cursor-- },
                    onRedo = { if (cursor < history.lastIndex) cursor++ },
                    onBack = onDismiss,
                    onDone = {
                        scope.launch {
                            player?.pause()
                            isPlaying = false

                            val cached = lastRender?.takeIf { it.first == edit }?.second
                            val rendered = if (cached != null) {
                                cached
                            } else {
                                isRendering = true
                                renderProgress = 0f
                                val result = NongRakMediaWatermarkEngine.renderStudioEdit(
                                    context = context,
                                    videoUri = videoUri,
                                    edit = edit
                                ) { renderProgress = it }
                                isRendering = false
                                result.getOrElse { failure ->
                                    // Names what the encoder reported. "ไม่สำเร็จ" on its own has
                                    // cost this project hours before.
                                    Toast.makeText(
                                        context,
                                        if (isThai) "บันทึกวิดีโอไม่สำเร็จ: ${failure.message ?: "ตัวเข้ารหัสวิดีโอไม่ตอบสนอง"}"
                                        else "Could not save the video: ${failure.message ?: "the encoder did not respond"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }.also { lastRender = edit to it }
                            }

                            pendingExport = rendered to readExportSpecs(context, rendered)
                        }
                    }
                )

                EditorPreview(
                    videoUri = videoUri,
                    edit = edit,
                    isPlaying = isPlaying,
                    memoryDate = memoryDate,
                    isThai = isThai,
                    positionFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onPlayerReady = { view, duration ->
                        player = view
                        durationMs = duration
                    },
                    onTogglePlay = { togglePlay() },
                    // Dragging replaces the current entry rather than pushing every pixel onto the
                    // undo stack: one drag would otherwise leave a hundred steps to undo through.
                    onMove = { moved -> history[cursor] = moved },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                EditorTransportRow(
                    isThai = isThai,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    showReRecord = onReRecord != null,
                    onSkipStart = { seekTo(edit.trimStartMs) },
                    onSkipEnd = { seekTo((trimEnd - 200).coerceAtLeast(edit.trimStartMs)) },
                    onTogglePlay = { togglePlay() },
                    onReRecord = {
                        player?.pause()
                        isPlaying = false
                        onReRecord?.invoke()
                    }
                )

                AiSuggestBar(
                    isThai = isThai,
                    busy = isSuggesting,
                    enabled = !isRendering && durationMs > 0,
                    onClick = {
                        scope.launch {
                            isSuggesting = true
                            player?.pause()
                            isPlaying = false
                            val result = NongRakAutoEditor.suggest(
                                context = context,
                                videoUri = videoUri,
                                current = edit,
                                childName = childName,
                                isThai = isThai
                            )
                            isSuggesting = false
                            result
                                .onSuccess { suggestion = it }
                                .onFailure { failure ->
                                    Toast.makeText(
                                        context,
                                        if (isThai) "วิเคราะห์คลิปไม่สำเร็จ: ${failure.message ?: "อ่านไฟล์ไม่ได้"}"
                                        else "Could not analyse the clip: ${failure.message ?: "unreadable file"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                )

                EditorToolRow(
                    isThai = isThai,
                    active = activeTool,
                    onSelect = { activeTool = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }

            if (isRendering) {
                RenderingScrim(progress = renderProgress, isThai = isThai)
            }
        }

        pendingExport?.let { (rendered, specs) ->
            NongRakExportPreviewDialog(
                videoUri = rendered,
                specs = specs,
                isThai = isThai,
                onSave = {
                    onApply(rendered, edit)
                    onDismiss()
                },
                onEditMore = { pendingExport = null }
            )
        }

        suggestion?.let { proposed ->
            AiSuggestionSheet(
                suggestion = proposed,
                isThai = isThai,
                onDismiss = { suggestion = null },
                onAccept = {
                    push(proposed.edit)
                    suggestion = null
                }
            )
        }

        activeTool?.let { tool ->
            EditorToolSheet(
                tool = tool,
                edit = edit,
                isThai = isThai,
                thumbnails = thumbnails,
                durationMs = durationMs,
                positionMs = positionMs,
                trimEndMs = trimEnd,
                onSeek = { seekTo(it) },
                onChange = { push(it) },
                onDismiss = { activeTool = null }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorTopBar(
    isThai: Boolean,
    busy: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Surface(color = EditorSurface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = EditorInk)
            }

            Text(
                if (isThai) "สตูดิโอต้องมนต์" else "Enchanted Editor",
                color = EditorInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo && !busy,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Filled.Undo,
                        if (isThai) "เลิกทำ" else "Undo",
                        tint = if (canUndo && !busy) EditorInk else EditorOutline,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo && !busy,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Filled.Redo,
                        if (isThai) "ทำซ้ำ" else "Redo",
                        tint = if (canRedo && !busy) EditorInk else EditorOutline,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onDone, enabled = !busy) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        if (isThai) "บันทึก" else "Done",
                        tint = if (busy) EditorOutline else EditorInk,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }
        }
    }
}

/** Transport controls and the re-record escape hatch, as the design lays them out. */
@Composable
private fun EditorTransportRow(
    isThai: Boolean,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    showReRecord: Boolean,
    onSkipStart: () -> Unit,
    onSkipEnd: () -> Unit,
    onTogglePlay: () -> Unit,
    onReRecord: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showReRecord) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .clickable { onReRecord() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Filled.VideocamOff,
                    null,
                    tint = EditorMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    if (isThai) "บันทึกใหม่" else "Re-record",
                    color = EditorMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onSkipStart, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    null,
                    tint = EditorInk,
                    modifier = Modifier.size(20.dp)
                )
            }
            Surface(shape = CircleShape, color = EditorInk, modifier = Modifier.size(40.dp)) {
                Box(
                    modifier = Modifier.clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isThai) "เล่น" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            IconButton(onClick = onSkipEnd, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.SkipNext,
                    null,
                    tint = EditorInk,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            "${formatClock(positionMs)} / ${formatClock(durationMs)}",
            color = EditorMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * The one-tap suggestion, sitting above the tools rather than among them.
 *
 * It is not a seventh way to change a setting - it changes several at once - so putting it in the
 * tool row would make it look like a peer of ฟิลเตอร์.
 */
@Composable
private fun AiSuggestBar(
    isThai: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (enabled && !busy) EditorInk else EditorOutline)
            .clickable(enabled = enabled && !busy) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(15.dp)
            )
        } else {
            Icon(
                Icons.Filled.AutoFixHigh,
                null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            when {
                busy && isThai -> "กำลังดูคลิป…"
                busy -> "Watching the clip…"
                isThai -> "AI แนะนำการแก้ไข"
                else -> "Suggest an edit"
            },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * What the suggestion would change, before it changes anything.
 *
 * Accepting pushes one entry onto the undo stack, so the whole suggestion comes back off in a
 * single tap. An assistant that rearranges a parent's work irreversibly is not a helpful one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSuggestionSheet(
    suggestion: NongRakAutoEditor.Suggestion,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = EditorSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = EditorOutline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.AutoFixHigh,
                    null,
                    tint = EditorInk,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    if (isThai) "AI แนะนำการแก้ไข" else "Suggested edit",
                    color = EditorInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                if (isThai) suggestion.reasonTh else suggestion.reasonEn,
                color = EditorMuted,
                fontSize = 13.sp
            )

            suggestion.edit.captionText?.takeIf { it.isNotBlank() }?.let { caption ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EditorTrack.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "“$caption”",
                        color = EditorInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }

            if (!suggestion.captionFromAi) {
                Text(
                    if (isThai) "เขียนข้อความให้ไม่ได้ตอนนี้ ส่วนการตัดและโทนภาพทำจากในเครื่องแล้ว"
                    else "Could not write a caption just now. The cut and the tone were worked out on the phone.",
                    color = EditorMuted,
                    fontSize = 10.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isThai) "ไม่ใช้" else "No thanks", color = EditorInk)
                }
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EditorInk),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isThai) "ใช้เลย" else "Use it", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                if (isThai) "กด เลิกทำ ด้านบนเพื่อย้อนกลับได้ตลอด"
                else "Undo at the top takes all of it back",
                color = EditorMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun RenderingScrim(progress: Float, isThai: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorInk.copy(alpha = 0.45f))
            // Swallows taps so the parent cannot start a second export on top of the first.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = EditorSurface, shadowElevation = 12.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 24.dp)
            ) {
                Text(
                    if (isThai) "กำลังลงเวทมนตร์…" else "Weaving the magic…",
                    color = EditorInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = EditorInk,
                    trackColor = EditorTrack,
                    modifier = Modifier.width(190.dp)
                )
                Text(
                    if (isThai) "${(progress * 100).roundToInt()}%  •  อย่าปิดหน้านี้"
                    else "${(progress * 100).roundToInt()}%  •  keep this screen open",
                    color = EditorMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorPreview(
    videoUri: Uri,
    edit: NongRakStudioEdit,
    isPlaying: Boolean,
    memoryDate: String?,
    isThai: Boolean,
    positionFraction: Float,
    onPlayerReady: (VideoView, Long) -> Unit,
    onTogglePlay: () -> Unit,
    onMove: (NongRakStudioEdit) -> Unit,
    modifier: Modifier = Modifier
) {
    val ratio = edit.aspectRatio?.let { it.ratioWidth / it.ratioHeight } ?: (9f / 16f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.dp, Color.White.copy(0.6f)),
            modifier = Modifier
                .aspectRatio(ratio)
                .fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(videoUri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                onPlayerReady(this, mp.duration.toLong())
                                seekTo(1)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                PreviewDecorations(
                    edit = edit,
                    memoryDate = memoryDate,
                    isThai = isThai,
                    onMove = onMove
                )

                if (!isPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(0.85f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(54.dp)
                            .clickable { onTogglePlay() }
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            if (isThai) "เล่น" else "Play",
                            tint = EditorInk,
                            modifier = Modifier.padding(13.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onTogglePlay() }
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(positionFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

/**
 * What the finished clip will carry, drawn over the preview.
 *
 * The particle layer comes from the SAME routine the encoder uses, so the preview cannot promise a
 * sparkle the export does not draw. Colour filters are absent here on purpose - they are GL
 * shaders with no cheap Compose equivalent, and the Filter popup says so rather than showing an
 * approximation the file will not match.
 */
@Composable
private fun PreviewDecorations(
    edit: NongRakStudioEdit,
    memoryDate: String?,
    isThai: Boolean,
    onMove: (NongRakStudioEdit) -> Unit
) {
    val particles = remember(edit.effect) {
        NongRakMediaWatermarkEngine.particleFrame(edit.effect, 540, 960)
    }

    if (particles != null) {
        // Matches DriftingOverlay's slow breath, so the preview moves the way the file will.
        val transition = rememberInfiniteTransition(label = "particles")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        Image(
            bitmap = particles.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(pulse)
        )
    }

    // Both overlays are placed by their centre in fraction-of-frame coordinates, the same units
    // the renderer uses, so what the parent drags to is where it ends up in the file.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val frameWidth = constraints.maxWidth.toFloat()
        val frameHeight = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        edit.mascot.art?.let { art ->
            val side = with(density) { (minOf(frameWidth, frameHeight) * 0.28f).toDp() }
            DraggableOverlay(
                x = edit.mascotX,
                y = edit.mascotY,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                onMove = { nx, ny -> onMove(edit.copy(mascotX = nx, mascotY = ny)) }
            ) {
                // The real companion, not a picture of one - the same rig, art and idle loop the
                // chat screen uses, so the parent recognises who they picked.
                Mascot(
                    art = art,
                    mood = MascotMood.IDLE,
                    animateFace = false,
                    modifier = Modifier.size(side)
                )
            }
        }

        if (!edit.captionText.isNullOrBlank()) {
            DraggableOverlay(
                x = edit.captionX,
                y = edit.captionY,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                onMove = { nx, ny -> onMove(edit.copy(captionX = nx, captionY = ny)) }
            ) {
                NongRakMilestoneStampBadge(
                    title = edit.captionText,
                    subtitle = formatMemoryDate(memoryDate, isThai),
                    style = edit.stampStyle ?: NongRakStampStyle.PASTEL_CLOUD
                )
            }
        }
    }
}

/**
 * Positions [content] by its centre at (x, y) in fraction-of-frame coordinates, and lets it be
 * dragged.
 *
 * The drag reports back in the same fractions rather than pixels, because the preview and the
 * exported frame are different sizes and only the fraction survives the difference.
 */
@Composable
private fun DraggableOverlay(
    x: Float,
    y: Float,
    frameWidth: Float,
    frameHeight: Float,
    onMove: (Float, Float) -> Unit,
    content: @Composable () -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (x * frameWidth - size.width / 2f).roundToInt(),
                    (y * frameHeight - size.height / 2f).roundToInt()
                )
            }
            .onSizeChanged { size = it }
            .pointerInput(frameWidth, frameHeight) {
                detectDragGestures { change, drag ->
                    change.consume()
                    if (frameWidth <= 0f || frameHeight <= 0f) return@detectDragGestures
                    onMove(
                        (x + drag.x / frameWidth).coerceIn(0f, 1f),
                        (y + drag.y / frameHeight).coerceIn(0f, 1f)
                    )
                }
            }
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

/** mm:ss, which is the only precision a phone-sized filmstrip can honestly show. */
internal fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

/**
 * Evenly spaced frames for the filmstrip.
 *
 * OPTION_CLOSEST_SYNC rather than OPTION_CLOSEST: seeking to an exact frame decodes everything
 * since the previous keyframe, which for eight positions in a long clip is several seconds the
 * parent spends looking at an empty strip.
 */
private fun extractThumbnails(context: android.content.Context, uri: Uri): List<Bitmap> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: return emptyList()
        (0 until THUMBNAIL_COUNT).mapNotNull { index ->
            val atUs = durationMs * 1000L * index / THUMBNAIL_COUNT
            retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    } catch (e: Exception) {
        // An unreadable strip is a cosmetic loss; trimming still works off the clock.
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}

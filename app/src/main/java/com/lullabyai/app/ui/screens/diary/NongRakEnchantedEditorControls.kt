package com.lullabyai.app.ui.screens.diary

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong

/**
 * The row of tools under the preview.
 *
 * Scrolls horizontally rather than shrinking to fit: seven labelled targets squeezed into a phone
 * width become seven unreadable ones, and this row is the whole navigation of the editor.
 */
@Composable
internal fun EditorToolRow(
    isThai: Boolean,
    active: EditorTool?,
    onSelect: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = EditorSurface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            items(EditorTool.entries) { tool ->
                val selected = tool == active
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) EditorInk else Color.Transparent)
                        .clickable { onSelect(tool) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .widthIn(min = 46.dp)
                ) {
                    Icon(
                        tool.icon,
                        null,
                        tint = if (selected) Color.White else EditorInk,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        if (isThai) tool.titleTh else tool.titleEn,
                        color = if (selected) Color.White else EditorMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * One modal per tool.
 *
 * A single sheet with a `when` inside it, rather than seven sheet call sites: the chrome, the
 * title and the dismiss behaviour then cannot drift apart between tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorToolSheet(
    tool: EditorTool,
    edit: NongRakStudioEdit,
    isThai: Boolean,
    thumbnails: List<Bitmap>,
    durationMs: Long,
    positionMs: Long,
    trimEndMs: Long,
    onSeek: (Long) -> Unit,
    onChange: (NongRakStudioEdit) -> Unit,
    onDismiss: () -> Unit
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isThai) tool.titleTh else tool.titleEn,
                    color = EditorInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        if (isThai) "ตกลง" else "Done",
                        color = EditorInk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            when (tool) {
                EditorTool.TRIM -> TrimPopup(
                    edit = edit,
                    isThai = isThai,
                    thumbnails = thumbnails,
                    durationMs = durationMs,
                    positionMs = positionMs,
                    trimEndMs = trimEndMs,
                    onSeek = onSeek,
                    onChange = onChange
                )
                EditorTool.SPEED -> SpeedPopup(edit, isThai, onChange)
                EditorTool.FILTER -> FilterPopup(edit, isThai, onChange)
                EditorTool.EFFECT -> EffectPopup(edit, isThai, onChange)
                EditorTool.RATIO -> RatioPopup(edit, isThai, onChange)
                EditorTool.MASCOT -> MascotPopup(edit, isThai, onChange)
                EditorTool.TEXT -> TextPopup(edit, isThai, onChange)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Popups
// ---------------------------------------------------------------------------------------------

@Composable
private fun SpeedPopup(
    edit: NongRakStudioEdit,
    isThai: Boolean,
    onChange: (NongRakStudioEdit) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SPEED_STEPS.forEach { speed ->
            ChoicePill(
                label = if (speed == 1f) "1x" else "${speed}x",
                selected = edit.speed == speed,
                modifier = Modifier.weight(1f),
                onClick = { onChange(edit.copy(speed = speed)) }
            )
        }
    }
    Text(
        if (isThai) "เสียงจะถูกปรับตามความเร็วด้วย เกิน 2 เท่าเสียงพูดจะเริ่มฟังไม่ออก"
        else "Audio is retimed to match. Past 2x a voice stops sounding like one.",
        color = EditorMuted,
        fontSize = 10.sp
    )
}

@Composable
private fun TrimPopup(
    edit: NongRakStudioEdit,
    isThai: Boolean,
    thumbnails: List<Bitmap>,
    durationMs: Long,
    positionMs: Long,
    trimEndMs: Long,
    onSeek: (Long) -> Unit,
    onChange: (NongRakStudioEdit) -> Unit
) {
    EditorTimeline(
        thumbnails = thumbnails,
        durationMs = durationMs,
        positionMs = positionMs,
        trimStartMs = edit.trimStartMs,
        trimEndMs = trimEndMs,
        keepAudio = edit.keepOriginalAudio,
        isThai = isThai,
        onSeek = onSeek,
        onTrimChange = { start, end -> onChange(edit.copy(trimStartMs = start, trimEndMs = end)) }
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isThai) "เก็บเสียงจากคลิป" else "Keep the clip's own sound",
                color = EditorInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (isThai) "เสียงหัวเราะของลูกมักเป็นสิ่งที่อยากเก็บที่สุด"
                else "A child's laugh is usually the part worth keeping",
                color = EditorMuted,
                fontSize = 10.sp
            )
        }
        Switch(
            checked = edit.keepOriginalAudio,
            onCheckedChange = { onChange(edit.copy(keepOriginalAudio = it)) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EditorInk
            )
        )
    }
}

@Composable
private fun FilterPopup(
    edit: NongRakStudioEdit,
    isThai: Boolean,
    onChange: (NongRakStudioEdit) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(NongRakVideoFilter.entries) { filter ->
            FilterCard(
                filter = filter,
                selected = edit.filter == filter,
                isThai = isThai,
                onClick = { onChange(edit.copy(filter = filter)) }
            )
        }
    }
    Text(
        if (isThai) "โทนสีจะเห็นผลจริงหลังกดบันทึก เพราะทำงานบนตัวเร่งกราฟิกตอนเรนเดอร์"
        else "Colour shows after saving - the grade runs on the GPU at render time.",
        color = EditorMuted,
        fontSize = 10.sp
    )
}

@Composable
private fun EffectPopup(
    edit: NongRakStudioEdit,
    isThai: Boolean,
    onChange: (NongRakStudioEdit) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(NongRakVideoEffect.entries) { effect ->
            EffectCard(
                effect = effect,
                selected = edit.effect == effect,
                isThai = isThai,
                onClick = { onChange(edit.copy(effect = effect)) }
            )
        }
    }
    Text(
        if (isThai) "ละอองและกลีบดอกเห็นได้ทันทีในตัวอย่าง และใส่ร่วมกับฟิลเตอร์ได้"
        else "Sparkles and petals show in the preview, and stack with a filter.",
        color = EditorMuted,
        fontSize = 10.sp
    )
}

@Composable
private fun RatioPopup(
    edit: NongRakStudioEdit,
    isThai: Boolean,
    onChange: (NongRakStudioEdit) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StudioAspectRatio.entries.forEach { ratio ->
            ChoicePill(
                label = ratio.id.replace('_', ':'),
                icon = ratio.icon,
                selected = edit.aspectRatio == ratio,
                modifier = Modifier.weight(1f),
                onClick = {
                    onChange(edit.copy(aspectRatio = if (edit.aspectRatio == ratio) null else ratio))
                }
            )
        }
    }
    Text(
        if (isThai) "แตะซ้ำเพื่อกลับไปใช้สัดส่วนเดิมของคลิป"
        else "Tap again to go back to the clip's own framing",
        color = EditorMuted,
        fontSize = 10.sp
    )
}

@Composable
private fun MascotPopup(
    edit: NongRakStudioEdit,
    isThai: Boolean,
    onChange: (NongRakStudioEdit) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(NongRakMascotSticker.entries) { mascot ->
            val selected = edit.mascot == mascot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) EditorInk else EditorTrack.copy(alpha = 0.45f))
                    .clickable { onChange(edit.copy(mascot = mascot)) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Image(
                    painter = painterResource(mascot.drawableRes),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    if (isThai) mascot.nameTh else mascot.nameEn,
                    color = if (selected) Color.White else EditorInk,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (edit.mascot != NongRakMascotSticker.NONE) {
        SectionLabel(if (isThai) "ตำแหน่ง" else "Position")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MascotPosition.entries.forEach { position ->
                ChoicePill(
                    label = if (isThai) position.titleTh else position.titleEn,
                    selected = edit.mascotPosition == position,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(edit.copy(mascotPosition = position)) }
                )
            }
        }
        Text(
            if (isThai) "มุมขวาล่างสงวนไว้สำหรับตราลิขสิทธิ์น้องรัก"
            else "The bottom-right corner is reserved for the NongRak licence badge",
            color = EditorMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TextPopup(
    edit: NongRakStudioEdit,
    isThai: Boolean,
    onChange: (NongRakStudioEdit) -> Unit
) {
    OutlinedTextField(
        value = edit.captionText.orEmpty(),
        onValueChange = { onChange(edit.copy(captionText = it)) },
        placeholder = {
            Text(
                if (isThai) "เช่น ก้าวแรกของน้องกีก้า" else "e.g. Giga's first steps",
                fontSize = 13.sp
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EditorInk,
            unfocusedBorderColor = EditorOutline,
            focusedTextColor = EditorInk,
            unfocusedTextColor = EditorInk
        ),
        modifier = Modifier.fillMaxWidth()
    )

    SectionLabel(if (isThai) "สไตล์ตราประทับ" else "Stamp style")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NongRakStampStyle.entries.forEach { style ->
            val selected = edit.stampStyle == style
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(style.badgeColor)
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = if (selected) EditorInk else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onChange(edit.copy(stampStyle = style)) },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------------------------

/**
 * The filmstrip, playhead and trim handles.
 *
 * One clip only. The design shows Clip 1 and Clip 2, but stitching several videos into one export
 * is a different feature with its own state model, and half-building it would leave a track the
 * parent can drag things onto that never reach the file.
 */
@Composable
private fun EditorTimeline(
    thumbnails: List<Bitmap>,
    durationMs: Long,
    positionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    keepAudio: Boolean,
    isThai: Boolean,
    onSeek: (Long) -> Unit,
    onTrimChange: (Long, Long?) -> Unit
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    fun msAt(x: Float): Long =
        if (trackWidthPx <= 0f || durationMs <= 0L) 0L
        else ((x / trackWidthPx).coerceIn(0f, 1f) * durationMs).roundToLong()

    fun fractionOf(ms: Long): Float =
        if (durationMs <= 0L) 0f else (ms.toFloat() / durationMs).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EditorTrack.copy(alpha = 0.35f))
            .border(1.dp, EditorOutline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatClock(positionMs),
                color = EditorInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isThai) "ช่วงที่เลือก ${formatClock(trimEndMs - trimStartMs)}"
                else "Selection ${formatClock(trimEndMs - trimStartMs)}",
                color = EditorMuted,
                fontSize = 10.sp
            )
            Text(formatClock(durationMs), color = EditorMuted, fontSize = 10.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(durationMs) {
                    detectTapGestures { offset -> onSeek(msAt(offset.x)) }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (thumbnails.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(EditorTrack)
                    )
                } else {
                    thumbnails.forEach { frame ->
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Everything outside the selection is dimmed rather than hidden, so the parent can
            // still see what they are cutting away and drag it back.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fractionOf(trimStartMs))
                    .background(EditorCanvas.copy(alpha = 0.72f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(1f - fractionOf(trimEndMs))
                    .background(EditorCanvas.copy(alpha = 0.72f))
            )

            TrimHandle(
                fraction = fractionOf(trimStartMs),
                trackWidthPx = trackWidthPx,
                density = density,
                onDrag = { x ->
                    val next = msAt(x).coerceAtMost(trimEndMs - MIN_CLIP_MS)
                    onTrimChange(next.coerceAtLeast(0L), trimEndMs.takeIf { it < durationMs })
                }
            )
            TrimHandle(
                fraction = fractionOf(trimEndMs),
                trackWidthPx = trackWidthPx,
                density = density,
                onDrag = { x ->
                    val next = msAt(x).coerceAtLeast(trimStartMs + MIN_CLIP_MS)
                    onTrimChange(trimStartMs, next.coerceAtMost(durationMs))
                }
            )

            Box(
                modifier = Modifier
                    .offset(x = with(density) { (fractionOf(positionMs) * trackWidthPx).toDp() })
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(EditorInk)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (keepAudio) Color(0xFFD4E6E5) else EditorTrack.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Icon(
                if (keepAudio) Icons.Filled.Mic else Icons.Filled.MicOff,
                null,
                tint = EditorInk,
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (keepAudio) (if (isThai) "เสียงต้นฉบับ" else "Original audio")
                else (if (isThai) "ปิดเสียง" else "Muted"),
                color = EditorInk,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TrimHandle(
    fraction: Float,
    trackWidthPx: Float,
    density: Density,
    onDrag: (Float) -> Unit
) {
    var dragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset(
                x = with(density) {
                    (fraction * trackWidthPx - 7.dp.toPx()).coerceAtLeast(0f).toDp()
                }
            )
            .fillMaxHeight()
            .width(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(EditorInk)
            .pointerInput(trackWidthPx) {
                detectDragGestures(
                    onDragStart = { dragX = fraction * trackWidthPx },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        onDrag(dragX)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(16.dp)
                .background(Color.White, RoundedCornerShape(1.dp))
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = EditorMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) EditorInk else EditorTrack.copy(alpha = 0.45f))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = if (selected) Color.White else EditorInk,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            color = if (selected) Color.White else EditorInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EffectCard(
    effect: NongRakVideoEffect,
    selected: Boolean,
    isThai: Boolean,
    onClick: () -> Unit
) {
    PresetCard(
        icon = effect.icon,
        tint = effect.primaryColor,
        label = if (isThai) effect.titleTh else effect.titleEn,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun FilterCard(
    filter: NongRakVideoFilter,
    selected: Boolean,
    isThai: Boolean,
    onClick: () -> Unit
) {
    PresetCard(
        icon = filter.icon,
        tint = filter.swatch,
        label = if (isThai) filter.titleTh else filter.titleEn,
        selected = selected,
        onClick = onClick
    )
}

/** Shared shape for the two preset rows, so a filter and an effect read as the same kind of thing. */
@Composable
private fun PresetCard(
    icon: ImageVector,
    tint: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) EditorInk else EditorTrack.copy(alpha = 0.45f))
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Text(
            label,
            color = if (selected) Color.White else EditorInk,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

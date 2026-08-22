@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lullabyai.app.R
import com.lullabyai.app.ui.theme.*
import kotlin.math.sin

/** Exclusive NongRak Magic FX Presets (CapCut/TikTok minimal style) */
enum class NongRakVideoEffect(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val icon: ImageVector,
    val primaryColor: Color
) {
    NONE("none", "ต้นฉบับ", "Original", Icons.Filled.Block, Color.Gray),
    STARLIGHT("starlight", "ละอองดาวนำโชค", "Starlight Sparkles", Icons.Filled.AutoAwesome, Color(0xFFFFD54F)),
    BUBBLES("bubbles", "ฟองสบู่วิเศษ", "Dream Bubbles", Icons.Filled.BlurCircular, Color(0xFF81D4FA)),
    BLOSSOM("blossom", "กลีบดอกไม้ละมุน", "Cherry Blossoms", Icons.Filled.LocalFlorist, Color(0xFFF48FB1)),
    GOLDEN_CROWN("crown", "มงกุฎก้าวสำคัญ", "Milestone Crown", Icons.Filled.EmojiEvents, Color(0xFFFFB300)),
    RAINBOW_AURA("rainbow", "แสงออโรร่า", "Rainbow Aura", Icons.Filled.FilterVintage, Color(0xFFCE93D8)),
    VINTAGE_FILM("vintage", "ฟิล์มละมุนตา", "Vintage Film", Icons.Filled.CameraRoll, Color(0xFFFFCC80)),
    TOY_KINGDOM("toys", "ดินแดนของเล่น", "Toy Kingdom", Icons.Filled.SmartToy, Color(0xFFFF8A65))
}

/** Exclusive NongRak Lullaby & Mood Beats */
enum class NongRakMusicPreset(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val durationText: String
) {
    ORIGINAL("original", "เสียงต้นฉบับ", "Original Audio", "0:00"),
    MOON_LULLABY("moon_lullaby", "เพลงกล่อมพระจันทร์", "Moon Lullaby", "2:15"),
    FIRST_WALTZ("first_waltz", "วอลต์ซก้าวแรก", "Baby's Waltz", "1:45"),
    GENTLE_MORNING("gentle_morning", "ทำนองรุ่งอรุณ", "Morning Melody", "2:30"),
    SOFT_CLOUD("soft_cloud", "ปุยเมฆในฝัน", "Soft Cloud", "3:10"),
    MUSIC_BOX("music_box", "กล่องดนตรีหรรษา", "Music Box", "1:20")
}

/** NongRak Mascot Sticker Collection */
enum class NongRakMascotSticker(
    val id: String,
    val nameTh: String,
    val nameEn: String,
    val drawableRes: Int,
    val actionText: String
) {
    NONE("none", "ไม่ใส่มาสคอต", "No Mascot", R.drawable.ic_nongrak_logo, "ไม่มี"),
    NONGRAK_GREETING("nr_greeting", "น้องรัก โบกมือ", "NongRak Waving", R.drawable.nongrak_mascot_greeting, "โบกมือทักทาย"),
    NONGRAK_HAPPY("nr_happy", "น้องรัก ปรบมือ", "NongRak Clapping", R.drawable.nongrak_mascot_happy, "ปรบมือยินดี"),
    NONGRAK_CARING("nr_caring", "น้องรัก ส่งกอดหัวใจ", "NongRak Heart Hug", R.drawable.nongrak_mascot_caring, "ส่งกอดหัวใจ"),
    NONGRAK_HERO("nr_hero", "น้องรัก ก้าวแรกคนเก่ง", "NongRak Star Hero", R.drawable.nongrak_mascot_hero, "เชียร์ก้าวสำคัญ"),
    PINGU_HAPPY("pingu_happy", "พังกุ สดใส", "Pingu Cheerful", R.drawable.pingu_mascot_happy, "ร่าเริง"),
    BUNNY_HAPPY("bunny_happy", "กระต่ายน้อย", "Bunny Sweet", R.drawable.bunny_mascot_happy, "ยิ้มหวาน"),
    COCO_HAPPY("coco_happy", "โคโค่ สนุกสนาน", "Coco Playful", R.drawable.coco_mascot_happy, "สดใส")
}

/** Mascot Screen Positions */
enum class MascotPosition(val id: String, val titleTh: String, val titleEn: String, val alignment: Alignment) {
    BOTTOM_LEFT("bottom_left", "ล่างซ้าย", "Bottom Left", Alignment.BottomStart),
    TOP_LEFT("top_left", "บนซ้าย", "Top Left", Alignment.TopStart),
    TOP_RIGHT("top_right", "บนขวา", "Top Right", Alignment.TopEnd),
    BOTTOM_CENTER("bottom_center", "ล่างกลาง", "Bottom Center", Alignment.BottomCenter)
}

/** AI Auto-Reformat Aspect Ratios */
enum class StudioAspectRatio(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val icon: ImageVector,
    val ratioWidth: Float,
    val ratioHeight: Float
) {
    VERTICAL_9_16("9_16", "9:16 สตอรี่ / Reels / TikTok", "9:16 Reels / TikTok / Shorts", Icons.Filled.StayCurrentPortrait, 9f, 16f),
    SQUARE_1_1("1_1", "1:1 จัตุรัส / ฟีด", "1:1 Square Feed", Icons.Filled.CropSquare, 1f, 1f),
    LANDSCAPE_16_9("16_9", "16:9 แนวนอน / ทีวี", "16:9 Landscape / TV", Icons.Filled.StayCurrentLandscape, 16f, 9f)
}

/** Time Capsule Milestone Targets */
enum class TimeCapsuleTarget(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val yearsFromNow: Int,
    val descriptionTh: String,
    val descriptionEn: String
) {
    FIRST_BIRTHDAY("1st_bday", "วันเกิด 1 ขวบ", "1st Birthday", 1, "เปิดดูในวันที่ลูกน้อยเป่าเค้กวันเกิดครั้งแรก", "Unlock on baby's 1st birthday"),
    THIRD_BIRTHDAY("3rd_bday", "วันเกิด 3 ขวบ (เข้าโรงเรียน)", "3rd Birthday", 3, "เปิดดูในวันแรกที่ลูกน้อยสะพายกระเป๋าไปโรงเรียน", "Unlock on first day of school"),
    SEVENTH_BIRTHDAY("7th_bday", "วันเกิด 7 ขวบ", "7th Birthday", 7, "เปิดดูในวัยประถมวัยเรียนรู้", "Unlock during elementary years"),
    EIGHTEENTH_BIRTHDAY("18th_bday", "วันบรรลุนิติภาวะ (18 ปี)", "18th Birthday", 18, "ของขวัญล้ำค่าในวันที่ลูกเติบโตเป็นผู้ใหญ่เต็มตัว", "The ultimate gift in adulthood"),
    GRADUATION("graduation", "วันรับปริญญา", "Graduation Day", 22, "เปิดดูความทรงจำวันแรกคลอดในวันที่ลูกประสบความสำเร็จสูงสุด", "Celebrate college graduation")
}

/** NongRak Milestone Stamp Styles */
enum class NongRakStampStyle(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val badgeColor: Color
) {
    PASTEL_CLOUD("pastel", "พาสเทลปุยเมฆ", "Pastel Cloud", Color(0xFF6C63FF)),
    GOLDEN_ROYAL("royal", "สีทองก้าวสำคัญ", "Golden Royal", Color(0xFFFFB300)),
    NEON_CANDY("candy", "แคนดี้สดใส", "Neon Candy", Color(0xFFE91E63)),
    MINIMAL_DARK("minimal", "มินิมอลโมเดิร์น", "Minimal Dark", Color(0xFF1E214D))
}

/**
 * Three groups, not eight tabs.
 *
 * Eight pills did not fit the width, so the row scrolled horizontally and half the studio stayed
 * invisible until you thought to swipe a strip of text. Grouping by what the parent is trying to
 * DO - make it look nice, put things on it, get it out - fits three full-width targets on screen
 * and keeps every tool one tap away.
 */
private enum class StudioGroup { STYLE, DECORATE, EXPORT }

/**
 * Universal Minimal NongRak Magic Video Studio:
 * Designed for every generation (parents to grandparents) with 1-tap presets,
 * animated mascot stickers, time capsule locks, AI reformat, and branded outro cards.
 */
@Composable
fun NongRakVideoStudioDialog(
    videoUri: Uri,
    title: String?,
    description: String?,
    memoryDate: String?,
    isThai: Boolean = true,
    onDismiss: () -> Unit,
    onApply: (NongRakVideoEffect, NongRakMusicPreset, Boolean) -> Unit
) {
    val context = LocalContext.current

    // Studio States
    var selectedEffect by remember { mutableStateOf(NongRakVideoEffect.STARLIGHT) }
    var selectedMusic by remember { mutableStateOf(NongRakMusicPreset.FIRST_WALTZ) }
    var selectedMascot by remember { mutableStateOf(NongRakMascotSticker.NONGRAK_GREETING) }
    var mascotPosition by remember { mutableStateOf(MascotPosition.BOTTOM_LEFT) }
    var selectedAspectRatio by remember { mutableStateOf(StudioAspectRatio.VERTICAL_9_16) }

    // Time Capsule States
    var isTimeCapsuleEnabled by remember { mutableStateOf(false) }
    var selectedCapsuleTarget by remember { mutableStateOf(TimeCapsuleTarget.FIRST_BIRTHDAY) }
    var capsuleLetter by remember { mutableStateOf("") }

    // Stamp & Tagging States
    var enableStamp by remember { mutableStateOf(true) }
    var selectedStampStyle by remember { mutableStateOf(NongRakStampStyle.PASTEL_CLOUD) }
    var customTag by remember { mutableStateOf(title ?: (if (isThai) "ก้าวสำคัญของลูกน้อย" else "Baby Milestone")) }
    var customStoryNote by remember { mutableStateOf(description ?: "") }

    // Outro & Watermark
    var showEndOutro by remember { mutableStateOf(true) }
    var isPreviewingOutro by remember { mutableStateOf(false) }

    var activeGroup by remember { mutableStateOf(StudioGroup.STYLE) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Minimal Header Bar (CapCut style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(0.12f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // Watermark status pill with official NongRak logo
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF020229),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F).copy(0.8f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_nongrak_logo),
                                contentDescription = "Logo",
                                modifier = Modifier.size(16.dp).clip(CircleShape)
                            )
                            Text(
                                if (isThai) "ลิขสิทธิ์แท้ น้องรัก AI" else "NongRak AI Certified",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Save / Apply Button
                    Button(
                        onClick = {
                            onApply(selectedEffect, selectedMusic, enableStamp)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(if (isThai) "บันทึก" else "Done", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Center Main Viewport (Aspect-Ratio Aware)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E1A)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(0.15f)),
                        modifier = Modifier
                            .aspectRatio(selectedAspectRatio.ratioWidth / selectedAspectRatio.ratioHeight)
                            .fillMaxHeight()
                            .shadow(16.dp, RoundedCornerShape(20.dp))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isPreviewingOutro) {
                                // 2-second Signature Animated Outro Preview
                                NongRakAnimatedOutroBumper(isThai = isThai)
                            } else {
                                // Video / Media Playback Surface
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(videoUri)
                                            val mc = MediaController(ctx)
                                            mc.setAnchorView(this)
                                            setMediaController(mc)
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = true
                                                start()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Real-Time Canvas Particle FX Filter
                                if (selectedEffect != NongRakVideoEffect.NONE) {
                                    VideoStudioParticleCanvas(
                                        effect = selectedEffect,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Interactive Animated Mascot Sticker
                                if (selectedMascot != NongRakMascotSticker.NONE) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp)
                                    ) {
                                        NongRakAnimatedMascotBadge(
                                            mascot = selectedMascot,
                                            modifier = Modifier.align(mascotPosition.alignment)
                                        )
                                    }
                                }

                                // Milestone Stamp Overlay (Top-Left)
                                if (enableStamp) {
                                    NongRakMilestoneStampBadge(
                                        title = customTag,
                                        subtitle = formatMemoryDate(memoryDate, isThai),
                                        style = selectedStampStyle,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(14.dp)
                                    )
                                }

                                // Time Capsule Lock Banner (if enabled)
                                if (isTimeCapsuleEnabled) {
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = Color(0xFF020229).copy(alpha = 0.9f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F)),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Filled.Lock, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(13.dp))
                                            Text(
                                                "⏳ ${selectedCapsuleTarget.titleTh}",
                                                color = Color(0xFFFFD54F),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Watermark is NOT shown in editor canvas per user design.
                                // It will be burned directly into the exported MP4 video frames with ending outro.
                            }


                        }
                    }
                }

                // CapCut-style Bottom Minimal Editing Console
                Surface(
                    color = Color(0xFF101226),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Three full-width groups. No horizontal scroll: a tool you cannot see
                        // is a tool that does not exist.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioGroupTab(
                                icon = Icons.Filled.AutoAwesome,
                                title = if (isThai) "สไตล์" else "Style",
                                selected = activeGroup == StudioGroup.STYLE,
                                modifier = Modifier.weight(1f),
                                onClick = { activeGroup = StudioGroup.STYLE; isPreviewingOutro = false }
                            )
                            StudioGroupTab(
                                icon = Icons.Filled.EmojiEmotions,
                                title = if (isThai) "ตกแต่ง" else "Decorate",
                                selected = activeGroup == StudioGroup.DECORATE,
                                modifier = Modifier.weight(1f),
                                onClick = { activeGroup = StudioGroup.DECORATE; isPreviewingOutro = false }
                            )
                            StudioGroupTab(
                                icon = Icons.Filled.IosShare,
                                title = if (isThai) "ส่งออก" else "Export",
                                selected = activeGroup == StudioGroup.EXPORT,
                                modifier = Modifier.weight(1f),
                                onClick = { activeGroup = StudioGroup.EXPORT; isPreviewingOutro = false }
                            )
                        }

                        // Capped and scrollable so the console can never grow tall enough to push
                        // the preview off screen, which is the one thing the parent is looking at.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (activeGroup) {
                                StudioGroup.STYLE -> {
                                    StudioSectionLabel(if (isThai) "พรีเซ็ตด่วน" else "Quick presets")
                                    MagicPresetsRow(
                                        onSelectCombo = { eff, mus ->
                                            selectedEffect = eff
                                            selectedMusic = mus
                                        },
                                        isThai = isThai
                                    )
                                    StudioSectionLabel(if (isThai) "เอฟเฟกต์" else "Effects")
                                    EffectsCarousel(
                                        selectedEffect = selectedEffect,
                                        onSelectEffect = { selectedEffect = it },
                                        isThai = isThai
                                    )
                                    StudioSectionLabel(if (isThai) "ดนตรี" else "Music")
                                    MusicBeatsCarousel(
                                        selectedMusic = selectedMusic,
                                        onSelectMusic = { selectedMusic = it },
                                        isThai = isThai
                                    )
                                }

                                StudioGroup.DECORATE -> {
                                    StudioSectionLabel(if (isThai) "มาสคอต" else "Mascot")
                                    MascotStickersSection(
                                        selectedMascot = selectedMascot,
                                        mascotPosition = mascotPosition,
                                        onSelectMascot = { selectedMascot = it },
                                        onSelectPosition = { mascotPosition = it },
                                        isThai = isThai
                                    )
                                    StudioSectionLabel(if (isThai) "แท็ก & ตราประทับ" else "Tags & stamp")
                                    TagsAndStampSection(
                                        tag = customTag,
                                        onTagChange = { customTag = it },
                                        storyNote = customStoryNote,
                                        onStoryNoteChange = { customStoryNote = it },
                                        enableStamp = enableStamp,
                                        onToggleStamp = { enableStamp = it },
                                        stampStyle = selectedStampStyle,
                                        onSelectStampStyle = { selectedStampStyle = it },
                                        isThai = isThai
                                    )
                                }

                                StudioGroup.EXPORT -> {
                                    StudioSectionLabel(if (isThai) "สัดส่วนภาพ" else "Aspect ratio")
                                    AspectRatioReformatSection(
                                        selectedRatio = selectedAspectRatio,
                                        onSelectRatio = { selectedAspectRatio = it },
                                        isThai = isThai
                                    )
                                    StudioSectionLabel(if (isThai) "ไตเติลปิดท้าย" else "Ending outro")
                                    OutroAndWatermarkSection(
                                        showOutro = showEndOutro,
                                        onToggleOutro = { showEndOutro = it },
                                        isPreviewing = isPreviewingOutro,
                                        onTogglePreview = { isPreviewingOutro = it },
                                        isThai = isThai
                                    )
                                    StudioSectionLabel(if (isThai) "แคปซูลเวลา" else "Time capsule")
                                    TimeCapsuleSection(
                                        isEnabled = isTimeCapsuleEnabled,
                                        onToggleEnabled = { isTimeCapsuleEnabled = it },
                                        selectedTarget = selectedCapsuleTarget,
                                        onSelectTarget = { selectedCapsuleTarget = it },
                                        letter = capsuleLetter,
                                        onLetterChange = { capsuleLetter = it },
                                        isThai = isThai
                                    )
                                }
                            }
                        }

                        // 1-Tap Direct Export Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isThai) "ส่งออก:" else "Export:",
                                color = Color.White.copy(0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            ExportDirectPill(
                                platformName = "TikTok",
                                icon = Icons.Filled.PlayArrow,
                                color = Color(0xFF00F2FE),
                                onClick = {
                                    exportMemoryToSocial(
                                        context = context,
                                        platform = "tiktok",
                                        title = customTag,
                                        description = customStoryNote,
                                        date = memoryDate,
                                        mediaUri = videoUri,
                                        mimeType = "video/mp4",
                                        isThai = isThai,
                                        aspectRatio = selectedAspectRatio.ratioWidth / selectedAspectRatio.ratioHeight,
                                        mascotResId = selectedMascot
                                            .takeIf { it != NongRakMascotSticker.NONE }?.drawableRes,
                                        mascotPosition = mascotPosition
                                    )
                                }
                            )
                            ExportDirectPill(
                                platformName = "Instagram",
                                icon = Icons.Filled.CameraAlt,
                                color = Color(0xFFE1306C),
                                onClick = {
                                    exportMemoryToSocial(
                                        context = context,
                                        platform = "instagram",
                                        title = customTag,
                                        description = customStoryNote,
                                        date = memoryDate,
                                        mediaUri = videoUri,
                                        mimeType = "video/mp4",
                                        isThai = isThai,
                                        aspectRatio = selectedAspectRatio.ratioWidth / selectedAspectRatio.ratioHeight,
                                        mascotResId = selectedMascot
                                            .takeIf { it != NongRakMascotSticker.NONE }?.drawableRes,
                                        mascotPosition = mascotPosition
                                    )
                                }
                            )
                            ExportDirectPill(
                                platformName = "Shorts",
                                icon = Icons.Filled.SmartDisplay,
                                color = Color(0xFFFF0000),
                                onClick = {
                                    exportMemoryToSocial(
                                        context = context,
                                        platform = "youtube_shorts",
                                        title = customTag,
                                        description = customStoryNote,
                                        date = memoryDate,
                                        mediaUri = videoUri,
                                        mimeType = "video/mp4",
                                        isThai = isThai,
                                        aspectRatio = selectedAspectRatio.ratioWidth / selectedAspectRatio.ratioHeight,
                                        mascotResId = selectedMascot
                                            .takeIf { it != NongRakMascotSticker.NONE }?.drawableRes,
                                        mascotPosition = mascotPosition
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Mascot Stickers Picker Section */
@Composable
private fun MascotStickersSection(
    selectedMascot: NongRakMascotSticker,
    mascotPosition: MascotPosition,
    onSelectMascot: (NongRakMascotSticker) -> Unit,
    onSelectPosition: (MascotPosition) -> Unit,
    isThai: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(NongRakMascotSticker.values()) { m ->
                val isSel = selectedMascot == m
                Surface(
                    onClick = { onSelectMascot(m) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSel) Primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) Primary else Color.White.copy(0.15f)),
                    modifier = Modifier.width(110.dp).height(85.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Image(
                            painter = painterResource(m.drawableRes),
                            contentDescription = m.nameEn,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isThai) m.nameTh else m.nameEn,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Mascot Position Selector
        if (selectedMascot != NongRakMascotSticker.NONE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (isThai) "ตำแหน่ง:" else "Position:",
                    color = Color.White.copy(0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                MascotPosition.values().forEach { pos ->
                    val isPosSel = mascotPosition == pos
                    Surface(
                        onClick = { onSelectPosition(pos) },
                        shape = RoundedCornerShape(100.dp),
                        color = if (isPosSel) Primary else Color.White.copy(0.1f),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = if (isThai) pos.titleTh else pos.titleEn,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Animated Mascot Overlay Component */
@Composable
private fun NongRakAnimatedMascotBadge(
    mascot: NongRakMascotSticker,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color(0xFF020229).copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFF5270C9)))),
        modifier = modifier
            .offset(y = bounceY.dp)
            .shadow(10.dp, RoundedCornerShape(100.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Image(
                painter = painterResource(mascot.drawableRes),
                contentDescription = mascot.nameEn,
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
            Column {
                Text(
                    text = mascot.nameTh,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "✨ ${mascot.actionText}",
                    color = Color(0xFFFFD54F),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** AI Auto-Reformat Section */
@Composable
private fun AspectRatioReformatSection(
    selectedRatio: StudioAspectRatio,
    onSelectRatio: (StudioAspectRatio) -> Unit,
    isThai: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StudioAspectRatio.values().forEach { r ->
            val isSel = selectedRatio == r
            Surface(
                onClick = { onSelectRatio(r) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSel) Primary.copy(0.3f) else Color.White.copy(0.08f),
                border = androidx.compose.foundation.BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) Primary else Color.White.copy(0.15f)),
                modifier = Modifier.weight(1f).height(80.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(6.dp)
                ) {
                    Icon(r.icon, null, tint = if (isSel) Primary else Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isThai) r.titleTh else r.titleEn,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Time Capsule Lock Section */
@Composable
private fun TimeCapsuleSection(
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    selectedTarget: TimeCapsuleTarget,
    onSelectTarget: (TimeCapsuleTarget) -> Unit,
    letter: String,
    onLetterChange: (String) -> Unit,
    isThai: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.LockClock, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp))
                Text(
                    if (isThai) "ล็อกเป็นแคปซูลเวลา (Time Capsule)" else "Lock as Time Capsule",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Color.White)
            )
        }

        if (isEnabled) {
            Text(
                if (isThai) "เลือกวันเปิดกล่องความทรงจำ:" else "Select Unlock Milestone:",
                color = Color.White.copy(0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TimeCapsuleTarget.values()) { target ->
                    val isSel = selectedTarget == target
                    Surface(
                        onClick = { onSelectTarget(target) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSel) Color(0xFFFFD54F).copy(0.3f) else Color.White.copy(0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) Color(0xFFFFD54F) else Color.White.copy(0.15f)),
                        modifier = Modifier.width(140.dp).padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isThai) target.titleTh else target.titleEn,
                                color = if (isSel) Color(0xFFFFD54F) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isThai) target.descriptionTh else target.descriptionEn,
                                color = Color.White.copy(0.7f),
                                fontSize = 9.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = letter,
                onValueChange = onLetterChange,
                label = { Text(if (isThai) "จดหมายถึงลูกในอนาคต (Message to Future Baby)" else "Message to baby", fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD54F),
                    unfocusedBorderColor = Color.White.copy(0.3f)
                ),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Tags & Custom Stamp Section */
@Composable
private fun TagsAndStampSection(
    tag: String,
    onTagChange: (String) -> Unit,
    storyNote: String,
    onStoryNoteChange: (String) -> Unit,
    enableStamp: Boolean,
    onToggleStamp: (Boolean) -> Unit,
    stampStyle: NongRakStampStyle,
    onSelectStampStyle: (NongRakStampStyle) -> Unit,
    isThai: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = tag,
            onValueChange = onTagChange,
            label = { Text(if (isThai) "ชื่อโมเมนต์ / แท็กก้าวสำคัญ" else "Milestone Tag", fontSize = 11.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Primary,
                unfocusedBorderColor = Color.White.copy(0.3f)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isThai) "แสดงตรายางก้าวสำคัญบนคลิป" else "Show Milestone Stamp",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = enableStamp,
                onCheckedChange = onToggleStamp,
                colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Color.White)
            )
        }

        if (enableStamp) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NongRakStampStyle.values()) { style ->
                    val isSel = stampStyle == style
                    Surface(
                        onClick = { onSelectStampStyle(style) },
                        shape = RoundedCornerShape(100.dp),
                        color = if (isSel) style.badgeColor else Color.White.copy(0.1f),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = if (isThai) style.titleTh else style.titleEn,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Outro & Watermark Settings Section */
@Composable
private fun OutroAndWatermarkSection(
    showOutro: Boolean,
    onToggleOutro: (Boolean) -> Unit,
    isPreviewing: Boolean,
    onTogglePreview: (Boolean) -> Unit,
    isThai: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.MovieCreation, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                Text(
                    if (isThai) "เพิ่มไตเติลปิดท้าย 2 วินาที (2s Outro Bumper)" else "2-second Outro Bumper",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = showOutro,
                onCheckedChange = onToggleOutro,
                colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Color.White)
            )
        }

        Button(
            onClick = { onTogglePreview(!isPreviewing) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.15f)),
            shape = RoundedCornerShape(100.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(if (isPreviewing) Icons.Filled.Videocam else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (isPreviewing) (if (isThai) "กลับไปที่วิดีโอ" else "Back to Video")
                else (if (isThai) "ดูตัวอย่างไตเติลปิดท้าย (Outro Preview)" else "Preview Outro Bumper"),
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

/** 2-second Signature Animated Outro Bumper */
@Composable
private fun NongRakAnimatedOutroBumper(isThai: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "outro")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF020229), Color(0xFF0A0C27), Color(0xFF1E214D))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.nongrak_mascot_greeting),
                contentDescription = "NongRak Mascot",
                modifier = Modifier
                    .size((80 * scale).dp)
                    .clip(CircleShape)
                    .shadow(16.dp, CircleShape)
            )

            Image(
                painter = painterResource(R.drawable.ic_nongrak_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(36.dp)
            )

            Text(
                text = "น้องรัก (NongRak AI)",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Text(
                text = if (isThai) "สร้างและบันทึกความทรงจำโดย น้องรัก AI" else "Preserved with Love by NongRak AI",
                color = Color(0xFFFFD54F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color.White.copy(0.12f),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFD54F).copy(0.6f))
            ) {
                Text(
                    text = "🌐 https://nongrak-ai.vercel.app/",
                    color = Color.White.copy(0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** 1-Tap AI Magic Weavers Presets */
@Composable
private fun MagicPresetsRow(
    onSelectCombo: (NongRakVideoEffect, NongRakMusicPreset) -> Unit,
    isThai: Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item {
            MagicPresetPill(
                title = if (isThai) "ละอองดาวนำโชค" else "Lucky Stars",
                effect = NongRakVideoEffect.STARLIGHT,
                music = NongRakMusicPreset.MOON_LULLABY,
                gradient = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65)),
                onClick = onSelectCombo
            )
        }
        item {
            MagicPresetPill(
                title = if (isThai) "ฟองสบู่วิเศษ" else "Dream Bubbles",
                effect = NongRakVideoEffect.BUBBLES,
                music = NongRakMusicPreset.FIRST_WALTZ,
                gradient = listOf(Color(0xFF81D4FA), Color(0xFF6C63FF)),
                onClick = onSelectCombo
            )
        }
        item {
            MagicPresetPill(
                title = if (isThai) "กลีบดอกไม้ละมุน" else "Blossom Memories",
                effect = NongRakVideoEffect.BLOSSOM,
                music = NongRakMusicPreset.GENTLE_MORNING,
                gradient = listOf(Color(0xFFF48FB1), Color(0xFFCE93D8)),
                onClick = onSelectCombo
            )
        }
        item {
            MagicPresetPill(
                title = if (isThai) "ฟิล์มละมุนตา" else "Vintage Film",
                effect = NongRakVideoEffect.VINTAGE_FILM,
                music = NongRakMusicPreset.SOFT_CLOUD,
                gradient = listOf(Color(0xFFFFCC80), Color(0xFF8D6E63)),
                onClick = onSelectCombo
            )
        }
    }
}

@Composable
private fun MagicPresetPill(
    title: String,
    effect: NongRakVideoEffect,
    music: NongRakMusicPreset,
    gradient: List<Color>,
    onClick: (NongRakVideoEffect, NongRakMusicPreset) -> Unit
) {
    Surface(
        onClick = { onClick(effect, music) },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E214D),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Brush.horizontalGradient(gradient)),
        modifier = Modifier.width(130.dp).height(65.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(effect.icon, null, tint = gradient.first(), modifier = Modifier.size(16.dp))
                Text(title, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "🎵 ${music.titleTh}",
                color = Color.White.copy(0.7f),
                fontSize = 8.5.sp,
                maxLines = 1
            )
        }
    }
}

/** FX Filters Carousel */
@Composable
private fun EffectsCarousel(
    selectedEffect: NongRakVideoEffect,
    onSelectEffect: (NongRakVideoEffect) -> Unit,
    isThai: Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(NongRakVideoEffect.values()) { eff ->
            val isSel = selectedEffect == eff
            Surface(
                onClick = { onSelectEffect(eff) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSel) eff.primaryColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) eff.primaryColor else Color.White.copy(0.15f)),
                modifier = Modifier.width(95.dp).height(65.dp)
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(eff.icon, null, tint = if (isSel) eff.primaryColor else Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isThai) eff.titleTh else eff.titleEn,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Music Beats Carousel */
@Composable
private fun MusicBeatsCarousel(
    selectedMusic: NongRakMusicPreset,
    onSelectMusic: (NongRakMusicPreset) -> Unit,
    isThai: Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(NongRakMusicPreset.values()) { mus ->
            val isSel = selectedMusic == mus
            Surface(
                onClick = { onSelectMusic(mus) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSel) Primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) Primary else Color.White.copy(0.15f)),
                modifier = Modifier.width(120.dp).height(65.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (mus == NongRakMusicPreset.ORIGINAL) Icons.Filled.VolumeOff else Icons.Filled.MusicNote, null, tint = if (isSel) Primary else Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (isThai) mus.titleTh else mus.titleEn,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (mus == NongRakMusicPreset.ORIGINAL) "Default" else "⏱ ${mus.durationText}",
                        color = Color.White.copy(0.6f),
                        fontSize = 8.5.sp
                    )
                }
            }
        }
    }
}

/** A section heading inside a group, so three stacked tools do not read as one wall. */
@Composable
private fun StudioSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

/**
 * One of the three group tabs.
 *
 * Icon above label, and it takes a weight from the caller so the three split the width evenly.
 * The old pills were text-only at 11sp and sized to their own content, which made them both hard
 * to read and hard to hit.
 */
@Composable
private fun StudioGroupTab(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Primary else Color.White.copy(0.08f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.White.copy(0.75f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = if (selected) Color.White else Color.White.copy(0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExportDirectPill(
    platformName: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.7f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Text(platformName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Milestone Stamp Badge */
@Composable
private fun NongRakMilestoneStampBadge(
    title: String,
    subtitle: String,
    style: NongRakStampStyle,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF020229).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, style.badgeColor),
        modifier = modifier.shadow(8.dp, RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(style.badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Column {
                Text(text = title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = style.badgeColor, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Prominent NongRak AI Watermark Badge (Locked at Bottom-Right) */
@Composable
fun NongRakProminentWatermark(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color(0xFF020229).copy(alpha = 0.90f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFF5270C9)))),
        modifier = modifier.shadow(8.dp, RoundedCornerShape(100.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nongrak_logo),
                contentDescription = "NongRak Logo",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "น้องรัก (NongRak AI)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                    Icon(
                        Icons.Filled.Verified,
                        null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = "Official Family Archive • ลิขสิทธิ์แท้",
                    color = Color(0xFFFFD54F),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun NongRakWatermarkBadge(
    modifier: Modifier = Modifier
) {
    NongRakProminentWatermark(modifier = modifier)
}

/** Real-Time Particle Simulation */
@Composable
private fun VideoStudioParticleCanvas(
    effect: NongRakVideoEffect,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fx_particles")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (effect) {
            NongRakVideoEffect.STARLIGHT -> {
                for (i in 0 until 16) {
                    val angle = (i * 22.5f + phase * 360f) * (Math.PI / 180f)
                    val radius = (120f + (i * 25f) % 250f) * (0.8f + 0.2f * sin(phase * 6f + i))
                    val cx = w / 2f + (radius * kotlin.math.cos(angle)).toFloat()
                    val cy = h / 2f + (radius * kotlin.math.sin(angle)).toFloat()
                    val starSize = (3f + (i % 4) * 2f)

                    drawCircle(
                        color = Color(0xFFFFD54F).copy(alpha = (0.3f + 0.5f * sin(phase * 4f + i).coerceAtLeast(0f))),
                        radius = starSize,
                        center = Offset(cx, cy)
                    )
                }
            }
            NongRakVideoEffect.BUBBLES -> {
                for (i in 0 until 12) {
                    val progress = (phase + i / 12f) % 1f
                    val y = h * (1f - progress)
                    val x = (w * 0.1f) + (i * 73f) % (w * 0.8f) + sin(progress * 10f + i) * 20f
                    val r = 8f + (i % 5) * 5f

                    drawCircle(
                        color = Color(0xFF81D4FA).copy(alpha = 0.35f * (1f - progress)),
                        radius = r,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f * (1f - progress)),
                        radius = r * 0.3f,
                        center = Offset(x - r * 0.3f, y - r * 0.3f)
                    )
                }
            }
            NongRakVideoEffect.BLOSSOM -> {
                for (i in 0 until 14) {
                    val progress = (phase + i / 14f) % 1f
                    val y = h * progress
                    val x = (w * 0.1f) + (i * 65f) % (w * 0.8f) + sin(progress * 8f + i) * 35f
                    val petalSize = 9f + (i % 3) * 4f

                    drawCircle(
                        color = Color(0xFFF48FB1).copy(alpha = 0.45f * (1f - progress * 0.5f)),
                        radius = petalSize,
                        center = Offset(x, y)
                    )
                }
            }
            else -> {}
        }
    }
}

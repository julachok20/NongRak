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
import com.lullabyai.app.ui.mascot.MascotArt
import com.lullabyai.app.ui.theme.*
import kotlin.math.sin

/**
 * Things sprinkled ON the picture.
 *
 * Split from [NongRakVideoFilter] because the design gives เอฟเฟกต์ and ฟิลเตอร์ their own tools,
 * and because they genuinely compose: a parent should be able to have a warm film tone AND falling
 * petals. While both lived in one field, choosing either silently discarded the other.
 */
enum class NongRakVideoEffect(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val icon: ImageVector,
    val primaryColor: Color
) {
    NONE("none", "ไม่ใส่", "None", Icons.Filled.Block, Color.Gray),
    STARLIGHT("starlight", "ละอองดาวนำโชค", "Starlight Sparkles", Icons.Filled.AutoAwesome, Color(0xFFFFD54F)),
    BUBBLES("bubbles", "ฟองสบู่วิเศษ", "Dream Bubbles", Icons.Filled.BlurCircular, Color(0xFF81D4FA)),
    BLOSSOM("blossom", "กลีบดอกไม้ละมุน", "Cherry Blossoms", Icons.Filled.LocalFlorist, Color(0xFFF48FB1)),
    GOLDEN_CROWN("crown", "มงกุฎก้าวสำคัญ", "Milestone Crown", Icons.Filled.EmojiEvents, Color(0xFFFFB300)),
    TOY_KINGDOM("toys", "ดินแดนของเล่น", "Toy Kingdom", Icons.Filled.SmartToy, Color(0xFFFF8A65))
}

/**
 * Colour treatments applied TO the picture.
 *
 * Every entry is a real GL colour operation, which is why none of them preview in Compose - the
 * editor says so rather than showing a swatch the export will not match.
 */
enum class NongRakVideoFilter(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val icon: ImageVector,
    val swatch: Color
) {
    NONE("none", "ต้นฉบับ", "Original", Icons.Filled.Block, Color.Gray),
    VINTAGE_FILM("vintage", "ฟิล์มละมุนตา", "Vintage Film", Icons.Filled.CameraRoll, Color(0xFFFFCC80)),
    RAINBOW_AURA("rainbow", "แสงออโรร่า", "Rainbow Aura", Icons.Filled.FilterVintage, Color(0xFFCE93D8)),
    GOLDEN_HOUR("golden", "แสงทองยามเย็น", "Golden Hour", Icons.Filled.WbTwilight, Color(0xFFFFB300)),
    SOFT_PASTEL("pastel", "พาสเทลนุ่มตา", "Soft Pastel", Icons.Filled.Gradient, Color(0xFFB39DDB))
}

// NongRakMusicPreset lived here: six named lullabies with no audio files behind them. The picker
// let a parent choose "เพลงกล่อมพระจันทร์" and nothing was ever mixed into the export. Bring it
// back together with licensed tracks in res/raw, not before.

/**
 * Which companion appears in the video - not which pose it is frozen in.
 *
 * This used to be eight static PNGs ("น้องรัก โบกมือ", "น้องรัก ปรบมือ" and so on), each a flat
 * picture pasted onto the frame. The app already owns a rigged mascot that breathes, tilts and
 * swings an arm through [MascotArt] and the skinned mesh, so the only choice worth offering is
 * *who* - the movement then comes for free, and it is the same character the parent already knows
 * from the chat screen.
 *
 * [drawableRes] survives only as the picker thumbnail; the video uses the rig.
 */
enum class NongRakMascotSticker(
    val id: String,
    val nameTh: String,
    val nameEn: String,
    /** Null for [NONE] - there is no companion to animate. */
    val art: MascotArt?
) {
    NONE("none", "ไม่ใส่มาสคอต", "No Mascot", null),
    NONGRAK("nongrak", "น้องรัก", "NongRak", MascotArt.NONGRAK),
    PINGU("pingu", "พังกุ", "Pingu", MascotArt.PINGU),
    BUNNY("bunny", "กระต่ายน้อย", "Bunny", MascotArt.BUNNY),
    COCO("coco", "โคโค่", "Coco", MascotArt.COCO);

    val drawableRes: Int get() = art?.art ?: R.drawable.ic_nongrak_logo
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

// TimeCapsuleTarget lived here. The studio collected a target birthday and a letter, stored
// neither, and had no unlock mechanism anywhere in the app or the database. It is a diary feature
// that needs a table and a scheduled unlock, not an editor control.

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

// NongRakAnimatedMascotBadge lived here: a static PNG with a bounce, pasted over the
// preview. The editor now shows the real rigged companion instead, so a second, simpler
// mascot renderer would only be something to keep in sync.

/** 2-second Signature Animated Outro Bumper */
@Composable
internal fun NongRakAnimatedOutroBumper(isThai: Boolean) {
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


/** Milestone Stamp Badge */
@Composable
internal fun NongRakMilestoneStampBadge(
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

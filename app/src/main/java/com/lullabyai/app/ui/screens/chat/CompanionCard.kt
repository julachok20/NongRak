package com.lullabyai.app.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class ChatColors(
    val surface: Color,
    val cardBg: Color,
    val cardBgElevated: Color,
    val topBarBg: Color,
    val bottomBarBg: Color,
    val hairline: Color,
    val border: Color,
    val primary: Color,
    val primaryLight: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val aiBubble: Color,
    val aiBubbleText: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val isDark: Boolean
)

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(
        surface = Color(0xFFF8F9FF),
        cardBg = Color.White,
        cardBgElevated = Color.White,
        topBarBg = Color.White,
        bottomBarBg = Color.White,
        hairline = Color(0xFFF3F4F6),
        border = Color(0xFFE5E7EB),
        primary = Color(0xFF3858D1),
        primaryLight = Color(0xFFEFF4FF),
        ink = Color(0xFF1F2937),
        inkSoft = Color(0xFF4B5563),
        inkFaint = Color(0xFF9CA3AF),
        userBubble = Color(0xFF3858D1),
        userBubbleText = Color.White,
        aiBubble = Color.White,
        aiBubbleText = Color(0xFF4B5563),
        inputBg = Color(0xFFF8F9FF),
        inputBorder = Color(0xFFE5E7EB),
        isDark = false
    )
}

@Composable
fun rememberChatColors(isDark: Boolean = com.lullabyai.app.ui.theme.LocalDarkTheme.current): ChatColors {
    return remember(isDark) {
        if (isDark) {
            ChatColors(
                surface = Color(0xFF12141C),
                cardBg = Color(0xFF1C202B),
                cardBgElevated = Color(0xFF242938),
                topBarBg = Color(0xFF171A23),
                bottomBarBg = Color(0xFF171A23),
                hairline = Color(0xFF2A303F),
                border = Color(0xFF343B4E),
                primary = Color(0xFF7B96FF),
                primaryLight = Color(0xFF1E2845),
                ink = Color(0xFFF3F5FA),
                inkSoft = Color(0xFFCBD1DE),
                inkFaint = Color(0xFF8F96A8),
                userBubble = Color(0xFF3858D1),
                userBubbleText = Color.White,
                aiBubble = Color(0xFF1C202B),
                aiBubbleText = Color(0xFFE5E8F2),
                inputBg = Color(0xFF161922),
                inputBorder = Color(0xFF2E3546),
                isDark = true
            )
        } else {
            ChatColors(
                surface = Color(0xFFF8F9FF),
                cardBg = Color.White,
                cardBgElevated = Color.White,
                topBarBg = Color.White,
                bottomBarBg = Color.White,
                hairline = Color(0xFFF3F4F6),
                border = Color(0xFFE5E7EB),
                primary = Color(0xFF3858D1),
                primaryLight = Color(0xFFEFF4FF),
                ink = Color(0xFF1F2937),
                inkSoft = Color(0xFF4B5563),
                inkFaint = Color(0xFF9CA3AF),
                userBubble = Color(0xFF3858D1),
                userBubbleText = Color.White,
                aiBubble = Color.White,
                aiBubbleText = Color(0xFF4B5563),
                inputBg = Color(0xFFF8F9FF),
                inputBorder = Color(0xFFE5E7EB),
                isDark = false
            )
        }
    }
}

// Backward-compatible fallback references
internal val StitchPrimary = Color(0xFF3858D1)
internal val StitchPrimaryLight = Color(0xFFEFF4FF)
internal val StitchSurface = Color(0xFFF8F9FF)
internal val StitchInk = Color(0xFF1F2937)
internal val StitchInkSoft = Color(0xFF4B5563)
internal val StitchInkFaint = Color(0xFF9CA3AF)
internal val StitchHairline = Color(0xFFF3F4F6)

/**
 * The tamagotchi companion: a floating mascot, three care buttons, a bonding meter, and a
 * status pill that says the one thing worth saying right now.
 *
 * [statusText] comes from ProactiveBriefUseCase — "อีก 30 นาที ถึงเวลากินยา" when something is
 * pending, "สวัสดีค่ะ" when nothing is. [emotion] picks the pose to match.
 */
@Composable
fun CompanionCard(
    mascot: MascotId,
    emotion: CompanionEmotion,
    isSpeaking: Boolean,
    statusText: String,
    bondingLevel: Int,
    bondingPercent: Int,
    onPlay: () -> Unit,
    onFood: () -> Unit,
    onSleep: () -> Unit,
    modifier: Modifier = Modifier,
    onPet: () -> Unit = {},
    garments: List<Int> = emptyList()
) {
    val chatColors = LocalChatColors.current
    val transition = rememberInfiniteTransition(label = "companion")
    // Stitch: @keyframes pulse-soft — the tint behind the mascot breathes over 3s.
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = modifier.widthIn(max = 280.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (chatColors.isDark) 0.dp else 10.dp, RoundedCornerShape(40.dp), spotColor = chatColors.primary.copy(0.18f))
                .clip(RoundedCornerShape(40.dp))
                .background(chatColors.cardBg)
                .border(if (chatColors.isDark) 1.dp else 0.dp, chatColors.border, RoundedCornerShape(40.dp))
                .padding(16.dp)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(listOf(chatColors.primaryLight, chatColors.cardBg)))
                        .border(4.dp, chatColors.cardBg, RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .alpha(pulse)
                            .background(chatColors.primary.copy(0.05f))
                    )
                    // น้องรัก is the only mascot with measured mouth coordinates, so it is the only
                    // one that can wear the painted talking face while TTS speaks (see AiAvatar.kt).
                    // Every other moment — and every other mascot — is the live pet: breathing,
                    // drifting, swaying, blinking, and hopping when touched.
                    // All four companions are skeletally animated now: a limb swings, the scarf or
                    // cape trails a beat behind the body, and the face blinks and talks on top.
                    // Face coordinates were measured per mascot, so this is no longer น้องรัก-only.
                    com.lullabyai.app.ui.mascot.Mascot(
                        art = mascot.rigArt(),
                        mood = emotion.toMood(isSpeaking),
                        speaking = isSpeaking,
                        garments = garments,
                        onTap = onPet,
                        modifier = Modifier.size(132.dp)
                    )
                }

                // Care buttons, overlaid top-left of the portrait exactly as in the Stitch layout.
                Column(
                    modifier = Modifier.align(Alignment.TopStart).offset(x = (-18).dp, y = (-6).dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CareButton(Icons.Filled.SportsEsports, "เล่นกับน้อง", Color(0xFF3B82F6), Color(0xFFDBEAFE), onPlay)
                    CareButton(Icons.Filled.Restaurant, "บันทึกอาหาร", Color(0xFFF97316), Color(0xFFFFEDD5), onFood)
                    CareButton(Icons.Filled.Bedtime, "บันทึกการนอน", Color(0xFF6366F1), Color(0xFFE0E7FF), onSleep)
                }
            }

            Spacer(Modifier.height(14.dp))

            BondingMeter(level = bondingLevel, percent = bondingPercent)
        }

        // Status pill straddles the bottom edge of the card (Stitch: -bottom-3).
        StatusPill(text = statusText, modifier = Modifier.offset(y = 14.dp))
    }
}

@Composable
private fun CareButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    border: Color,
    onClick: () -> Unit
) {
    val chatColors = LocalChatColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(if (chatColors.isDark) 0.dp else 3.dp, CircleShape)
            .clip(CircleShape)
            .background(chatColors.cardBg)
            .border(1.dp, if (chatColors.isDark) chatColors.border else border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BondingMeter(level: Int, percent: Int) {
    val chatColors = LocalChatColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (chatColors.isDark) Color(0xFF3B1E2B) else Color(0xFFFDF2F8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEC4899), modifier = Modifier.size(14.dp))
                }
                Text("ความผูกพัน", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = chatColors.inkSoft)
            }
            Spacer(Modifier.weight(1f))
            Text("Lv.", fontSize = 12.sp, color = chatColors.inkFaint, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(3.dp))
            Text("$level", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = chatColors.primary)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(chatColors.hairline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(chatColors.primary, Color(0xFF60A5FA))))
            )
        }
        Text(
            "รู้ใจน้องไปแล้ว $percent%",
            fontSize = 10.sp,
            color = chatColors.inkFaint,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    val chatColors = LocalChatColors.current
    val transition = rememberInfiniteTransition(label = "ping")
    val pingScale by transition.animateFloat(
        initialValue = 1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "pingScale"
    )
    val pingAlpha by transition.animateFloat(
        initialValue = 0.75f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "pingAlpha"
    )

    Row(
        modifier = modifier
            .shadow(if (chatColors.isDark) 0.dp else 4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(chatColors.cardBg)
            .border(1.dp, chatColors.border, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(10.dp).scale(pingScale).alpha(pingAlpha)
                    .background(Color(0xFF4ADE80), CircleShape)
            )
            Box(Modifier.size(10.dp).background(Color(0xFF22C55E), CircleShape))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = chatColors.inkSoft, maxLines = 1)
    }
}

/**
 * The companion once a conversation is running: one tidy strip instead of the full card.
 *
 * Keeps what the parent asked to always see — the pet itself (still tappable, still alive) and
 * ความผูกพัน — and keeps โทรคุย reachable, while giving the chat back most of the screen.
 */
@Composable
fun CompanionStrip(
    mascot: MascotId,
    emotion: CompanionEmotion,
    isSpeaking: Boolean,
    statusText: String,
    bondingLevel: Int,
    bondingPercent: Int,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
    garments: List<Int> = emptyList()
) {
    val chatColors = LocalChatColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (chatColors.isDark) 0.dp else 4.dp, RoundedCornerShape(24.dp), spotColor = chatColors.primary.copy(0.15f))
            .clip(RoundedCornerShape(24.dp))
            .background(chatColors.cardBg)
            .border(if (chatColors.isDark) 1.dp else 0.dp, chatColors.border, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(chatColors.primaryLight, chatColors.cardBg)))
        ) {
            // The minimised bar is the same living companion as the big card, not a thumbnail of
            // it — same rig, same moods, so greeting still reads as a greeting down here.
            com.lullabyai.app.ui.mascot.Mascot(
                art = mascot.rigArt(),
                mood = emotion.toMood(isSpeaking),
                speaking = isSpeaking,
                garments = garments,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = chatColors.ink, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(chatColors.hairline)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(bondingPercent.coerceIn(0, 100) / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(chatColors.primary, Color(0xFF60A5FA))))
                    )
                }
                Text("Lv.$bondingLevel", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chatColors.primary)
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(chatColors.primary)
                .clickable(onClick = onCall),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Call, "โทรคุย", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** The two prominent calls to action under the companion card: storytelling mode and voice call. */
@Composable
fun CompanionCtaRow(
    storyActive: Boolean,
    onStory: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatColors = LocalChatColors.current
    Row(modifier = modifier.widthIn(max = 280.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GradientCta(
            gradient = listOf(Color(0xFFA855F7), Color(0xFF4F46E5)),
            iconBg = if (chatColors.isDark) Color(0xFF2E1C4E) else Color(0xFFF5F3FF),
            iconTint = if (chatColors.isDark) Color(0xFFC084FC) else Color(0xFF9333EA),
            icon = Icons.Filled.MenuBook,
            label = "โหมดเล่านิทาน",
            // The gradient border is the affordance; a filled interior marks it as the active mode.
            active = storyActive,
            onClick = onStory,
            modifier = Modifier.weight(1f)
        )
        GradientCta(
            gradient = listOf(chatColors.primary, Color(0xFF2563EB)),
            iconBg = chatColors.primaryLight,
            iconTint = chatColors.primary,
            icon = Icons.Filled.Call,
            label = "โทรคุย",
            active = false,
            onClick = onCall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GradientCta(
    gradient: List<Color>,
    iconBg: Color,
    iconTint: Color,
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatColors = LocalChatColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(gradient))
            .padding(2.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) iconBg else chatColors.cardBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = chatColors.ink, maxLines = 1)
        }
    }
}

/** Pill under an AI reply that sends a canned follow-up when tapped. */
@Composable
fun SuggestionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val chatColors = LocalChatColors.current
    Row(
        modifier = Modifier
            .shadow(if (chatColors.isDark) 0.dp else 1.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(chatColors.cardBg)
            .border(1.dp, chatColors.border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = chatColors.inkSoft, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = chatColors.inkSoft)
    }
}

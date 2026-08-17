package com.lullabyai.app.ui.screens.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.R
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

/**
 * Stitch: "Pro Health-Tech Welcome - Clinical Variant".
 *
 * The first screen anyone sees, so it has one job: say what this is and get a parent to sign up.
 * The clinical framing — trust badge, HIPAA/AES/ISO row, a separate doctor entrance — is the
 * design saying "this is a medical tool, not a toy" before a word of copy is read.
 *
 * ponytail: the new copy is inline bilingual rather than added to LocalStrings — it appears
 * nowhere else, and that strings file is shared by ~90 screens.
 */
@Composable
fun WelcomeScreen(
    onSignUp: () -> Unit,
    onLogin: () -> Unit,
    settingsViewModel: SettingsViewModel,
    // Default no-op keeps this screen wireable today — AppNavigation.kt passes the real
    // navigation to Screen.DoctorWelcome.route once the doctor flow is hooked up centrally.
    onDoctorMode: () -> Unit = {}
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val settings by settingsViewModel.settings.collectAsState()

    // Staggered reveal: logo first, then the headline, then the CTAs — reads as a small intro
    // rather than everything landing at once.
    var showLogo by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showLogo = true
        delay(400)
        showText = true
        delay(250)
        showButtons = true
    }

    val pulse = rememberInfiniteTransition(label = "welcomePulse")
    val glowScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2600, easing = EaseOutCubic), RepeatMode.Reverse),
        label = "glowScale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE8EEFF), Color(0xFFF6F8FF), Color.White)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Primary))
                Spacer(Modifier.width(10.dp))
                Text(
                    "SMART PARENTING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = Primary
                )
                Spacer(Modifier.weight(1f))
                LanguagePill(
                    isThai = settings.language == AppLanguage.TH,
                    onPick = { wantThai ->
                        // toggleLanguage() flips; only call it when the tapped side differs, or
                        // tapping the active half would switch away from itself.
                        if (wantThai != (settings.language == AppLanguage.TH)) {
                            settingsViewModel.toggleLanguage()
                        }
                    }
                )
            }

            Spacer(Modifier.height(28.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(260.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Primary.copy(alpha = 0.14f), Color.Transparent)
                            )
                        )
                )
                // Fully qualified: inside BoxScope the ColumnScope overload is not applicable,
                // and Kotlin will not fall back to the top-level one on its own.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLogo,
                    enter = fadeIn(tween(700)) +
                        scaleIn(tween(700, easing = EaseOutCubic), initialScale = 0.7f)
                ) {
                    Image(
                        painter = painterResource(R.drawable.nongrak_logo_modern),
                        contentDescription = "NongRak AI",
                        modifier = Modifier.size(230.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome, null,
                            tint = Primary, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isThai) "ผู้ช่วยอัจฉริยะคู่ใจคุณพ่อคุณแม่"
                            else "Your smart parenting companion",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        if (isThai) "ผู้ช่วย AI ดูแลและบันทึก\nทุกช่วงเวลาของลูกน้อย"
                        else "AI companion to care for\nyour child's journey",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        if (isThai) "บันทึกพัฒนาการ สุขภาพ และการนอน พร้อมคำแนะนำที่อบอุ่นและเข้าใจง่าย"
                        else "Track growth, daily routines, and milestones with warm, thoughtful guidance",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = showButtons,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 }
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onSignUp,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                    ) {
                        Text(
                            if (isThai) "เริ่มต้นใช้งาน" else "Get started",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.ArrowForward, null,
                            tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (isThai) "เข้าสู่ระบบ" else "Sign in",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = Outline.copy(0.35f))
                        Text(
                            if (isThai) "หรือ" else "or",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        HorizontalDivider(Modifier.weight(1f), color = Outline.copy(0.35f))
                    }

                    Spacer(Modifier.height(22.dp))

                    // Doctors get their own entrance rather than a buried text link — a separate
                    // audience with a separate account type, not a footnote.
                    Surface(
                        onClick = onDoctorMode,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(Primary.copy(0.10f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.MedicalServices, null,
                                    tint = Primary, modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isThai) "เข้าสู่ระบบสำหรับบุคลากร & ผู้เชี่ยวชาญ" else "Sign in as a clinician / specialist",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                                Text(
                                    "Doctor & Provider Portal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TrustMark(Icons.Filled.Shield, "PRIVACY FIRST")
                TrustMark(Icons.Filled.Lock, "ENCRYPTED")
                TrustMark(Icons.Filled.ChildCare, "WHO STANDARDS")
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** The TH | EN segmented control from the design — one pill, the active half filled. */
@Composable
private fun LanguagePill(isThai: Boolean, onPick: (Boolean) -> Unit) {
    Row(
        Modifier
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(4.dp)
    ) {
        listOf(true, false).forEach { thai ->
            val active = thai == isThai
            Box(
                Modifier
                    .background(
                        if (active) Primary else Color.Transparent,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onPick(thai) }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    if (thai) "TH" else "EN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Compliance marks. Deliberately quiet — reassurance, not decoration. */
@Composable
private fun TrustMark(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Outline, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = Outline
        )
    }
}

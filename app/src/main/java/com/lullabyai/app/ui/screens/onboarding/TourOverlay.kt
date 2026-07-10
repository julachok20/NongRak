package com.lullabyai.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

private enum class TourStop { HOME, SIDEBARS, CHILD_INFO, CHATBOT, QA, LANGUAGE }
private enum class CardAnchor { START, CENTER, END }

/** [arrowRotationDeg] points the arrow icon toward the real bottom-nav icon this step refers to —
 *  0°=right, 90°=down, 135°=down-left, 225°=down-right (screen coords, clockwise from east).
 *  [cardBottomPadding] and [cardAnchor] move the card to a different spot each step — closer to the
 *  bar and centered for the "whole bar" step, off to whichever side the specific icon sits on for
 *  the rest — instead of parking in one fixed place for the whole tour. */
private data class TourStepInfo(
    val stop: TourStop, val route: String, val color: Color,
    val cardAnchor: CardAnchor, val cardWidthFraction: Float, val cardBottomPadding: androidx.compose.ui.unit.Dp, val arrowRotationDeg: Float,
    val targetTh: String, val targetEn: String,
    val titleTh: String, val titleEn: String, val bodyTh: String, val bodyEn: String
)

private val tourSteps = listOf(
    TourStepInfo(
        TourStop.HOME, Screen.Dashboard.route, Primary, CardAnchor.END, 0.8f, 150.dp, 135f,
        "ไอคอน 'หน้าหลัก' มุมล่างซ้าย", "the 'Home' icon, bottom-left",
        "หน้าหลัก", "Home",
        "นี่คือหน้าหลักของคุณ ภาพรวมของลูกน้อย บริการทั้งหมด และคำแนะนำจาก AI อยู่ที่นี่",
        "This is your Home page — your child's overview, every service, and AI advice all live here."
    ),
    TourStepInfo(
        TourStop.CHILD_INFO, Screen.ChildSummary.route, Color(0xFF3949AB), CardAnchor.START, 0.78f, 100.dp, 90f,
        "ไอคอน 'ข้อมูลเด็ก' ใต้การ์ดนี้", "the 'Child Info' icon just below this card",
        "ข้อมูลของเด็ก", "Child Info",
        "หน้านี้แสดงโปรไฟล์ การเจริญเติบโต วัคซีน และประวัติสุขภาพของลูก ถ้ายังไม่ได้เพิ่มข้อมูลลูก หน้านี้จะแนะนำให้เพิ่มก่อน",
        "This page shows your child's profile, growth, vaccines, and health history. If you haven't added a child yet, this page will prompt you to."
    ),
    TourStepInfo(
        TourStop.CHATBOT, Screen.AiChat.route, Primary, CardAnchor.CENTER, 0.8f, 128.dp, 90f,
        "ปุ่มแชทลอยตรงกลางด้านล่าง", "the floating chat button, center-bottom",
        "แชท AI", "AI Chat",
        "ถามอะไรก็ได้เกี่ยวกับลูกน้อย น้องรักเข้าใจและช่วยบันทึกข้อมูลให้คุณได้เลย",
        "Ask anything about your child — NongRak understands and can log things for you."
    ),
    TourStepInfo(
        TourStop.SIDEBARS, Screen.ChildSummary.route, Color(0xFF00897B), CardAnchor.START, 0.82f, 110.dp, 270f,
        "ไอคอน ☰ มุมบนซ้ายของหน้าข้อมูลเด็ก", "the ☰ icon, top-left of the Child Info pages",
        "เมนูด้านข้าง", "Side menu",
        "แตะไอคอน ☰ ที่มุมบนซ้ายของหน้าข้อมูลเด็ก เพื่อเปิดเมนูด้านข้าง สลับไปดูภาพรวม พัฒนาการ สุขภาพ สมุดวัคซีน ความเสี่ยงโรค โภชนาการ ออกกำลังกาย และการนอนได้จากที่เดียว",
        "Tap the ☰ icon at the top-left of any Child Info page to open the side menu — switch between Overview, Development, Health, Vaccine Book, Disease Risk, Nutrition, Exercise, and Sleep, all from one place."
    ),
    TourStepInfo(
        TourStop.QA, Screen.HelpFaq.route, Color(0xFF00897B), CardAnchor.END, 0.78f, 100.dp, 90f,
        "ไอคอน 'ตั้งค่า' ใต้การ์ดนี้ (คำถามที่พบบ่อยอยู่ในนั้น)", "the 'Settings' icon just below this card (Help & FAQ is inside)",
        "คำถามที่พบบ่อย", "Help & FAQ",
        "มีคำถาม? ดูคำตอบและวิธีใช้งานแอปได้ที่นี่ทุกเมื่อ",
        "Have questions? Find answers and how-tos here anytime."
    ),
    TourStepInfo(
        TourStop.LANGUAGE, Screen.Settings.route, Color(0xFF6A1B9A), CardAnchor.END, 0.78f, 100.dp, 90f,
        "ไอคอน 'ตั้งค่า' ใต้การ์ดนี้ แล้วเลื่อนไปที่ 'ภาษา'", "the 'Settings' icon just below this card, then scroll to 'Language'",
        "เปลี่ยนภาษา", "Language",
        "เปลี่ยนภาษาของแอปได้ที่แถว 'ภาษา' ในหน้าตั้งค่า (เลื่อนลงมาที่หมวดการตั้งค่า)",
        "Switch the app's language anytime from the 'Language' row in Settings (scroll to the Preferences section)."
    )
)

/**
 * Guided tour overlay drawn ON TOP of the real NavHost (see AppNavigation.kt) — navigates the real
 * app through Home → Child Info → AI Chat → Side menu → Help & FAQ → Language, dims the real screen
 * underneath (visible but non-interactive) rather than showing static mockups. The guide card sits
 * to one side of the screen each step (not full-width bottom-center) with an arrow rotated toward
 * the real UI element it's talking about, and names that element explicitly.
 *
 * "Side menu" here means the ☰ drawer on the ข้อมูลลูก (Child Info) pages specifically — opened via
 * ChildScreenScaffold's hamburger icon, listing Overview/Development/Health/Vaccine/Risk/Nutrition/
 * Exercise/Sleep — NOT the app-wide bottom AppBottomNav (that one's already visible, dimmed, behind
 * every step, so it doesn't need its own dedicated stop). It's placed after Home/Child Info/Chat
 * rather than right after Home since it's a Child-Info-page feature.
 *
 * Child Info always navigates to the real page, even for a childless account — if there's no child
 * yet, that page's own real empty state ("please add a child") shows, which is the actual, honest
 * behavior of that page rather than a fabricated preview.
 */
@Composable
fun TourOverlay(
    step: Int,
    navController: NavController,
    isThai: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val info = tourSteps[step]
    val isLast = step == tourSteps.lastIndex

    LaunchedEffect(step) {
        // popUpTo Dashboard (not just launchSingleTop) so each step REPLACES the previous tour
        // destination instead of piling up a Dashboard→Menu→ChildInfo→Chat→FAQ→Settings backstack.
        navController.navigate(info.route) {
            popUpTo(Screen.Dashboard.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    // System back during the tour steps backward through the tour instead of the real backstack.
    BackHandler(enabled = true) { if (step > 0) onBack() else onSkip() }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { } } // swallow taps to the real (dimmed) screen underneath
    ) {
        Box(
            Modifier.align(
                when (info.cardAnchor) {
                    CardAnchor.START -> Alignment.BottomStart
                    CardAnchor.CENTER -> Alignment.BottomCenter
                    CardAnchor.END -> Alignment.BottomEnd
                }
            )
                .fillMaxWidth(info.cardWidthFraction)
                .padding(horizontal = 16.dp)
                .padding(bottom = info.cardBottomPadding)
        ) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${step + 1}/${tourSteps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        if (!isLast) TextButton(onClick = onSkip) { Text(if (isThai) "ข้าม" else "Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text(if (isThai) info.titleTh else info.titleEn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = info.color)
                    Text(if (isThai) info.bodyTh else info.bodyEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

                    Surface(shape = RoundedCornerShape(12.dp), color = info.color.copy(0.08f)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowRightAlt, null, tint = info.color,
                                modifier = Modifier.graphicsLayer { rotationZ = info.arrowRotationDeg }
                            )
                            Text(
                                (if (isThai) "แตะ" else "Tap ") + (if (isThai) info.targetTh else info.targetEn),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (step > 0) {
                            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                                Text(if (isThai) "ย้อนกลับ" else "Back")
                            }
                        }
                        Button(
                            onClick = { if (isLast) onFinish() else onNext() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = info.color)
                        ) {
                            Text(if (isLast) (if (isThai) "เสร็จสิ้น" else "Finish") else (if (isThai) "ถัดไป" else "Next"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

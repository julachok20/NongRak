package com.lullabyai.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

private enum class TourStop { WELCOME, CHILD_SWITCH, EXPORT_GROWTH, SIDEBARS, VACCINE, DISEASE_RISK, STATS, SETTINGS }
private enum class CardAnchor { START, CENTER, END }

/** [arrowRotationDeg]/[targetTh]/[targetEn] point at a real, always-visible piece of chrome (a
 *  bottom-nav icon, the FAB) — null when the step is describing in-page content instead (e.g. a
 *  banner you'd have to scroll to see), where an arrow would point at nothing.
 *  [targetKey] instead draws a precise spotlight cutout at that element's EXACT position, read from
 *  [TourTargetRegistry] — used for the ☰ sidebar icon, which the overlay has no direct layout access
 *  to since it's drawn as a separate sibling on top of the real screen, not inside it.
 *  [samplePreview] renders a small illustrative mockup inside the tour card itself, for steps whose
 *  real page may be empty this early (a brand-new, still-childless account) — so the walkthrough
 *  still shows what the feature looks like regardless of the account's actual data. */
private data class TourStepInfo(
    val stop: TourStop, val route: String, val color: Color,
    val cardAnchor: CardAnchor, val cardWidthFraction: Float, val cardBottomPadding: androidx.compose.ui.unit.Dp,
    val arrowRotationDeg: Float? = null, val targetTh: String? = null, val targetEn: String? = null,
    val targetKey: String? = null, val samplePreview: (@Composable (Boolean) -> Unit)? = null,
    val titleTh: String, val titleEn: String, val bodyTh: String, val bodyEn: String
)

private val tourSteps = listOf(
    TourStepInfo(
        TourStop.WELCOME, Screen.Dashboard.route, Primary, CardAnchor.START, 0.85f, 110.dp,
        arrowRotationDeg = 225f, targetTh = "ปุ่ม ⚡ บันทึกด่วน มุมล่างขวา", targetEn = "the ⚡ Quick Action button, bottom-right",
        titleTh = "ยินดีต้อนรับสู่ NongRak AI", titleEn = "Welcome to NongRak AI",
        bodyTh = "ยินดีต้อนรับค่ะ! นี่คือหน้าหลักของคุณ กดปุ่ม ⚡ 'บันทึกด่วน' มุมล่างขวาเพื่อเข้าถึงฟีเจอร์บันทึกข้อมูลอย่างรวดเร็ว และทุกเดือนแอปจะชวนคุณทำ 'ประเมินสุขภาพจิตสำหรับผู้ปกครอง' เพื่อดูแลใจคุณไปพร้อมกับดูแลลูกน้อย",
        bodyEn = "Welcome! This is your Home page. Tap the ⚡ Quick Action button in the bottom-right for fast logging — and every month the app invites you to a short Parent Mental Health Check-in, because caring for your child starts with caring for you too."
    ),
    TourStepInfo(
        TourStop.CHILD_SWITCH, Screen.ChildSummary.route, Color(0xFF3949AB), CardAnchor.CENTER, 0.85f, 110.dp,
        samplePreview = { isThai -> ChildSwitchSamplePreview(isThai) },
        titleTh = "สลับ/ซิงค์ข้อมูลลูก (Beta)", titleEn = "Switch & Sync Child (Beta)",
        bodyTh = "แตะที่ชื่อลูกด้านบนของหน้านี้เพื่อสลับดูข้อมูลลูกคนอื่น หรือซิงค์ข้อมูลลูกกับผู้ปกครองอีกท่านในบัญชีอื่น — ฟีเจอร์นี้ยังอยู่ในช่วงทดลอง (Beta) และอาจมีการปรับปรุงเพิ่มเติม",
        bodyEn = "Tap your child's name at the top of this page to switch between children, or sync a child's data with another parent's account. This feature is still in Beta and may keep improving."
    ),
    TourStepInfo(
        TourStop.EXPORT_GROWTH, Screen.ChildSummary.route, Color(0xFF3949AB), CardAnchor.CENTER, 0.85f, 110.dp,
        samplePreview = { isThai -> GrowthExportSamplePreview(isThai) },
        titleTh = "บันทึกการเจริญเติบโต และส่งออก PDF", titleEn = "Growth Records & PDF Export",
        bodyTh = "เลื่อนลงเพื่อดูแบนเนอร์ 'อัปเดตน้ำหนักและส่วนสูง' สำหรับบันทึกการเจริญเติบโตของลูก และ 'ส่งออกสรุป (PDF)' เพื่อสร้างรายงานสุขภาพส่งให้คุณหมอได้ทันที",
        bodyEn = "Scroll down for the 'Update weight & height' banner to log growth records, and 'Export summary (PDF)' to generate a doctor-ready health report."
    ),
    TourStepInfo(
        TourStop.SIDEBARS, Screen.ChildSummary.route, Color(0xFF00897B), CardAnchor.CENTER, 0.85f, 110.dp,
        titleTh = "จุดเข้าถึงหลัก", titleEn = "Main destinations",
        bodyTh = "เลื่อนลงเพื่อดูปุ่มหลัก 4 ปุ่ม — บันทึกสุขภาพ, ดูสถิติ, ความเสี่ยงโรค และส่งออก PDF — ใช้เข้าถึงทุกหน้าของลูกได้จากที่เดียว",
        bodyEn = "Scroll down for the 4 main buttons — Health Log, Stats, Disease Risk, and PDF Export — your one place to reach every page for your child."
    ),
    TourStepInfo(
        TourStop.VACCINE, Screen.VaccineBook.route, Color(0xFF3F51B5), CardAnchor.CENTER, 0.85f, 110.dp,
        titleTh = "สมุดวัคซีน", titleEn = "Vaccine Book",
        bodyTh = "บันทึกวัคซีนที่ลูกได้รับ ดูกำหนดการฉีดที่กำลังจะถึง และติดตามประวัติวัคซีนทั้งหมดของลูกได้ที่หน้านี้",
        bodyEn = "Record vaccines your child has received, track upcoming doses, and review their full vaccination history here."
    ),
    TourStepInfo(
        TourStop.DISEASE_RISK, Screen.DiseaseRisk.route, Color(0xFFB71C1C), CardAnchor.CENTER, 0.85f, 110.dp,
        titleTh = "ความเสี่ยงโรค (AI)", titleEn = "Disease Risk (AI)",
        bodyTh = "AI จะช่วยประเมินความเสี่ยงเบื้องต้นจากอาการที่คุณระบุ แต่ผลลัพธ์นี้ไม่สามารถทดแทนการวินิจฉัยของแพทย์ได้ และ AI อาจมีข้อผิดพลาดได้ — โปรดปรึกษาแพทย์เสมอหากมีอาการรุนแรงหรือไม่แน่ใจ",
        bodyEn = "AI gives a preliminary risk read based on symptoms you describe — but it does not replace a doctor's diagnosis, and AI can make mistakes. Always consult a real doctor for serious or uncertain symptoms."
    ),
    TourStepInfo(
        TourStop.STATS, Screen.StatisticsInsights.route, Color(0xFF6A1B9A), CardAnchor.CENTER, 0.85f, 110.dp,
        titleTh = "สถิติ", titleEn = "Statistics",
        bodyTh = "ดูสถิติและแนวโน้มทั้งหมดของลูกน้อยได้ในที่เดียว ทั้งการเจริญเติบโต โภชนาการ การนอน และสุขภาพ",
        bodyEn = "See all your child's stats and trends in one place — growth, nutrition, sleep, and health."
    ),
    TourStepInfo(
        TourStop.SETTINGS, Screen.Settings.route, Color(0xFF6A1B9A), CardAnchor.CENTER, 0.85f, 110.dp,
        titleTh = "ตั้งค่า", titleEn = "Settings",
        bodyTh = "ปรับแต่งการตั้งค่าแอปได้ที่นี่ โดยเฉพาะการสลับภาษา (ไทย/อังกฤษ) ได้ทุกเมื่อ — กด 'เสร็จสิ้น' เพื่อกลับไปที่หน้าหลัก",
        bodyEn = "Customize app settings here — especially switching the language (Thai/English) anytime. Tap 'Finish' to return to the Home page."
    )
)

@Composable
private fun ChildSwitchSamplePreview(isThai: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF3949AB).copy(0.15f)), contentAlignment = Alignment.Center) { Text("🙂") }
            Column(Modifier.weight(1f)) {
                Text(if (isThai) "น้องตัวอย่าง" else "Sample Child", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(if (isThai) "แตะเพื่อสลับ" else "Tap to switch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.SyncAlt, null, tint = Color(0xFF3949AB))
        }
    }
}

@Composable
private fun GrowthExportSamplePreview(isThai: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SampleBannerRow(Icons.Filled.MonitorWeight, if (isThai) "อัปเดตน้ำหนัก/ส่วนสูง" else "Update weight & height")
        SampleBannerRow(Icons.Filled.PictureAsPdf, if (isThai) "ส่งออกสรุป (PDF)" else "Export summary (PDF)")
    }
}

@Composable
private fun SampleBannerRow(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Color(0xFF3949AB), modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Guided tour overlay drawn ON TOP of the real NavHost (see AppNavigation.kt) — navigates the real
 * app through Home → Child Info (switch/sync, then growth/PDF) → Side menu → Vaccine Book → Disease
 * Risk → Statistics → Settings, dims the real screen underneath (visible but non-interactive) rather
 * than showing static mockups. A small language toggle in the card lets a first-time user switch the
 * tour's language on the spot instead of waiting for the Settings step. Steps with [TourStepInfo.targetKey]
 * carve an exact spotlight hole in the scrim around that real element (read from [TourTargetRegistry]).
 */
@Composable
fun TourOverlay(
    step: Int,
    navController: NavController,
    isThai: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val info = tourSteps[step]
    val isLast = step == tourSteps.lastIndex

    LaunchedEffect(step) {
        // popUpTo Dashboard (not just launchSingleTop) so each step REPLACES the previous tour
        // destination instead of piling up a backstack across all 8 stops.
        navController.navigate(info.route) {
            popUpTo(Screen.Dashboard.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    // System back during the tour steps backward through the tour instead of the real backstack.
    BackHandler(enabled = true) { if (step > 0) onBack() else onSkip() }

    // The overlay's own window-space origin, so a target's boundsInWindow() (from TourTargetRegistry,
    // reported by the real button buried in a different composable subtree) can be converted into this
    // Box's LOCAL draw coordinates.
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val spotlightRect = info.targetKey?.let { TourTargetRegistry.targets[it] }?.translate(-overlayOrigin.x, -overlayOrigin.y)

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
            .pointerInput(Unit) { detectTapGestures { } } // swallow taps to the real (dimmed) screen underneath
    ) {
        Canvas(
            // Offscreen compositing is required for BlendMode.Clear to punch a real transparent hole
            // instead of just blending black-on-black.
            Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.45f))
            spotlightRect?.let { r ->
                val pad = 8.dp.toPx()
                val topLeft = Offset(r.left - pad, r.top - pad)
                val size = Size(r.width + pad * 2, r.height + pad * 2)
                val corner = CornerRadius(16.dp.toPx())
                drawRoundRect(color = Color.Transparent, topLeft = topLeft, size = size, cornerRadius = corner, blendMode = BlendMode.Clear)
                drawRoundRect(color = info.color, topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(width = 3.dp.toPx()))
            }
        }

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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onToggleLanguage, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Icon(Icons.Filled.Language, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(if (isThai) "EN" else "TH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                            if (!isLast) TextButton(onClick = onSkip) { Text(if (isThai) "ข้าม" else "Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Text(if (isThai) info.titleTh else info.titleEn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = info.color)
                    Text(if (isThai) info.bodyTh else info.bodyEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

                    info.samplePreview?.let { preview ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isThai) "ตัวอย่าง" else "Sample", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            preview(isThai)
                        }
                    }

                    val targetText = if (isThai) info.targetTh else info.targetEn
                    if (targetText != null && info.arrowRotationDeg != null) {
                        Surface(shape = RoundedCornerShape(12.dp), color = info.color.copy(0.08f)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowRightAlt, null, tint = info.color,
                                    modifier = Modifier.graphicsLayer { rotationZ = info.arrowRotationDeg }
                                )
                                Text(
                                    (if (isThai) "แตะ" else "Tap ") + targetText,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

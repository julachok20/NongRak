package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

/** Parent first-run walkthrough — 9 steps after registration. */
object ParentTourSteps {
    const val STEP_CHILD_SWITCH = 1
    const val STEP_CHILD_ACTIONS = 2

    val steps = listOf(
        TourStepInfo(
            Screen.Dashboard.route, Primary, CardAnchor.START, 0.85f, 110.dp,
            arrowRotationDeg = 225f, targetTh = "ปุ่ม ⚡ บันทึกด่วน มุมล่างขวา", targetEn = "the ⚡ Quick Action button, bottom-right",
            titleTh = "ยินดีต้อนรับสู่ NongRak AI", titleEn = "Welcome to NongRak AI",
            bodyTh = "นี่คือหน้าหลักของคุณ กดปุ่ม ⚡ 'บันทึกด่วน' มุมล่างขวาเพื่อบันทึกข้อมูลอย่างรวดเร็ว และทุกเดือนแอปจะชวนคุณทำ 'ประเมินสุขภาพจิตสำหรับผู้ปกครอง'",
            bodyEn = "This is your Home page. Tap the ⚡ Quick Action button in the bottom-right for fast logging — and every month the app invites you to a Parent Mental Health Check-in."
        ),
        TourStepInfo(
            Screen.ChildSummary.route, Color(0xFF3949AB), CardAnchor.CENTER, 0.85f, 110.dp,
            targetKey = "parent_child_switch",
            samplePreview = { isThai -> ChildSwitchSamplePreview(isThai) },
            titleTh = "สลับข้อมูลลูก", titleEn = "Switch child",
            bodyTh = "แตะที่ชื่อลูกด้านบนเพื่อสลับดูข้อมูลลูกคนอื่น หรือซิงค์ข้อมูลลูกกับผู้ปกครองอีกท่านในบัญชีอื่น (Beta)",
            bodyEn = "Tap your child's name at the top to switch between children, or sync a child's data with another parent's account (Beta)."
        ),
        TourStepInfo(
            Screen.ChildSummary.route, Color(0xFF3949AB), CardAnchor.CENTER, 0.85f, 110.dp,
            targetKey = "parent_child_actions",
            samplePreview = { isThai -> ChildActionGridSamplePreview(isThai) },
            titleTh = "ปุ่มเข้าถึงฟีเจอร์", titleEn = "Feature shortcuts",
            bodyTh = "ปุ่มด้านล่าง — บันทึกสุขภาพ, ดูสถิติ, สมุดความทรงจำ, ส่งออก PDF, คลังเอกสาร, บันทึกด่วน และสิทธิ์การเข้าถึง — ใช้เข้าถึงทุกหน้าของลูกได้จากที่เดียว",
            bodyEn = "Buttons below — Health Log, Stats, Memory book, PDF export, Document vault, Quick log, and Access — your one place to reach every page for your child."
        ),
        TourStepInfo(
            Screen.HealthLog.route, Color(0xFF00897B), CardAnchor.CENTER, 0.85f, 110.dp,
            titleTh = "บันทึกสุขภาพ", titleEn = "Health Log",
            bodyTh = "ศูนย์สุขภาพ — ดูสรุปและแจ้งเตือนทั้งหมด สมุดวัคซีน — ตรวจสอบและบันทึกวัคซีน รวมถึงน้ำหนัก พัฒนาการ การนอน โภชนาการ และออกกำลังกาย",
            bodyEn = "Health Center — full summary and alerts. Vaccine book — track vaccinations. Plus weight, development, sleep, nutrition, and exercise logging."
        ),
        TourStepInfo(
            Screen.StatisticsInsights.route, Color(0xFF6A1B9A), CardAnchor.CENTER, 0.85f, 110.dp,
            titleTh = "สถิติ", titleEn = "Statistics",
            bodyTh = "ดูสถิติและแนวโน้มทั้งหมดของลูกน้อยในที่เดียว — การเจริญเติบโต โภชนาการ การนอน และสุขภาพ",
            bodyEn = "See all your child's stats and trends in one place — growth, nutrition, sleep, and health."
        ),
        TourStepInfo(
            Screen.PermissionAccess.route, Color(0xFF6A1B9A), CardAnchor.CENTER, 0.85f, 110.dp,
            titleTh = "สิทธิ์การเข้าถึง", titleEn = "Permission Access",
            bodyTh = "ควบคุมว่าใครดูหรือแก้ไขข้อมูลลูกได้ เพิ่มสมาชิกครอบครัว และแชร์ QR-code เพื่อซิงค์ข้อมูลลูกกับผู้ปกครองอื่น",
            bodyEn = "Control who can view or edit your child's data. Add family members and share a QR code to sync with another parent."
        ),
        TourStepInfo(
            Screen.AiChat.route, Color(0xFF3F51B5), CardAnchor.CENTER, 0.85f, 110.dp,
            titleTh = "แชทบอท AI", titleEn = "AI Chatbot",
            bodyTh = "ผู้ช่วยสุขภาพ 24/7 — แชทถาม-ตอบ ส่งรูปหรือเอกสาร ใช้ไมโครโฟนพูดแทนพิมพ์ หรือโทรสนทนากับ AI ได้",
            bodyEn = "Your 24/7 health assistant — chat anytime, attach photos or documents, use voice input, or start an AI voice call."
        ),
        TourStepInfo(
            Screen.MyDoctorsHub.route, Color(0xFF00897B), CardAnchor.CENTER, 0.85f, 110.dp,
            titleTh = "เชื่อมต่อ", titleEn = "Connect",
            bodyTh = "Pedia Connect — ค้นหาและจองคิวคุณหมอ ติดตามนัดหมาย Lab Connect — ดูผลแล็บ App Connect — เชื่อมต่ออุปกรณ์สวมใส่ (Health Connect)",
            bodyEn = "Pedia Connect — find and book doctors, track appointments. Lab Connect — view lab results. App Connect — link wearables via Health Connect."
        ),
        TourStepInfo(
            Screen.Dashboard.route, Primary, CardAnchor.CENTER, 0.92f, 110.dp,
            titleTh = "สรุป — พร้อมเริ่มใช้งาน!", titleEn = "Summary — you're ready!",
            bodyTh = "เริ่มจากหน้าหลัก → ข้อมูลเด็ก → บันทึกสุขภาพ/สถิติ → จัดการสิทธิ์ → แชท AI → เชื่อมต่อคุณหมอ ใช้ปุ่ม ⚡ บันทึกด่วนเมื่อต้องการบันทึกเร็ว สลับภาษาได้ทุกเมื่อจากปุ่ม TH/EN",
            bodyEn = "Start from Home → Child Info → Health Log/Stats → Access control → AI Chat → Connect doctors. Use ⚡ Quick Action for fast logging. Switch language anytime with the TH/EN toggle."
        )
    )
}

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
    GuidedTourOverlay(
        steps = ParentTourSteps.steps,
        step = step,
        homeRoute = Screen.Dashboard.route,
        navController = navController,
        isThai = isThai,
        onNext = onNext,
        onBack = onBack,
        onSkip = onSkip,
        onFinish = onFinish,
        onToggleLanguage = onToggleLanguage
    )
}

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
private fun ChildActionGridSamplePreview(isThai: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple(Icons.Filled.AddCircle, if (isThai) "บันทึกสุขภาพ" else "Health Log", Primary),
            Triple(Icons.Filled.BarChart, if (isThai) "ดูสถิติ" else "Stats", Primary),
            Triple(Icons.Filled.MenuBook, if (isThai) "สมุดแห่งความทรงจำ" else "Memory book", Color(0xFFFFC107)),
            Triple(Icons.Filled.PictureAsPdf, if (isThai) "ส่งออก PDF" else "Export PDF", Error),
            Triple(Icons.Filled.FolderOpen, if (isThai) "คลังเอกสาร" else "Document vault", Color(0xFF43A047)),
            Triple(Icons.Filled.Bolt, if (isThai) "บันทึกด่วน" else "Quick log", PrimaryContainer),
            Triple(Icons.Filled.LockPerson, if (isThai) "สิทธิ์การเข้าถึง" else "Access", Secondary)
        ).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (icon, label, tint) ->
                    SampleActionChip(icon, label, tint, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SampleActionChip(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f), modifier = modifier) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

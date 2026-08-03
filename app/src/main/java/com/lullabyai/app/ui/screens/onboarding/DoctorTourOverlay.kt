package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary

/** Doctor first sign-up walkthrough — 4 steps. */
object DoctorTourSteps {
    val steps = listOf(
        TourStepInfo(
            Screen.DoctorDashboard.route, Primary, CardAnchor.START, 0.85f, 110.dp,
            titleTh = "ยินดีต้อนรับคุณหมอ", titleEn = "Welcome, Doctor",
            bodyTh = "นี่คือหน้าหลักของคุณ — ดูคิวผู้ป่วยวันนี้ ผู้ป่วยล่าสุด และเมนูจัดการคลินิก ตั้งค่าเวลาทำงานก่อนรับการจองจากผู้ปกครอง",
            bodyEn = "This is your home — today's patient queue, recent patients, and clinic management. Set your working hours before parents can book."
        ),
        TourStepInfo(
            Screen.DoctorPatientManagement.route, Color(0xFF3949AB), CardAnchor.CENTER, 0.85f, 110.dp,
            titleTh = "ผู้ป่วย", titleEn = "Patients",
            bodyTh = "เข้าถึงรายชื่อผู้ป่วย ข้อมูลผู้ป่วย ลงทะเบียนผู้ป่วยใหม่ และจัดการช่องนัดหมายได้จากศูนย์กลางนี้",
            bodyEn = "Access your patient directory, patient records, new registrations, and slot management from this hub."
        ),
        TourStepInfo(
            Screen.DoctorPatientSchedule.route, Color(0xFF00897B), CardAnchor.CENTER, 0.85f, 110.dp,
            titleTh = "นัดหมาย", titleEn = "Appointments",
            bodyTh = "ดูตารางนัดหมายทั้งหมด ตรวจสอบคิววันนี้ และบันทึกผลการตรวจหลังพบผู้ป่วย",
            bodyEn = "View your full schedule, check today's queue, and record visit results after seeing a patient."
        ),
        TourStepInfo(
            Screen.DoctorDashboard.route, Primary, CardAnchor.CENTER, 0.92f, 110.dp,
            titleTh = "พร้อมเริ่มใช้งาน!", titleEn = "You're ready!",
            bodyTh = "เริ่มจากหน้าหลัก → ผู้ป่วย → นัดหมาย ใช้แถบด้านล่างสลับระหว่างหน้าหลัก ผู้ป่วย และตั้งค่า สลับภาษาได้ทุกเมื่อจากปุ่ม TH/EN",
            bodyEn = "Start from Home → Patients → Appointments. Use the bottom bar to switch between Home, Patients, and Settings. Switch language anytime with the TH/EN toggle."
        )
    )
}

@Composable
fun DoctorTourOverlay(
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
        steps = DoctorTourSteps.steps,
        step = step,
        homeRoute = Screen.DoctorDashboard.route,
        navController = navController,
        isThai = isThai,
        onNext = onNext,
        onBack = onBack,
        onSkip = onSkip,
        onFinish = onFinish,
        onToggleLanguage = onToggleLanguage
    )
}

package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

/**
 * Parent first-run walkthrough — three steps, all on the Dashboard.
 *
 * Each step spotlights one real control and asks the parent to *do* one thing: log a record, ask
 * the AI, open the child page. The tour advances when she taps the control (the shell watches for
 * the route change), not when she presses Next — so she finishes holding proof the app works
 * rather than a map of where things are.
 *
 * The nine-step version this replaced narrated ~25 feature names across screens she had no reason
 * to visit yet; the features it used to list are better introduced by a one-line tip the first time
 * she actually opens each screen.
 */
object ParentTourSteps {

    val steps = listOf(
        // 1 — logging is cheap. Ten seconds disproves "this will be a form" faster than any sentence.
        TourStepInfo(
            Screen.Dashboard.route, Primary, CardAnchor.CENTER, 0.9f, 110.dp,
            targetKey = "parent_quick_action",
            titleTh = "ลองบันทึกครั้งแรก", titleEn = "Try your first record",
            bodyTh = "แตะการ์ด ⚡ บันทึกด่วน แล้วลองบันทึกการนอนของลูกเมื่อคืน ใช้เวลาแค่ 10 วินาที",
            bodyEn = "Tap the ⚡ Quick Action card and log last night's sleep. It takes ten seconds."
        ),
        // 2 — the reason she installed the app. It used to sit at step 7 of 9, behind six menu readings.
        TourStepInfo(
            Screen.Dashboard.route, Color(0xFF3F51B5), CardAnchor.CENTER, 0.9f, 150.dp,
            targetKey = "nav_ai_fab",
            titleTh = "ถามอะไรก็ได้ ตี 2 ก็ตอบ", titleEn = "Ask anything, even at 2am",
            bodyTh = "แตะปุ่มกลางด้านล่าง แล้วลองถามว่า \"ลูกตื่นกลางคืนบ่อย ปกติไหม\"",
            bodyEn = "Tap the centre button below, then try asking: \"My baby wakes often at night — is that normal?\""
        ),
        // 3 — one address to remember, plus permission to fill it in slowly. The vaccine book does
        // not have to be found tonight.
        TourStepInfo(
            Screen.Dashboard.route, Color(0xFF3949AB), CardAnchor.CENTER, 0.9f, 150.dp,
            targetKey = "nav_" + com.lullabyai.app.ui.components.BottomTab.CHILD.name,
            titleTh = "ทุกอย่างของลูกอยู่ตรงนี้", titleEn = "Everything about your child lives here",
            bodyTh = "แตะแท็บ \"ข้อมูลลูก\" — วัคซีน น้ำหนัก พัฒนาการ เก็บไว้ที่เดียว ค่อย ๆ เพิ่มได้ และแชร์ให้คุณพ่อหรือคุณยายดูด้วยกันได้",
            bodyEn = "Tap the Child tab — vaccines, weight, development, all in one place. Add it a little at a time, and share it with dad or grandma."
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

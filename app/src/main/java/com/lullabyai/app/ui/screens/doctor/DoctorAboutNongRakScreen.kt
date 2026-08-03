@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

/**
 * "About NongRak (เกี่ยวกับ NongRak AI) - Doctor Mode". Same InfoScaffold-style pattern as
 * InfoPages.kt's AboutUsScreen (that helper is private to that file, so a small local one is
 * used here), filled in with the doctor-facing mission/feature content from the Stitch design.
 */
@Composable
fun DoctorAboutNongRakScreen(navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เกี่ยวกับ NongRak AI" else "About NongRak AI",
                onBack = onBack,
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.SETTINGS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isThai) "สำหรับบุคลากรทางการแพทย์" else "For medical personnel",
                style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold
            )

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(22.dp))
                        Text("NongRak AI", style = MaterialTheme.typography.headlineSmall, color = Primary, fontWeight = FontWeight.Bold)
                    }
                    Text("Version 2.4.0 (Stable)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeading(icon = Icons.Filled.AutoAwesome, text = if (isThai) "พันธกิจของเรา" else "Our Mission")
            Para(
                if (isThai)
                    "เรามุ่งมั่นสนับสนุนกุมารแพทย์ด้วยเทคโนโลยี AI ที่แม่นยำ เพื่อยกระดับการดูแลสุขภาพเด็กไทย ลดภาระงานของแพทย์ และเพิ่มความแม่นยำในการวินิจฉัย โดยอ้างอิงจากฐานข้อมูลทางการแพทย์ระดับสากล"
                else
                    "We're committed to supporting pediatricians with precise AI technology to elevate child healthcare in Thailand — reducing workload and improving diagnostic accuracy, trained against international medical databases."
            )

            SectionHeading(icon = Icons.Filled.Psychology, text = if (isThai) "การวิเคราะห์อัจฉริยะ" else "Smart Analysis")
            Para(
                if (isThai) "อัลกอริทึมที่ผ่านการฝึกฝนด้วยฐานข้อมูลทางการแพทย์ระดับสากล เพื่อผลลัพธ์ที่เชื่อถือได้"
                else "Algorithms trained on international medical databases for reliable results"
            )

            SectionHeading(icon = Icons.Filled.VerifiedUser, text = if (isThai) "ความปลอดภัยของข้อมูล" else "Data Security")
            Para(
                if (isThai) "ข้อมูลของผู้ป่วยได้รับการปกป้องตามมาตรฐาน HIPAA และระบบความปลอดภัยระดับองค์กร"
                else "Patient confidentiality is protected per HIPAA standards and enterprise-level security"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { navController.navigate(Screen.PrivacyPolicy.route) }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (isThai) "นโยบายความเป็นส่วนตัว" else "Privacy Policy", color = Primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { navController.navigate(Screen.TermsOfUse.route) }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (isThai) "ข้อกำหนดการใช้งาน" else "Terms of Service", color = Primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                if (isThai) "© 2026 NongRak AI สงวนลิขสิทธิ์ เข้าถึงได้เฉพาะบุคลากรทางการแพทย์ที่ผ่านการยืนยันตัวตนแล้วเท่านั้น"
                else "© 2026 NongRak AI. All rights reserved. Access restricted to verified medical personnel only.",
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
        }
    }
}

@Composable
private fun SectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

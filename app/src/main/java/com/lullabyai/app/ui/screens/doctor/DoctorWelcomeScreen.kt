package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.components.LanguageToggleButton
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.SettingsViewModel

/**
 * "Modern Doctor Welcome Screen (Redesign)" — the doctor-side landing/marketing screen.
 * Parent Welcome no longer links here (parent-only). Route `doctor_welcome` still exists for
 * in-flow navigation. Leads to Doctor Sign In and Doctor Sign Up, and back to parent Welcome
 * via the mode toggle.
 */
@Composable
fun DoctorWelcomeScreen(navController: NavController, settingsViewModel: SettingsViewModel) {
    val settings by settingsViewModel.settings.collectAsState()
    val isThai = settings.language == AppLanguage.TH

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrimaryContainer.copy(alpha = 0.12f), Color.White)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: brand row + language toggle
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.MedicalServices, null, tint = Primary, modifier = Modifier.size(22.dp))
                    Text("NongRak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                }
                LanguageToggleButton(isThai = isThai, onToggle = { settingsViewModel.toggleLanguage() })
            }

            Spacer(Modifier.height(28.dp))

            // Verified badge for medical professionals
            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(alpha = 0.1f)) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = Primary, modifier = Modifier.size(16.dp))
                    Text(
                        if (isThai) "สำหรับบุคลากรทางการแพทย์" else "For healthcare professionals",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                if (isThai) "AI Precision Support" else "AI Precision Support",
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            // Headline metric
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                Text(
                    if (isThai) "ความแม่นยำสูง 99.8%" else "99.8% analysis accuracy",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                if (isThai) "ดูแลเจ้าตัวเล็กได้มั่นใจ ด้วยผู้ช่วยอัจฉริยะ" else "Care for your smallest patients with confidence, powered by AI",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                if (isThai)
                    "NongRak AI นำเทคโนโลยีปัญญาประดิษฐ์มาช่วยเพิ่มความแม่นยำและความรวดเร็วในการวินิจฉัย ยกระดับการดูแลเด็กสู่มาตรฐานสากล"
                else
                    "NongRak AI brings artificial intelligence to help sharpen diagnostic precision and speed, elevating pediatric care to an international standard.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Primary/secondary CTAs
            Button(
                onClick = { navController.navigate(Screen.DoctorSignIn.route) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(if (isThai) "เข้าสู่ระบบแพทย์" else "Doctor sign in", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { navController.navigate(Screen.DoctorSignUp.route) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
            ) {
                Text(if (isThai) "ลงทะเบียนพาร์ทเนอร์" else "Register as partner", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(28.dp))

            // Feature row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DoctorFeatureCard(
                    icon = Icons.Filled.Sync,
                    title = if (isThai) "เรียลไทม์" else "Real-time",
                    subtitle = if (isThai) "ข้อมูลเชิงลึกทันใจ" else "Instant insights",
                    modifier = Modifier.weight(1f)
                )
                DoctorFeatureCard(
                    icon = Icons.Filled.Public,
                    title = if (isThai) "สากล" else "Global",
                    subtitle = if (isThai) "มาตรฐานระดับโลก" else "International standard",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = { navController.popBackStack() }) {
                Text(if (isThai) "สลับเป็นโหมดผู้ปกครอง" else "Switch to parent mode", color = Primary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { navController.navigate(Screen.PrivacyPolicy.route) }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (isThai) "ความเป็นส่วนตัว" else "Privacy", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
                TextButton(onClick = { navController.navigate(Screen.TermsOfUse.route) }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (isThai) "ข้อกำหนดการใช้งาน" else "Terms of service", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            Text("NongRak © 2026 All rights reserved.", style = MaterialTheme.typography.labelSmall, color = Outline)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DoctorFeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

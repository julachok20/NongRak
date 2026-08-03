package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Primary

/**
 * "Signing In (กำลังเข้าสู่ระบบ)". IMPORTANT — per the actual Stitch design (verified via the
 * rendered HTML, not just its title): this is a LOADING/TRANSITION screen, not a credentials
 * form. It has no email/password fields and no submit button — only a spinner and two status
 * lines ("กำลังเข้าสู่ระบบ..." / "ตรวจสอบความถูกต้องของข้อมูลส่วนบุคคล...") plus a verified badge.
 *
 * Real credentials form lives in [DoctorSignInScreen] (`Screen.DoctorSignIn`). Keep this route
 * if Agent C wants a Stitch-faithful interstitial after submit; otherwise the form's own
 * PrimaryButton loading state is enough (current DoctorSignIn behavior).
 */
@Composable
fun DoctorLoginScreen(navController: NavController) {
    val s = LocalStrings.current
    val isThai = s.isThai

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "กำลังเข้าสู่ระบบ" else "Signing In",
                onBack = { navController.popBackStack() },
                navController = navController,
                // No bell / avatar on an auth screen — nobody is signed in yet, and both actions
                // navigate to PARENT destinations (Notifications / Settings), which would drop a
                // doctor out of doctor mode entirely.
                showNotification = false,
                showProfile = false
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MedicalServices, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("NongRak AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(20.dp))
            Text(
                if (isThai) "กำลังเข้าสู่ระบบ..." else "Signing in...",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isThai) "ตรวจสอบความถูกต้องของข้อมูลส่วนบุคคล..." else "Verifying your personal information...",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.VerifiedUser, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                Text("NongRak", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
        }
    }
}

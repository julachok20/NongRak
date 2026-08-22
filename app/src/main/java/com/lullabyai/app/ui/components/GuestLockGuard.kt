package com.lullabyai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * Gates screens that require an active registered account (e.g. adding children, booking doctor
 * appointments, sharing caregiver access, or recording data). In Test/Guest mode, displays a
 * locked view with clear sign-up / login action buttons.
 */
@Composable
fun GuestLockGuard(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit,
    title: String? = null,
    message: String? = null,
    content: @Composable () -> Unit
) {
    if (childViewModel.isGuest()) {
        val s = LocalStrings.current
        val isThai = s.isThai

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (isThai) "✨ บัญชีทดลอง (Test Account)" else "✨ Test / Demo Account",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = title ?: if (isThai) "ต้องสมัครสมาชิกก่อนใช้งานหน้านี้" else "Registration Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = message ?: if (isThai)
                        "ฟีเจอร์นี้เปิดให้ใช้งานสำหรับสมาชิกที่ลงทะเบียนเท่านั้น บัญชีทดลองสามารถดูข้อมูลตัวอย่างและสำรวจระบบได้ กรุณาสมัครสมาชิกเพื่อใช้งานอย่างเต็มรูปแบบ"
                    else
                        "This feature is available for registered members only. Test accounts can view sample data and explore features. Please sign up to unlock full functionality.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { navController.navigate(Screen.SignUp.route) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(if (isThai) "สมัครสมาชิก (ฟรี)" else "Sign up (Free)", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { navController.navigate(Screen.Login.route) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(if (isThai) "เข้าสู่ระบบ" else "Sign in", fontWeight = FontWeight.Bold, color = Primary)
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onBack) {
                    Text(if (isThai) "ย้อนกลับ" else "Go back", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        content()
    }
}

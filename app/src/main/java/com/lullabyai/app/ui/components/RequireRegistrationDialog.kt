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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

@Composable
fun RequireRegistrationDialog(
    title: String = "ต้องสมัครสมาชิกก่อนใช้งาน",
    message: String = "กรุณาสมัครสมาชิกหรือเข้าสู่ระบบเพื่อบันทึกข้อมูลและใช้งานฟีเจอร์นี้",
    isThai: Boolean = true,
    onDismiss: () -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Primary.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (isThai) "✨ บัญชีทดลอง (Test) — ดูข้อมูลตัวอย่างได้เท่านั้น" else "✨ Test Account — View-only mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onSignUp()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(
                    if (isThai) "สมัครสมาชิก (ฟรี)" else "Sign up (Free)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    onDismiss()
                    onSignIn()
                }) {
                    Text(
                        if (isThai) "เข้าสู่ระบบ" else "Sign in",
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        if (isThai) "ไว้คราวหน้า" else "Maybe later",
                        color = Outline
                    )
                }
            }
        }
    )
}

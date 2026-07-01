package com.lullabyai.app.ui.screens.onboarding

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*

/**
 * Medical Guidance disclaimer shown once after sign-up, before onboarding. Makes clear that NongRak
 * AI guides the parent and is NOT a replacement for a licensed physician. The user must accept.
 */
@Composable
fun MedicalDisclaimerScreen(onAccept: () -> Unit, onBack: () -> Unit) {
    val s = LocalStrings.current
    val isThai = s.isThai

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) }
                Text(if (isThai) "Medical Guidance" else "Medical Guidance", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (isThai) "คำแนะนำการใช้งานและข้อกำหนดทางการแพทย์" else "Usage Guidance & Medical Terms",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isThai) "โปรดอ่านข้อมูลสำคัญนี้ก่อนเริ่มใช้งาน" else "Please read this important information before you start",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(84.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.HealthAndSafety, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(Icons.Filled.AutoAwesome, if (isThai) "ผู้ช่วย AI" else "AI Assistant")
                Pill(Icons.Filled.MenuBook, if (isThai) "ให้คำแนะนำเท่านั้น" else "Guidance Only")
            }

            Spacer(Modifier.height(20.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            if (isThai)
                                "NongRak AI เป็นเพียงผู้ช่วยในการติดตามพัฒนาการและให้คำแนะนำด้านสุขภาพทั่วไป ไม่ใช่การวินิจฉัยทางการแพทย์ การให้คำปรึกษาจากวิชาชีพ หรือการรักษาโดยแพทย์ที่มีใบอนุญาต"
                            else
                                "NongRak AI is an assistant for tracking development and providing general health guidance. It is NOT a replacement for a clinical diagnosis, professional medical advice, or treatment by a licensed physician.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (isThai) "ข้อควรปฏิบัติ (Key Points)" else "Key Points",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            KeyPoint(Icons.Filled.Lightbulb, if (isThai) "ข้อมูลเชิงลึกของ AI มาจากข้อมูลทางการแพทย์ทั่วไป และอ้างอิงเพื่อการศึกษาเท่านั้น" else "AI insights come from general medical knowledge, for educational reference only.")
            KeyPoint(Icons.Filled.MedicalServices, if (isThai) "ควรปรึกษาแพทย์ทุกครั้งเมื่อมีอาการกังวลเกี่ยวกับสุขภาพของลูกน้อย" else "Always consult a doctor when you are concerned about your child's health.")
            KeyPoint(Icons.Filled.Warning, if (isThai) "ในกรณีฉุกเฉิน โปรดติดต่อบริการฉุกเฉินในพื้นที่ของคุณทันที" else "In an emergency, contact your local emergency services immediately.", danger = true)

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Lock, null, tint = Outline, modifier = Modifier.size(14.dp))
                Text(if (isThai) "ข้อมูลของคุณถูกจัดเก็บอย่างปลอดภัยและเป็นส่วนตัว" else "Your data is handled securely and privately.", style = MaterialTheme.typography.labelSmall, color = Outline)
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = if (isThai) "ฉันเข้าใจและยอมรับ" else "I understand and accept", onClick = onAccept)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun KeyPoint(icon: ImageVector, text: String, danger: Boolean = false) {
    val bg = if (danger) Error.copy(0.08f) else MaterialTheme.colorScheme.surface
    val tint = if (danger) Error else Primary
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

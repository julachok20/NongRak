package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.theme.*

/**
 * "Help Center (Simplified) - Doctor Mode". Mirrors HelpFaqScreen.kt's structure (intro card +
 * expandable FAQ list + contact card), extended with the design's search bar / popular topics /
 * category rows for the doctor-facing content.
 *
 * Omitted from the Stitch design: the generic marketing footer (App Store/Google Play badges,
 * "Medical Imaging / Clinical Copilot / Data Analytics" product links) — those describe a
 * separate marketing website, not content that belongs inside a native in-app screen, so
 * reproducing them here would be inventing dead links rather than preserving the design's intent.
 */
@Composable
fun DoctorHelpFaqScreen(navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }

    val categories = listOf(
        Triple(Icons.Filled.MedicalServices, if (isThai) "ฟีเจอร์ทางการแพทย์" else "Clinical Features", if (isThai) "คู่มือการใช้งานฟีเจอร์ทางการแพทย์" else "Medical feature guides"),
        Triple(Icons.Filled.Build, if (isThai) "ฝ่ายเทคนิค" else "Technical Support", if (isThai) "ปัญหาด้านการติดตั้งและระบบ" else "Installation & system issues"),
        Triple(Icons.Filled.ManageAccounts, if (isThai) "การจัดการบัญชี" else "Account Management", if (isThai) "การจัดการโปรไฟล์และสิทธิ์" else "Profile & permissions management")
    )

    val faqs = listOf(
        (if (isThai) "เปลี่ยนรหัสผ่านอย่างไร?" else "How do I change my password?") to
            (if (isThai) "ไปที่เมนู 'การตั้งค่า' เลือก 'ความปลอดภัย' แล้วเลือก 'เปลี่ยนรหัสผ่าน'" else "Go to 'Settings', select 'Security', then 'Change Password'"),
        (if (isThai) "รองรับไฟล์รูปแบบใดบ้าง?" else "What file formats are supported?") to
            (if (isThai) "ไฟล์มาตรฐานทางการแพทย์ DICOM รวมถึง JPEG และ PNG" else "DICOM medical standard files, as well as JPEG and PNG"),
        (if (isThai) "แชร์ผลการวิเคราะห์อย่างไร?" else "How do I share analysis results?") to
            (if (isThai) "ผ่าน QR Code หรือลิงก์ที่ปลอดภัยแบบจำกัดเวลา" else "Via QR Code or a time-limited secure link")
    )
    val filteredFaqs = faqs.filter { (q, a) -> query.isBlank() || q.contains(query, true) || a.contains(query, true) }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "ศูนย์ช่วยเหลือ" else "Help Center", onBack = onBack, navController = navController, isDoctor = true) },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.SETTINGS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isThai) "เราจะช่วยอะไรคุณได้บ้างในวันนี้?" else "How can we help you today?", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        if (isThai) "การเชื่อมต่อ HIS" else "HIS integration",
                        if (isThai) "การวิเคราะห์ AI" else "AI analysis",
                        if (isThai) "ลืมรหัสผ่าน" else "Forgot password"
                    ).forEach { tag ->
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(alpha = 0.08f)) {
                            Text(tag, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            items(categories) { (icon, title, subtitle) ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionLabel(if (isThai) "คำถามที่พบบ่อย" else "FAQs") }
            items(filteredFaqs) { (question, answer) -> FaqItem(question, answer) }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isThai) "ยังมีคำถามอื่น?" else "Still have questions?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(if (isThai) "ติดต่อทีมสนับสนุนของเราได้ที่อีเมล" else "Contact our support team by email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:julachok2@gmail.com")
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "NongRak AI - ติดต่อสอบถาม (แพทย์)")
                                }
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Filled.Email, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "ติดต่อ julachok2@gmail.com" else "Contact julachok2@gmail.com")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(question, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Outline)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

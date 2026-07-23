@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.help

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

@Composable
fun HelpFaqScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val faqs = listOf(
        Pair("NongRak คืออะไร?", "NongRak คือผู้ช่วย AI สำหรับคุณพ่อคุณแม่ในการดูแลและติดตามพัฒนาการของลูกน้อย โดยใช้ปัญญาประดิษฐ์ในการวิเคราะห์ข้อมูลสุขภาพและให้คำแนะนำที่เหมาะสม"),
        Pair(
            "แอปนี้ทำอะไรได้บ้าง?",
            "NongRak ช่วยติดตามและบันทึกทุกด้านของลูกน้อยในที่เดียว ได้แก่ การเจริญเติบโต (น้ำหนัก/ส่วนสูง เทียบเกณฑ์ WHO), สมุดวัคซีน, พัฒนาการตามวัย, ประวัติสุขภาพและอาการป่วย, โภชนาการและแคลอรี่, การให้นม, การขับถ่าย, การนอนหลับ (พร้อมเสียงกล่อมนอน), สถิติและข้อมูลเชิงลึกแบบกราฟ, รายงานสรุปประจำเดือน, แบบประเมินสุขภาพใจผู้ปกครอง, คลังข้อมูลทางการแพทย์ที่ AI ใช้วิเคราะห์ และผู้ช่วยแชท AI ที่ตอบคำถามหรือช่วยบันทึกข้อมูลแทนคุณได้"
        ),
        Pair(
            "มีฟังก์ชันอะไรบ้าง และเข้าถึงได้อย่างไร?",
            "เมนูด้านล่างของแอปมี 5 แท็บหลัก: 'หน้าหลัก' รวมภาพรวมและบริการทั้งหมด, 'แชท AI' (ปุ่มกลาง) คุยกับผู้ช่วย AI, 'ข้อมูลเด็ก' ดูโปรไฟล์ วัคซีน พัฒนาการ และประวัติสุขภาพ, 'สถิติ' ดูกราฟและข้อมูลเชิงลึกทุกด้าน (น้ำหนัก ส่วนสูง โภชนาการ การนอน การขับถ่าย) รวมถึงรายงานประจำเดือน, และ 'ตั้งค่า' สำหรับจัดการบัญชี ความเป็นส่วนตัว และดูข้อมูลที่ AI เก็บไว้ นอกจากนี้ยังมีปุ่มบันทึกด่วนบนหน้าหลักสำหรับบันทึกการกิน การนอน อาการป่วย หรือทักษะใหม่ได้ในแตะเดียว"
        ),
        Pair("ข้อมูลของลูกจะปลอดภัยไหม?", "ข้อมูลทั้งหมดถูกเข้ารหัสและจัดเก็บอย่างปลอดภัยบนเซิร์ฟเวอร์ที่ได้มาตรฐาน เราไม่เปิดเผยข้อมูลส่วนตัวให้บุคคลที่สาม"),
        Pair("AI ให้คำแนะนำทางการแพทย์ได้ไหม?", "NongRak ให้ข้อมูลและคำแนะนำเบื้องต้นเท่านั้น ไม่ใช่การวินิจฉัยทางการแพทย์ หากมีปัญหาสุขภาพเร่งด่วนควรพบแพทย์ทันที"),
        Pair("จะเพิ่มข้อมูลวัคซีนได้อย่างไร?", "ไปที่หน้า 'ข้อมูลลูก' → 'สมุดวัคซีน' แล้วกดปุ่ม '+' เพื่อเพิ่มรายการวัคซีนใหม่"),
        Pair("บันทึกการเจริญเติบโตทำอย่างไร?", "ไปที่หน้าหลัก → 'บันทึกพัฒนาการ' หรือจากหน้า 'ข้อมูลลูก' แล้วเลือก 'บันทึกการเจริญเติบโต'"),
        Pair("ลืมรหัสผ่านทำอย่างไร?", "กดปุ่ม 'ลืมรหัสผ่าน' ในหน้าเข้าสู่ระบบ ระบบจะส่งลิงก์รีเซ็ตรหัสผ่านไปยังอีเมลที่ลงทะเบียน"),
        Pair("สามารถมีบัญชีลูกหลายคนได้ไหม?", "ได้ครับ/ค่ะ เพิ่มลูกได้หลายคนต่อบัญชี และสามารถเชื่อมโปรไฟล์เด็กคนเดียวกันกับผู้ปกครองอีกท่านได้ด้วย (เช่น พ่อกับแม่ดูข้อมูลลูกร่วมกัน) ผ่านเมนู 'เพิ่มลูก' → 'เชื่อมข้อมูล'"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("วิธีใช้งาน & FAQ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "กลับไปหน้าตั้งค่า", tint = Primary)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(Icons.Filled.Notifications, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            // Help now lives under Settings — keep the Settings tab highlighted here
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.SETTINGS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Help, null, tint = Primary, modifier = Modifier.size(28.dp))
                        Column {
                            Text("คำถามที่พบบ่อย", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                            Text("ค้นหาคำตอบที่ต้องการได้ที่นี่", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(faqs) { (question, answer) ->
                FaqItem(question = question, answer = answer)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ยังมีคำถามอื่น?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("ติดต่อทีมสนับสนุนของเราได้ที่อีเมล", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:julachok2@gmail.com")
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "NongRak - ติดต่อสอบถาม")
                                }
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Filled.Email, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ติดต่อ julachok2@gmail.com")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
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

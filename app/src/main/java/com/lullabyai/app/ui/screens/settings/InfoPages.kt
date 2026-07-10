@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary

@Composable
private fun InfoScaffold(title: String, navController: NavController, onBack: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.SETTINGS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = body
        )
    }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
fun AboutUsScreen(navController: NavController, onBack: () -> Unit) = InfoScaffold("เกี่ยวกับเรา", navController, onBack) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("NongRak", style = MaterialTheme.typography.headlineSmall, color = Primary, fontWeight = FontWeight.Bold)
            Text("เวอร์ชั่น 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Para("NongRak คือผู้ช่วย AI สำหรับคุณพ่อคุณแม่ในการดูแลและติดตามพัฒนาการของลูกน้อย เราเชื่อว่าการเลี้ยงดูเด็กควรเป็นเรื่องที่อบอุ่นและไม่กดดัน")
    Heading("พันธกิจของเรา")
    Para("เราตั้งใจนำเทคโนโลยีปัญญาประดิษฐ์มาช่วยให้คุณแม่ทุกคนเข้าถึงคำแนะนำการเลี้ยงดูเด็กที่ถูกต้อง เหมาะสมกับวัย และพร้อมให้คำปรึกษาตลอด 24 ชั่วโมง")
    Heading("สิ่งที่เราดูแล")
    Para("• ติดตามพัฒนาการตามวัย\n• บันทึกการเจริญเติบโตและสุขภาพ\n• สมุดวัคซีนดิจิทัล\n• ประเมินความเสี่ยงโรคด้วย AI\n• ผู้ช่วย AI ตอบทุกคำถามเรื่องลูก")
    Para("© 2026 NongRak. All rights reserved.")
}

@Composable
fun PrivacyPolicyScreen(navController: NavController, onBack: () -> Unit) = InfoScaffold("นโยบายความเป็นส่วนตัว", navController, onBack) {
    Para("นโยบายนี้จัดทำตามพระราชบัญญัติคุ้มครองข้อมูลส่วนบุคคล พ.ศ. 2562 (PDPA) อธิบายว่าเราเก็บรวบรวม ใช้ เปิดเผย และปกป้องข้อมูลของคุณและบุตรหลานอย่างไร (ปรับปรุงล่าสุด 13 มิถุนายน 2569)")

    Heading("1. ผู้ควบคุมข้อมูลส่วนบุคคล")
    Para("[ชื่อผู้พัฒนา/บริษัท — โปรดกรอก] ผู้ให้บริการแอป NongRak AI\nติดต่อเจ้าหน้าที่คุ้มครองข้อมูล (DPO): julachok2@gmail.com")

    Heading("2. ข้อมูลที่เราเก็บรวบรวม")
    Para("• ข้อมูลบัญชี: ชื่อ อีเมล รูปโปรไฟล์\n• ข้อมูลของบุตรหลาน: ชื่อ วันเกิด เพศ น้ำหนัก ส่วนสูง รูปภาพ\n• ข้อมูลสุขภาพที่ละเอียดอ่อน: ประวัติการแพ้ ประวัติการรักษา วัคซีน พัฒนาการ และข้อมูลที่ใช้ประเมินความเสี่ยงโรค\n• บทสนทนากับผู้ช่วย AI")

    Heading("3. วัตถุประสงค์และฐานทางกฎหมาย")
    Para("เราประมวลผลข้อมูลโดยอาศัย ความยินยอมโดยชัดแจ้ง ของคุณ (ที่ให้ไว้ตอนสมัคร) เพื่อให้บริการวิเคราะห์ ติดตามพัฒนาการ สมุดวัคซีน และคำแนะนำการเลี้ยงดู เนื่องจากข้อมูลสุขภาพเป็นข้อมูลอ่อนไหว เราจึงขอความยินยอมเป็นการเฉพาะ")

    Heading("4. การประมวลผลด้วย AI และการเปิดเผยข้อมูล")
    Para("เพื่อสร้างคำตอบ ระบบจะส่งข้อความและข้อมูลที่เกี่ยวข้องไปประมวลผลกับผู้ให้บริการ AI ภายนอก ได้แก่ Google (Gemini) และ Anthropic (Claude) ผ่านเซิร์ฟเวอร์ที่ปลอดภัยของเรา เราไม่ขายข้อมูลของคุณ และไม่เปิดเผยให้บุคคลที่สามเพื่อการตลาด")

    Heading("5. การใช้ข้อมูลเพื่อพัฒนาโมเดล AI (ทางเลือก แยกจากความยินยอมข้างต้น)")
    Para(
        "นี่คือวัตถุประสงค์ที่แยกต่างหากจากข้อ 3-4 และไม่ใช่เงื่อนไขบังคับในการใช้แอป: หากคุณเปิดใช้งานตัวเลือก " +
        "\"ใช้ข้อมูลเพื่อพัฒนาโมเดล AI\" ใน ตั้งค่า > ความเป็นส่วนตัว (PDPA) ด้วยตนเอง เราจะนำข้อมูลของคุณและบุตรหลานบางส่วน " +
        "ไปใช้พัฒนา/ฝึกโมเดล AI ทางการแพทย์ของเรา โดยจะลบข้อมูลระบุตัวตนออกก่อนนำไปใช้ (de-identified) " +
        "คุณสามารถเปิดหรือปิดตัวเลือกนี้ได้ทุกเมื่อในหน้าตั้งค่า การไม่เปิดใช้งานจะไม่ส่งผลต่อการใช้งานฟีเจอร์อื่นใดของแอปทั้งสิ้น"
    )

    Heading("6. ระยะเวลาการเก็บรักษา")
    Para("เราเก็บข้อมูลไว้ตราบเท่าที่บัญชีของคุณยังใช้งานอยู่ เมื่อคุณลบบัญชี ข้อมูลของคุณและบุตรหลานจะถูกลบออกจากระบบ")

    Heading("7. ความปลอดภัยของข้อมูล")
    Para("ข้อมูลถูกส่งผ่านการเข้ารหัส (HTTPS) จัดเก็บโดยมีการควบคุมการเข้าถึงระดับผู้ใช้ (Row Level Security) รูปภาพถูกเก็บในพื้นที่ส่วนตัวและเข้าถึงได้เฉพาะผู้ปกครองเจ้าของบัญชีผ่านลิงก์ชั่วคราวเท่านั้น")

    Heading("8. สิทธิ์ของคุณตาม PDPA")
    Para("คุณมีสิทธิ์: เข้าถึงและขอสำเนาข้อมูล (เมนู ตั้งค่า > ขอข้อมูลของฉันทั้งหมด), แก้ไขข้อมูลให้ถูกต้อง, ลบข้อมูล/บัญชี, ถอนความยินยอม (รวมถึงความยินยอมข้อ 5), คัดค้านหรือระงับการประมวลผล และร้องเรียนต่อสำนักงานคณะกรรมการคุ้มครองข้อมูลส่วนบุคคล (สคส.)")

    Heading("9. ข้อมูลของเด็กและความยินยอมของผู้ปกครอง")
    Para("แอปนี้ใช้งานโดยผู้ปกครองเพื่อดูแลเด็กอายุ 0-3 ปี การใช้งานถือว่าคุณเป็นผู้ปกครองโดยชอบด้วยกฎหมายและให้ความยินยอมแทนบุตรหลานในการประมวลผลข้อมูลสุขภาพ รวมถึงความยินยอมแยกต่างหากตามข้อ 5 หากเลือกเปิดใช้งาน")

    Heading("10. การถอนความยินยอมและการลบบัญชี")
    Para("คุณสามารถถอนความยินยอมได้ทุกเมื่อโดยการลบบัญชีหรือติดต่อเรา การถอนความยินยอมอาจทำให้ไม่สามารถใช้งานบางฟีเจอร์ได้ ติดต่อ: julachok2@gmail.com")

    Para("หมายเหตุ: ข้อความในวงเล็บ [ ] เป็นข้อมูลที่ต้องกรอกให้ครบก่อนเผยแพร่จริง")
}

@Composable
fun TermsOfUseScreen(navController: NavController, onBack: () -> Unit) = InfoScaffold("ข้อกำหนดการใช้งาน", navController, onBack) {
    Para("กรุณาอ่านข้อกำหนดการใช้งานนี้อย่างละเอียดก่อนใช้แอปพลิเคชัน NongRak")
    Heading("1. การยอมรับข้อกำหนด")
    Para("การใช้แอปพลิเคชันถือว่าคุณยอมรับข้อกำหนดการใช้งานและนโยบายความเป็นส่วนตัวทั้งหมด")
    Heading("2. ข้อจำกัดความรับผิดชอบทางการแพทย์")
    Para("NongRak ให้ข้อมูลและคำแนะนำเบื้องต้นเท่านั้น ไม่ใช่การวินิจฉัยหรือการรักษาทางการแพทย์ หากลูกของคุณมีอาการผิดปกติหรือเร่งด่วน กรุณาปรึกษาแพทย์หรือผู้เชี่ยวชาญทันที")
    Heading("3. ความถูกต้องของข้อมูล")
    Para("คุณมีหน้าที่กรอกข้อมูลที่ถูกต้อง คำแนะนำของ AI อ้างอิงจากข้อมูลที่คุณให้ไว้ ความแม่นยำขึ้นอยู่กับความครบถ้วนของข้อมูล")
    Heading("4. การใช้งานที่เหมาะสม")
    Para("ห้ามใช้แอปพลิเคชันในทางที่ผิดกฎหมายหรือก่อให้เกิดความเสียหายต่อระบบและผู้ใช้อื่น")
    Heading("5. การติดต่อ")
    Para("หากมีคำถามเกี่ยวกับข้อกำหนดการใช้งาน กรุณาติดต่อ julachok2@gmail.com")
}

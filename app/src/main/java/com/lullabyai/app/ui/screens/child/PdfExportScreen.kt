@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.content.Intent
import android.widget.Toast
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.DoctorReportGenerator
import com.lullabyai.app.viewmodel.ChildViewModel

private data class CategoryOption(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val labelTh: String, val labelEn: String, val defaultOn: Boolean)
private val CATEGORY_OPTIONS = listOf(
    CategoryOption("basic", Icons.Filled.Person, "ข้อมูลเบื้องต้น", "Basic Info", true),
    CategoryOption("nutrition", Icons.Filled.TrendingUp, "ประวัติการเติบโตและโภชนาการ", "Growth & Nutrition", true),
    CategoryOption("vaccine", Icons.Filled.MedicalServices, "สมุดวัคซีน (ทั้งหมด)", "Vaccination Book (All)", true),
    CategoryOption("milestones", Icons.Filled.EmojiEvents, "พัฒนาการตามวัย", "Developmental Milestones", true),
    CategoryOption("health", Icons.Filled.HealthAndSafety, "ประวัติสุขภาพ การนอน และยา", "Health, Sleep & Medication", true),
    CategoryOption("ai", Icons.Filled.Psychology, "การวิเคราะห์สุขภาพโดย AI", "AI Health Analysis", true),
    CategoryOption("documents", Icons.Filled.AttachFile, "เอกสารแนบ", "Medical Documents", false)
)

/** "Export Report" — the universal entry point every PDF-export button in the app should route to
 *  (per Child-Project/Update/33.PDF-Export). Lets the parent choose a time period, which data
 *  categories to include, and detailed vs. summary format before generating. */
@Composable
fun PdfExportScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val child = state.child

    var periodMonths by remember { mutableStateOf<Int?>(3) }   // null = custom/all-time
    var selectedCategories by remember { mutableStateOf(CATEGORY_OPTIONS.filter { it.defaultOn }.map { it.key }.toSet()) }
    var detailed by remember { mutableStateOf(true) }

    fun share(file: java.io.File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, if (s.isThai) "ส่งออกรายงาน" else "Export report"))
    }

    fun generate() {
        if (selectedCategories.isEmpty()) {
            Toast.makeText(context, if (s.isThai) "เลือกอย่างน้อย 1 หมวดข้อมูล" else "Select at least one data category", Toast.LENGTH_SHORT).show()
            return
        }
        childViewModel.exportDoctorReport(context, s.isThai, selectedCategories, periodMonths, detailed) { file, err ->
            if (file == null) {
                Toast.makeText(context, (if (s.isThai) "สร้าง PDF ไม่สำเร็จ" else "Couldn't create PDF") + (err?.let { ": $it" } ?: ""), Toast.LENGTH_LONG).show()
            } else {
                runCatching { share(file) }.onFailure {
                    Toast.makeText(context, (if (s.isThai) "แชร์ไม่สำเร็จ: " else "Share failed: ") + it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ส่งออกรายงาน" else "Export Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.background) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { generate() },
                        enabled = !state.reportLoading,
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (state.reportLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else {
                            Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                            Text(if (s.isThai) "สร้างไฟล์ PDF" else "Generate PDF", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Report preview card
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).background(Primary.copy(0.08f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Description, null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(child?.name ?: "", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Primary)
                        Text(if (s.isThai) "รายงานสุขภาพทางการแพทย์" else "Medical Health Report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.08f)) {
                            Text(
                                periodMonths?.let { (if (s.isThai) "$it เดือนล่าสุด" else "Last $it months") } ?: (if (s.isThai) "ทั้งหมด" else "All time"),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Period
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (s.isThai) "เลือกช่วงเวลา" else "Select Period", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(3 to (if (s.isThai) "3 เดือน" else "3 Months"), 6 to (if (s.isThai) "6 เดือน" else "6 Months"), null to (if (s.isThai) "ทั้งหมด" else "All time"))
                        .forEach { (months, label) ->
                            val selected = periodMonths == months
                            OutlinedButton(
                                onClick = { periodMonths = months },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Primary.copy(0.08f) else Color.Transparent, contentColor = if (selected) Primary else MaterialTheme.colorScheme.onSurface),
                                border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
                            ) { Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
                        }
                }
            }

            // Data categories
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (s.isThai) "ข้อมูลที่ต้องการแสดง" else "Data Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        CATEGORY_OPTIONS.forEachIndexed { i, opt ->
                            val checked = opt.key in selectedCategories
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(Modifier.size(40.dp).background(Primary.copy(0.08f), RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
                                    Icon(opt.icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(if (s.isThai) opt.labelTh else opt.labelEn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on -> selectedCategories = if (on) selectedCategories + opt.key else selectedCategories - opt.key },
                                    colors = CheckboxDefaults.colors(checkedColor = Primary)
                                )
                            }
                            if (i < CATEGORY_OPTIONS.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            // Format
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (s.isThai) "รูปแบบรายงาน" else "Report Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), RoundedCornerShape(100.dp)).padding(4.dp)) {
                    listOf(true to (if (s.isThai) "รายงานฉบับเต็ม" else "Detailed Report"), false to (if (s.isThai) "สรุปย่อ" else "Summary Only")).forEach { (isDetailed, label) ->
                        val selected = detailed == isDetailed
                        Surface(
                            onClick = { detailed = isDetailed }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp),
                            color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                        ) {
                            Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    if (detailed) (if (s.isThai) "รายงานฉบับเต็มรวมทุกตาราง บันทึกย่อ และรายละเอียด" else "Detailed includes all tables, notes, and individual logs.")
                    else (if (s.isThai) "สรุปย่อแสดงเฉพาะตัวเลขสำคัญและตาราง ไม่รวมบันทึกย่อยาว" else "Summary shows key figures and tables only, without long free-text notes."),
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

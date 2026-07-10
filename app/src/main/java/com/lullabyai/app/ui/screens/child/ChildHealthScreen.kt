@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun ChildHealthScreen(
    childViewModel: ChildViewModel,
    chatViewModel: com.lullabyai.app.viewmodel.ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child
    val healthRecords = childState.healthRecords
    val allergies = childState.allergies
    val vaccines = childState.vaccines
    val medicationDoses = childState.medicationDoses
    val medicalDocuments = childState.medicalDocuments
    // No permanent dismissal by design — reappears every time this screen loads until an allergy
    // record exists, since Health/Disease Risk AI both read allergy history (buildChildContext).
    var showMissingInfo by remember { mutableStateOf(true) }
    val missingFields = remember(child, allergies) {
        if (child != null && allergies.isEmpty()) listOf(if (s.isThai) "ประวัติการแพ้" else "Allergy history") else emptyList()
    }

    LaunchedEffect(child) {
        child?.let {
            chatViewModel.loadHealthAnalysis(it, s.isThai)
            chatViewModel.loadDiseaseRisk(it, s.isThai)
        }
    }
    LaunchedEffect(child?.id) {
        childViewModel.loadMedications()
        childViewModel.loadMedicalDocuments()
    }

    ChildScreenScaffold(title = if (s.isThai) "สุขภาพ" else "Health", currentTab = "health", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Profile header (centered)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.size(84.dp).clip(CircleShape).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                            val avatar = childState.childAvatarSignedUrl
                            if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text("👶", fontSize = 40.sp)
                        }
                        Text(child?.name ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                            Text((if (s.isThai) "อายุ " else "Age ") + childViewModel.calculateAge(s.isThai), modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            child?.heightCm?.let { StatChip(Icons.Filled.Height, "$it " + (if (s.isThai) "ซม." else "cm")) }
                            child?.weightKg?.let { StatChip(Icons.Filled.MonitorWeight, "$it " + (if (s.isThai) "กก." else "kg")) }
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFF4CAF50).copy(0.12f)) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Text(if (s.isThai) "สุขภาพดีเยี่ยม" else "Excellent health", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // AI risk analysis card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if (s.isThai) "วิเคราะห์ความเสี่ยงโดย AI" else "AI Risk Analysis", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                                Text(if (s.isThai) "อัปเดตล่าสุด: วันนี้" else "Last updated: today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.Info, null, tint = Outline, modifier = Modifier.size(18.dp))
                        }
                        com.lullabyai.app.ui.components.AiTextOrTimeout(
                            text = chatState.healthAnalysis,
                            timeoutMessage = if (s.isThai) "ไม่สามารถโหลดข้อมูลเพิ่มเติม" else "Unable to load additional info",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatBox(if (s.isThai) "ภูมิคุ้มกัน" else "Immunity", if (s.isThai) "ระดับปกติ" else "Normal", Primary, Modifier.weight(1f))
                            StatBox(if (s.isThai) "ความเสี่ยงแพ้" else "Allergy risk", if (s.isThai) "ต่ำมาก" else "Very low", Secondary, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Allergy history
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Error.copy(0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(16.dp))
                            }
                            Text(if (s.isThai) "ประวัติการแพ้" else "Allergy history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        if (allergies.isEmpty()) {
                            Text(if (s.isThai) "ยังไม่มีประวัติการแพ้" else "No allergy records yet", style = MaterialTheme.typography.bodySmall, color = Outline)
                        } else {
                            allergies.forEach { a ->
                                val sevColor = when (a.severity) {
                                    "severe" -> Error; "moderate" -> Color(0xFFFF9800); else -> Color(0xFFFBC02D)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(sevColor.copy(0.06f)).padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(sevColor))
                                    Text(a.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    Surface(shape = RoundedCornerShape(6.dp), color = sevColor.copy(0.15f)) {
                                        Text(a.severity.uppercase(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = sevColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { navController.navigate(Screen.AllergyHistory.route) }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Filled.Add, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (s.isThai) "เพิ่ม / แก้ไขรายการ" else "Add / edit", color = Primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Vaccine progress summary
            item {
                val total = vaccines.size
                val completed = vaccines.count { it.status == "completed" }
                val pct = if (total > 0) completed * 100 / total else 0
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.VaccineBook.route) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Vaccines, null, tint = Primary, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (s.isThai) "สมุดวัคซีน" else "Vaccine Book", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (s.isThai) "รับแล้ว $completed / $total เข็ม" else "$completed / $total doses received", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("$pct%", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                    }
                }
            }

            // Medication summary + medicine alert
            item {
                // Earliest pending dose — includes overdue ones, since a dose stays "due" until acted on.
                val nextDose = medicationDoses.filter { it.status == "pending" }
                    .mapNotNull { d -> com.lullabyai.app.util.TimeUtils.parseInstant(d.scheduledAt)?.let { d to it } }
                    .minByOrNull { it.second }
                // Actionable once within 15 min of the scheduled time (before or after).
                val nextDoseWaiting = nextDose?.let {
                    java.time.Duration.between(java.time.Instant.now(), it.second).toMinutes() > 15
                } ?: false
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Medication, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                                Text(if (s.isThai) "การทานยา" else "Medication", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { navController.navigate(Screen.MedicationHistory.route) }) {
                                Text(if (s.isThai) "ดูทั้งหมด" else "View all", color = Primary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (nextDose != null) {
                            Surface(shape = RoundedCornerShape(12.dp), color = (if (nextDoseWaiting) Outline else Primary).copy(0.08f)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Filled.Alarm, null, tint = if (nextDoseWaiting) Outline else Primary, modifier = Modifier.size(20.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(if (nextDoseWaiting) (if (s.isThai) "ยาครั้งถัดไป (รอเวลา)" else "Next dose (waiting)") else (if (s.isThai) "ถึงเวลาทานยาแล้ว" else "Dose due now"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text((com.lullabyai.app.util.TimeUtils.localTime(nextDose.first.scheduledAt) ?: "") + " น.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (nextDoseWaiting) Outline else Primary)
                                    }
                                    if (!nextDoseWaiting) {
                                        Button(onClick = { childViewModel.markDoseTaken(nextDose.first.id) }, shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                            Text(if (s.isThai) "ยืนยันให้ยาแล้ว" else "Confirm", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(if (s.isThai) "ไม่มีตารางการทานยาที่ต้องดำเนินการ" else "No pending doses", style = MaterialTheme.typography.bodySmall, color = Outline)
                        }
                    }
                }
            }

            // Medical documents summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.MedicalDocumentHistory.route) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Secondary.copy(0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Description, null, tint = Secondary, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (s.isThai) "เอกสารทางการแพทย์" else "Medical Documents", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (s.isThai) "${medicalDocuments.size} ไฟล์" else "${medicalDocuments.size} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                    }
                }
            }

            // Health records
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.History, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                                Text(if (s.isThai) "บันทึกสุขภาพล่าสุด" else "Recent health records", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                onClick = { navController.navigate(Screen.AddHealthRecord.route) },
                                shape = RoundedCornerShape(100.dp), color = Primary
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(if (s.isThai) "บันทึกใหม่" else "New record", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        if (healthRecords.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                Text(if (s.isThai) "ยังไม่มีบันทึกสุขภาพ" else "No health records yet", color = Outline, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            healthRecords.take(5).forEach { record ->
                                HealthRecordRow(record = record, onClick = {
                                    childViewModel.selectHealthRecord(record.id)
                                    navController.navigate(Screen.HealthDetail.route)
                                })
                            }
                            TextButton(onClick = { navController.navigate(Screen.HealthHistory.route) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (s.isThai) "ดูประวัติสุขภาพทั้งหมด" else "View full health history", color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Card(
                    onClick = { navController.navigate(Screen.WasteRecord.route) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.WaterDrop, null, tint = Primary)
                        Column(Modifier.weight(1f)) {
                            Text(if (s.isThai) "บันทึกการขับถ่าย" else "Waste Record", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(if (s.isThai) "ติดตามปัสสาวะและอุจจาระ" else "Track urine & stool", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showMissingInfo) {
        com.lullabyai.app.ui.components.MissingChildInfoPrompt(
            missingFields = missingFields, isThai = s.isThai,
            onFillIn = { showMissingInfo = false; navController.navigate(Screen.AllergyHistory.route) },
            onSkip = { showMissingInfo = false }
        )
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HealthRecordRow(record: HealthRecord, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(com.lullabyai.app.util.TimeUtils.localDate(record.recordedAt)?.substring(5)?.replace("-", "/") ?: "", style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.width(44.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(record.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            record.description?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        record.temperature?.let {
            Surface(shape = RoundedCornerShape(8.dp), color = (if (it > 37.5) Error else Color(0xFF4CAF50)).copy(0.12f)) {
                Text("$it°C", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = if (it > 37.5) Error else Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(18.dp))
    }
}

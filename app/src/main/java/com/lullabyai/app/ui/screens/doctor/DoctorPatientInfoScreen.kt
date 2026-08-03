@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.data.remote.DoctorPatientDriveHelper
import com.lullabyai.app.viewmodel.DoctorPatientInfoViewModel

/** Doctor-side read-only patient chart: basic info, allergy/chronic-condition history, vaccine
 *  coverage ring + upcoming dose, and the latest visit note. Mirrors Stitch
 *  "Patient Info with Vaccine List". */
@Composable
fun DoctorPatientInfoScreen(childId: String, navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientInfoViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(childId, s.isThai) { vm.load(childId, s.isThai) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { vm.onDriveConsentHandled(childId, s.isThai) }

    LaunchedEffect(state.driveConsentIntent) {
        val intent = state.driveConsentIntent ?: return@LaunchedEffect
        vm.clearDriveConsentIntent()
        driveConsentLauncher.launch(intent)
    }

    state.driveAccountPickerAccounts?.let { accounts ->
        DriveAccountPickerDialog(
            accounts = accounts,
            isThai = s.isThai,
            onDismiss = { vm.dismissDriveAccountPicker() },
            onAccountSelected = { email -> vm.connectDriveWithAccount(childId, email, s.isThai) }
        )
    }

    val child = state.child
    val total = state.vaccines.size
    val completed = state.vaccines.count { it.status == "completed" }
    val pct = if (total > 0) completed * 100 / total else 0
    val nextDose = state.vaccines.filter { it.status != "completed" && it.status != "skipped" }
        .minByOrNull { it.scheduledDate ?: "9999" }
    val ageMonths = child?.let { runCatching { com.lullabyai.app.data.VaccineSetupCatalog.ageMonthsFromBirthdate(it.birthdate) }.getOrDefault(0) } ?: 0
    val context = androidx.compose.ui.platform.LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var editHn by remember(state.hn) { mutableStateOf(state.hn) }
    var editNote by remember(state.clinicNote) { mutableStateOf(state.clinicNote) }

    if (showEditDialog && child != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (s.isThai) "แก้ไขข้อมูล (ฝั่งคลินิก)" else "Edit clinic details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (s.isThai) "ข้อมูลเด็กเป็นของผู้ปกครอง แก้ไขได้เฉพาะ HN และบันทึกของคลินิก"
                        else "Child data belongs to the parent; only the clinic's HN and note are editable.",
                        style = MaterialTheme.typography.bodySmall, color = Outline
                    )
                    OutlinedTextField(
                        value = editHn, onValueChange = { editHn = it },
                        label = { Text("HN") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNote, onValueChange = { editNote = it },
                        label = { Text(if (s.isThai) "บันทึกของคลินิก" else "Clinic note") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.saveDoctorFields(child.id, editHn, editNote, s.isThai); showEditDialog = false },
                    enabled = !state.savingDoctorFields
                ) { Text(if (s.isThai) "บันทึก" else "Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ข้อมูลคนไข้" else "Patient Info",
                onBack = { navController.popBackStack() },
                navController = navController,
                centerTitle = true,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading || child == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(36.dp))
                        }
                        Column {
                            Text(child.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
                            Text(
                                "HN: " + clinicalHn(child.id),
                                style = MaterialTheme.typography.labelMedium, color = Outline
                            )
                            Text(
                                (if (s.isThai) "${ageMonths / 12} ปี ${ageMonths % 12} เดือน" else "${ageMonths / 12}y ${ageMonths % 12}m"),
                                style = MaterialTheme.typography.labelMedium, color = Outline
                            )
                        }
                    }
                }
            }
            // Stitch "สถานะการเติบโต": last recorded weight/height plus the verdict from the app's
            // own GrowthReference bands. Verdict is omitted when there is no weight to judge.
            if (child.weightKg != null || child.heightCm != null) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader(Icons.Filled.MonitorWeight, if (s.isThai) "สถานะการเติบโต" else "Growth status")
                                Text(if (s.isThai) "ล่าสุด" else "Latest", style = MaterialTheme.typography.labelSmall, color = Outline)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                child.weightKg?.let { w ->
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("$w", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary)
                                        Text(if (s.isThai) "กก." else "kg", style = MaterialTheme.typography.labelMedium, color = Outline)
                                    }
                                }
                                child.heightCm?.let { h ->
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("$h", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary)
                                        Text(if (s.isThai) "ซม." else "cm", style = MaterialTheme.typography.labelMedium, color = Outline)
                                    }
                                }
                            }
                            com.lullabyai.app.util.GrowthReference.weightStatus(child.weightKg, ageMonths)?.let { status ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = status.color, modifier = Modifier.size(18.dp))
                                    Text(
                                        if (s.isThai) status.labelTh else status.labelEn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = status.color,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(Icons.Filled.PersonSearch, if (s.isThai) "ข้อมูลพื้นฐาน" else "Basic Info")
                        InfoRow(if (s.isThai) "วันเกิด" else "Birthdate", child.birthdate)
                        InfoRow(if (s.isThai) "เพศ" else "Gender", child.gender)
                        InfoRow(if (s.isThai) "หมู่เลือด" else "Blood group", child.bloodGroup ?: "-")
                        InfoRow(if (s.isThai) "สัญชาติ" else "Nationality", child.nationality ?: "-")
                    }
                }
            }
            state.pediaIntake?.let { intake ->
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(Icons.Filled.Assignment, if (s.isThai) "Pedia Connect (ลงทะเบียน)" else "Pedia Connect (registration)")
                            InfoRow(if (s.isThai) "ชื่อในระบบ Pedia" else "Pedia name", intake.name)
                            intake.birthdate?.let { InfoRow(if (s.isThai) "วันเกิด" else "Birthdate", it) }
                            intake.bloodGroup?.takeIf { it.isNotBlank() }?.let { InfoRow(if (s.isThai) "หมู่เลือด" else "Blood group", it) }
                            intake.allergies?.takeIf { it.isNotBlank() }?.let { InfoRow(if (s.isThai) "แพ้" else "Allergies", it) }
                            intake.notes?.takeIf { it.isNotBlank() }?.let { InfoRow(if (s.isThai) "หมายเหตุ" else "Notes", it) }
                            state.pediaGuardian?.let { g ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                Text(if (s.isThai) "ผู้ปกครองหลัก" else "Main guardian", style = MaterialTheme.typography.labelSmall, color = Outline)
                                Text(g.fullName, fontWeight = FontWeight.SemiBold)
                                g.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Outline) }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(Icons.Filled.MedicalInformation, if (s.isThai) "ประวัติทางการแพทย์" else "Medical History", tint = Error)
                        Text(if (s.isThai) "ประวัติแพ้ยา / แพ้อาหาร" else "Allergies", style = MaterialTheme.typography.labelSmall, color = Outline)
                        if (state.allergies.isEmpty() && child.allergies.isNullOrBlank()) {
                            Text(if (s.isThai) "ไม่มี" else "None", color = Outline, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.allergies.forEach { a ->
                                    Surface(shape = RoundedCornerShape(8.dp), color = ErrorContainer) {
                                        Text(a.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = OnErrorContainer)
                                    }
                                }
                                if (state.allergies.isEmpty()) child.allergies?.let {
                                    Surface(shape = RoundedCornerShape(8.dp), color = ErrorContainer) {
                                        Text(it, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = OnErrorContainer)
                                    }
                                }
                            }
                        }
                        Text(if (s.isThai) "โรคประจำตัว" else "Chronic conditions", style = MaterialTheme.typography.labelSmall, color = Outline)
                        Text(
                            state.chronicConditions.joinToString(", ") { it.name }.ifBlank { if (s.isThai) "ไม่มี" else "None" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Tertiary) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Vaccines, null, tint = Color.White)
                            Text(
                                if (s.isThai) "สถานะวัคซีน (จากแอปผู้ปกครอง)" else "Vaccine status (from parent app)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                                CircularProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.White,
                                    trackColor = Color.White.copy(0.2f),
                                    strokeWidth = 6.dp
                                )
                                Text("$pct%", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (s.isThai) "ครบถ้วนตามช่วงอายุ" else "Up to date",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                nextDose?.let {
                                    Text(
                                        (if (s.isThai) "ครั้งถัดไป: " else "Next: ") + it.vaccineName,
                                        color = Color.White.copy(0.85f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } ?: Text(
                                    if (s.isThai) "ไม่มีนัดวัคซีนที่รอ" else "No upcoming doses",
                                    color = Color.White.copy(0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (state.vaccines.isEmpty()) {
                            Text(
                                if (s.isThai) "ยังไม่มีข้อมูลวัคซีนในแอปผู้ปกครอง"
                                else "No vaccine records in the parent app yet",
                                color = Color.White.copy(0.85f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            val completed = state.vaccines.filter { it.status == "completed" }
                                .sortedByDescending { it.dateGiven ?: it.scheduledDate ?: "" }
                            val upcoming = state.vaccines.filter { it.status != "completed" && it.status != "skipped" }
                                .sortedBy { it.scheduledDate ?: "9999" }
                            completed.take(4).forEach { v ->
                                VaccineBookRow(v, isThai = s.isThai, done = true, onDark = true)
                            }
                            upcoming.take(2).forEach { v ->
                                VaccineBookRow(v, isThai = s.isThai, done = false, onDark = true)
                            }
                        }
                        TextButton(
                            onClick = { navController.navigate(Screen.DoctorPatientVaccineHistory.route(childId)) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text(if (s.isThai) "ดูประวัติการรับวัคซีน" else "View vaccine history")
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(Icons.Filled.HistoryEdu, if (s.isThai) "บันทึกสรุปล่าสุด" else "Latest summary")
                        val visit = state.latestVisit
                        if (visit == null) {
                            Text(if (s.isThai) "ยังไม่มีบันทึกการตรวจ" else "No visit recorded yet", color = Outline, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Surface(shape = RoundedCornerShape(10.dp), color = SurfaceContainerLow) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(visit.diagnosis ?: (if (s.isThai) "ไม่มีการวินิจฉัย" else "No diagnosis"), fontWeight = FontWeight.Bold, color = Primary)
                                    visit.treatmentPlan?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            }
            // Stitch footer: พิมพ์ประวัติคนไข้ (PDF) + แก้ไขข้อมูล. The PDF is generated from what
            // this screen already shows; แก้ไขข้อมูล edits only the clinic's own HN/note, because
            // `children` belongs to the parent (children RLS allows the doctor SELECT only).
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val file = com.lullabyai.app.util.DoctorReportGenerator.generate(
                                context = context,
                                data = doctorChartData(state, child, ageMonths, s.isThai),
                                categories = setOf("basic", "vaccine", "health"),
                                detailed = true
                            )
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                clipData = android.content.ClipData.newRawUri("report", uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    send,
                                    if (s.isThai) "พิมพ์ประวัติคนไข้" else "Print patient record"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(Icons.Filled.Print, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (s.isThai) "พิมพ์ประวัติคนไข้ (PDF)" else "Print patient record (PDF)",
                            fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.isThai) "แก้ไขข้อมูล" else "Edit details", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    state.error?.let { Text(it, color = Error, style = MaterialTheme.typography.labelMedium) }
                }
            }
            // Google Drive backup — always visible so the doctor can connect and sync files.
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(Icons.Filled.Cloud, if (s.isThai) "รายละเอียดใน Google Drive" else "Detailed records (Google Drive)")
                        if (!state.driveConnected) {
                            Text(
                                state.driveError ?: DoctorPatientDriveHelper.userMessage(s.isThai),
                                color = Outline,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { vm.connectDrive(childId, s.isThai) },
                                enabled = !state.driveLoading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                if (state.driveLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text(if (s.isThai) "เชื่อมต่อ Google Drive" else "Connect Google Drive", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            state.driveConnectedAccount?.let { account ->
                                Text(
                                    if (s.isThai) "บัญชี: $account" else "Account: $account",
                                    color = Outline,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(
                                onClick = { vm.connectDrive(childId, s.isThai, forceAccountPicker = true) },
                                enabled = !state.driveLoading
                            ) {
                                Text(
                                    if (s.isThai) "เปลี่ยนบัญชี Google" else "Change Google account",
                                    color = Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            when {
                            state.driveLoading -> CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            state.driveError != null && state.driveRecords.isEmpty() ->
                                Text(state.driveError!!, color = Outline, style = MaterialTheme.typography.bodySmall)
                            state.driveRecords.isEmpty() ->
                                Text(
                                    if (s.isThai) "เชื่อมต่อแล้ว — ยังไม่มีไฟล์รายละเอียดใน Drive" else "Connected — no detailed Drive files yet",
                                    color = Outline,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            else -> state.driveRecords.forEach { file ->
                                Surface(
                                    onClick = { vm.openDriveRecord(file.id, s.isThai) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SurfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Description, null, tint = Primary, modifier = Modifier.size(20.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(file.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            file.modifiedTime?.let {
                                                Text(it.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = Outline)
                                            }
                                        }
                                        Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedDriveRecord?.let { rec ->
        AlertDialog(
            onDismissRequest = { vm.clearSelectedDriveRecord() },
            title = { Text(if (s.isThai) "รายละเอียดการตรวจ" else "Clinical detail") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rec.recordedAt.take(19).replace('T', ' ').let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                    if (rec.symptoms.isNotBlank()) {
                        Text(if (s.isThai) "อาการ / การตรวจ" else "Observations", fontWeight = FontWeight.Bold)
                        Text(rec.symptoms, style = MaterialTheme.typography.bodySmall)
                    }
                    if (rec.diagnosis.isNotBlank()) {
                        Text(if (s.isThai) "การวินิจฉัย" else "Diagnosis", fontWeight = FontWeight.Bold)
                        Text(rec.diagnosis, style = MaterialTheme.typography.bodySmall)
                    }
                    if (rec.treatmentPlan.isNotBlank()) {
                        Text(if (s.isThai) "แผนการรักษา" else "Treatment", fontWeight = FontWeight.Bold)
                        Text(rec.treatmentPlan, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearSelectedDriveRecord() }) {
                    Text(if (s.isThai) "ปิด" else "Close")
                }
            }
        )
    }
}

@Composable
private fun VaccineBookRow(
    v: com.lullabyai.app.data.model.VaccineRecord,
    isThai: Boolean,
    done: Boolean,
    onDark: Boolean = false
) {
    val rowBg = when {
        onDark -> Color.White.copy(0.12f)
        done -> HealthGood.copy(0.08f)
        else -> SurfaceContainerLow
    }
    val primaryText = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (onDark) Color.White.copy(0.78f) else Outline
    Surface(shape = RoundedCornerShape(14.dp), color = rowBg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                null,
                tint = if (done) HealthGood else HealthAttention,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                // Stitch row: "BCG (แรกเกิด)" / "15 ม.ค. 2565" / hospital / status chip. Lot numbers
                // are not on vaccine_records, so no lot line is invented here.
                Text(
                    v.ageGroup?.takeIf { it.isNotBlank() }?.let { "${v.vaccineName} ($it)" } ?: v.vaccineName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryText
                )
                val date = (if (done) v.dateGiven else v.scheduledDate)?.takeIf { it.isNotBlank() }
                date?.let { raw ->
                    val shown = com.lullabyai.app.ui.screens.doctorconnect.thaiDateTimeLabel(raw, isThai, withTime = false) ?: raw
                    Text(
                        (if (done) shown else (if (isThai) "เป้าหมาย: $shown" else "Target: $shown")),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedText
                    )
                }
                v.place?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = mutedText)
                }
            }
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (done) HealthGood.copy(0.18f) else HealthAttention.copy(0.18f)
            ) {
                Text(
                    if (done) (if (isThai) "ฉีดแล้ว" else "Given") else (if (isThai) "รอนัดหมาย" else "Scheduled"),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (done) HealthGood else HealthAttention
                )
            }
        }
    }
}

/** Maps what this chart already shows into the shared PDF model. Only fields the doctor can see
 *  are filled; the rest stay blank so the report never implies data the clinic does not hold. */
private fun doctorChartData(
    state: com.lullabyai.app.viewmodel.DoctorPatientInfoUiState,
    child: com.lullabyai.app.data.model.Child,
    ageMonths: Int,
    isThai: Boolean
): com.lullabyai.app.util.DoctorReportGenerator.ChildData {
    val today = java.time.LocalDate.now().toString()
    return com.lullabyai.app.util.DoctorReportGenerator.ChildData(
        documentRef = "HN " + clinicalHn(child.id),
        recordDate = today,
        childCode = child.name,
        nickname = "",
        birthdate = child.birthdate,
        age = if (isThai) "${ageMonths / 12} ปี ${ageMonths % 12} เดือน" else "${ageMonths / 12}y ${ageMonths % 12}m",
        gender = child.gender,
        idNumber = "",
        nationality = child.nationality ?: "",
        ethnicity = "",
        religion = "",
        unitClass = "",
        recorder = "",
        updatedDate = today,
        vaccines = state.vaccines.map { v ->
            com.lullabyai.app.util.DoctorReportGenerator.Vac(
                ageGroup = v.ageGroup ?: "",
                name = v.vaccineName,
                dateGiven = v.dateGiven ?: v.scheduledDate ?: "",
                place = v.place ?: "",
                note = if (v.status == "completed") "" else (if (isThai) "รอนัดหมาย" else "Scheduled")
            )
        },
        development = List(com.lullabyai.app.util.DoctorReportGenerator.DEV_DOMAINS.size) {
            com.lullabyai.app.util.DoctorReportGenerator.DevStatus(normal = false, monitor = false, note = "")
        },
        devObservation = "",
        regularFood = "",
        allergyAvoid = state.allergies.joinToString(", ") { it.name },
        eatingProblem = "",
        growth = listOfNotNull(
            if (child.weightKg != null || child.heightCm != null) {
                com.lullabyai.app.util.DoctorReportGenerator.Growth(
                    date = today,
                    weight = child.weightKg?.toString() ?: "",
                    height = child.heightCm?.toString() ?: "",
                    status = com.lullabyai.app.util.GrowthReference.weightStatus(child.weightKg, ageMonths)
                        ?.let { if (isThai) it.labelTh else it.labelEn } ?: "",
                    note = ""
                )
            } else null
        ),
        nutritionAdvice = "",
        chronicDisease = state.chronicConditions.joinToString(", ") { it.name },
        drugFoodAllergy = state.allergies.joinToString(", ") { it.name }
            .ifBlank { child.allergies.orEmpty() },
        medications = "",
        majorIllness = "",
        surgeryHospital = "",
        followupDoctor = state.latestVisit?.nextAppointmentDate ?: "",
        healthHistoryDetail = state.clinicNote,
        currentCare = "",
        sleepSummary = "",
        medicationSummary = "",
        overallSummary = state.latestVisit?.diagnosis ?: "",
        recommendations = state.latestVisit?.treatmentPlan ?: ""
    )
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = Primary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

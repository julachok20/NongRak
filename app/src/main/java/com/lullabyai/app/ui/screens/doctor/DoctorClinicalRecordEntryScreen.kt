@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.components.ScheduleDatePickerField
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.AppointmentBookingMeta
import com.lullabyai.app.util.VisitReasonKind
import com.lullabyai.app.util.VisitReasonTemplates
import androidx.activity.ComponentActivity
import com.lullabyai.app.viewmodel.DoctorClinicalRecordEntryViewModel
import com.lullabyai.app.viewmodel.DoctorVaccineCertificateViewModel

/** Doctor writes up a completed visit for one appointment — symptoms/observations, vitals,
 *  diagnosis, and an optional follow-up date. Saving writes a doctor_visits row (doctor_id set)
 *  and marks the appointment completed. Mirrors Stitch "Clinical Record Entry (Doctor Side)". */
@Composable
fun DoctorClinicalRecordEntryScreen(
    appointmentId: String,
    navController: NavController,
    vm: DoctorClinicalRecordEntryViewModel = viewModel()
) {
    val s = LocalStrings.current
    val certVm: DoctorVaccineCertificateViewModel = viewModel(LocalContext.current as ComponentActivity)
    val state by vm.uiState.collectAsState()
    val certWorking by certVm.isWorking.collectAsState()
    val certError by certVm.error.collectAsState()
    LaunchedEffect(appointmentId) { vm.load(appointmentId) }

    val draft = state.draft
    // Gate on the type the PARENT actually picked at booking, not on keywords in free text —
    // otherwise a general visit that merely mentions วัคซีน offered to send a certificate.
    val bookedType = remember(state.appointment?.reason, state.appointment?.notes) {
        AppointmentBookingMeta.parseType(state.appointment?.reason, state.appointment?.notes)
    }
    val isVaccineVisit = bookedType == com.lullabyai.app.util.AppointmentBookingType.VACCINE
    val reasonKind = if (isVaccineVisit) VisitReasonKind.VACCINE else VisitReasonKind.GENERAL

    val context = androidx.compose.ui.platform.LocalContext.current
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val childId = state.child?.id ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "document"
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) return@rememberLauncherForActivityResult
        vm.attachDocument(childId, name, resolver.getType(uri), bytes, s.isThai)
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                vm.patchDraft {
                    d -> d.copy(symptoms = if (d.symptoms.isBlank()) spoken else "${d.symptoms} $spoken")
                }
            }
        }
    }
    fun startSpeech() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (s.isThai) "th-TH" else "en-US")
        }
        runCatching { speechLauncher.launch(intent) }
    }

    val quickSymptoms = remember(s.isThai) {
        if (s.isThai) listOf("มีไข้สูง", "ไอมีเสมหะ", "ปอดปกติ", "คอแดง")
        else listOf("High fever", "Productive cough", "Clear lungs", "Red throat")
    }

    LaunchedEffect(state.saved) {
        if (!state.saved) return@LaunchedEffect
        if (isVaccineVisit) {
            android.widget.Toast.makeText(
                context,
                if (s.isThai) "บันทึกแล้ว — ส่งใบรับรองวัคซีนได้ด้านล่าง" else "Saved — you can send the vaccine certificate below",
                android.widget.Toast.LENGTH_LONG
            ).show()
            vm.acknowledgeSaved()
        } else {
            navController.popBackStack()
        }
    }

    // Surface non-fatal Drive upload warning before navigating away when save succeeds with warning
    // (saved=true still pops; warning is mainly for in-flight display if pop is delayed).

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "บันทึกการตรวจ" else "Clinical Record Entry",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(40.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(state.child?.name ?: "-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            // Stitch shows HN / อายุ / เพศ under the name so the doctor can confirm
                            // they are writing on the right chart.
                            state.child?.let { child ->
                                Text(
                                    "HN: " + clinicalHn(child.id),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Outline
                                )
                                clinicalAgeGender(child.birthdate, child.gender, s.isThai)?.let { meta ->
                                    Text(meta, style = MaterialTheme.typography.bodySmall, color = Outline)
                                }
                            }
                            state.appointment?.reason?.let {
                                Text(AppointmentBookingMeta.displayReason(it, s.isThai), style = MaterialTheme.typography.bodySmall, color = Outline)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.child?.allergies?.takeIf { it.isNotBlank() }?.let { allergy ->
                                    Surface(shape = RoundedCornerShape(8.dp), color = ErrorContainer) {
                                        Text((if (s.isThai) "แพ้ยา: " else "Allergy: ") + allergy, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = OnErrorContainer)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = Primary.copy(0.1f)) {
                                    Text(
                                        visitKindLabel(reasonKind, s.isThai),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Title and actions stack instead of sharing one row: on a 360dp screen the
                        // heading plus the action labels overflowed and clipped the mic button.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.EditNote, null, tint = Primary)
                            Text(
                                if (s.isThai) "บันทึกการตรวจทางคลินิก" else "Clinical Observations",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Stitch "ใช้เสียงบันทึก" — same Thai recognizer the chatbot uses.
                            OutlinedButton(onClick = { startSpeech() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(Icons.Filled.Mic, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (s.isThai) "ใช้เสียงบันทึก" else "Dictate", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { documentPicker.launch(arrayOf("application/pdf", "image/*")) },
                                enabled = !state.isUploading && state.child != null,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (s.isThai) "อัปโหลดเอกสาร" else "Upload file", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                        if (state.isUploading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                                Text(
                                    if (s.isThai) "กำลังอัปโหลด..." else "Uploading…",
                                    style = MaterialTheme.typography.labelMedium, color = Outline
                                )
                            }
                        }
                        if (state.attachments.isNotEmpty()) {
                            state.attachments.forEach { name ->
                                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Description, null, tint = Primary, modifier = Modifier.size(16.dp))
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Filled.CheckCircle, null, tint = HealthGood, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        } else {
                            Text(
                                if (s.isThai) "หรืออัปโหลดเอกสารที่นี่ (PDF / รูปภาพ)" else "Or upload documents here (PDF / image)",
                                style = MaterialTheme.typography.labelSmall, color = Outline
                            )
                        }
                        OutlinedTextField(
                            value = draft.symptoms, onValueChange = { vm.patchDraft { d -> d.copy(symptoms = it) } }, modifier = Modifier.fillMaxWidth().height(140.dp),
                            placeholder = { Text(if (s.isThai) "พิมพ์อาการสำคัญ ประวัติการเจ็บป่วย และผลการตรวจร่างกาย..." else "Chief complaint, history, physical exam findings...") },
                            shape = RoundedCornerShape(16.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickSymptoms.forEach { tag ->
                                SuggestionChip(tag) {
                                    vm.patchDraft {
                                        d -> d.copy(symptoms = if (d.symptoms.isBlank()) tag else "${d.symptoms}, $tag")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (s.isThai) "สัญญาณชีพ (VITALS)" else "VITALS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Outline)
                        StyledVitalRow(Icons.Filled.MonitorWeight, if (s.isThai) "น้ำหนัก" else "Weight", draft.weight, "kg") { vm.patchDraft { d -> d.copy(weight = it) } }
                        StyledVitalRow(Icons.Filled.Thermostat, if (s.isThai) "อุณหภูมิ" else "Temperature", draft.temperature, "°C", highlight = draft.temperature.toDoubleOrNull()?.let { it >= 38.0 } == true) { vm.patchDraft { d -> d.copy(temperature = it) } }
                        StyledVitalRow(Icons.Filled.Height, if (s.isThai) "ส่วนสูง" else "Height", draft.height, "cm") { vm.patchDraft { d -> d.copy(height = it) } }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (s.isThai) "การวินิจฉัย" else "Diagnosis", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Outline)
                        OutlinedTextField(value = draft.diagnosis, onValueChange = { vm.patchDraft { d -> d.copy(diagnosis = it) } }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(if (s.isThai) "รหัส ICD-10 หรือ ชื่อโรค..." else "ICD-10 code or diagnosis name...") }, singleLine = true)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Medication, null, tint = Primary)
                            Text(
                                if (s.isThai) "แผนการรักษาและสั่งยา" else "Prescriptions",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedTextField(
                            value = draft.medicineName, onValueChange = { vm.patchDraft { d -> d.copy(medicineName = it) } },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (s.isThai) "ชื่อยา (Medicine Name)" else "Medicine name") }
                        )
                        OutlinedTextField(
                            value = draft.medicineInstructions, onValueChange = { vm.patchDraft { d -> d.copy(medicineInstructions = it) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (s.isThai) "วิธีใช้ (Instructions)" else "Instructions") }
                        )
                        OutlinedButton(
                            onClick = {
                                vm.patchDraft {
                                    d -> d.copy(
                                        prescriptions = d.prescriptions + (d.medicineName.trim() to d.medicineInstructions.trim()),
                                        medicineName = "",
                                        medicineInstructions = ""
                                    )
                                }
                            },
                            enabled = draft.medicineName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.isThai) "เพิ่มรายการยา" else "Add medicine")
                        }
                        HorizontalDivider(color = OutlineVariant.copy(0.3f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (s.isThai) "รายการยาที่สั่ง" else "Prescribed items",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Outline
                            )
                            Text(
                                if (s.isThai) "${draft.prescriptions.size} รายการ" else "${draft.prescriptions.size} items",
                                style = MaterialTheme.typography.labelMedium, color = Outline
                            )
                        }
                        if (draft.prescriptions.isEmpty()) {
                            Text(
                                if (s.isThai) "ยังไม่มีรายการเพิ่มเติม" else "No items yet",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                        } else {
                            draft.prescriptions.forEachIndexed { index, item ->
                                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerLow) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            if (item.second.isNotBlank()) {
                                                Text(item.second, style = MaterialTheme.typography.bodySmall, color = Outline)
                                            }
                                        }
                                        IconButton(onClick = {
                                            vm.patchDraft { d -> d.copy(prescriptions = d.prescriptions.filterIndexed { i, _ -> i != index }) }
                                        }) {
                                            Icon(Icons.Filled.Delete, null, tint = Error, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Campaign, null, tint = Primary)
                            Text(if (s.isThai) "คำแนะนำถึงผู้ปกครอง" else "Advice for the guardian", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (s.isThai) "ข้อความนี้จะแสดงให้ผู้ปกครองเห็นตรง ๆ ในผลการตรวจและบันทึกการหาหมอ"
                            else "Shown to the guardian verbatim in Lab Results and their visit record",
                            style = MaterialTheme.typography.bodySmall, color = Outline
                        )
                        OutlinedTextField(
                            value = draft.doctorAdvice, onValueChange = { vm.patchDraft { d -> d.copy(doctorAdvice = it) } },
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            placeholder = { Text(if (s.isThai) "เช่น ให้ดื่มน้ำมาก ๆ พักผ่อนให้เพียงพอ หากมีไข้เกิน 2 วันให้กลับมาพบแพทย์" else "e.g. plenty of fluids and rest; return if the fever lasts over 2 days") }
                        )
                    }
                }
            }
            if (reasonKind == VisitReasonKind.VACCINE) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Tertiary.copy(alpha = 0.08f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Vaccines, null, tint = Tertiary)
                                Text(
                                    if (s.isThai) "สรุปหลังฉีดวัคซีน (แสดงต่อผู้ปกครอง)" else "Post-vaccination summary (shown to parent)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedTextField(
                                value = draft.vaccineName, onValueChange = { vm.patchDraft { d -> d.copy(vaccineName = it) } },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(if (s.isThai) "ชื่อวัคซีน" else "Vaccine name") }
                            )
                            OutlinedTextField(
                                value = draft.vaccineLot, onValueChange = { vm.patchDraft { d -> d.copy(vaccineLot = it) } },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(if (s.isThai) "Lot / batch" else "Lot / batch") }
                            )
                            OutlinedTextField(
                                value = draft.vaccineSite, onValueChange = { vm.patchDraft { d -> d.copy(vaccineSite = it) } },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(if (s.isThai) "ตำแหน่งฉีด" else "Injection site") }
                            )
                            OutlinedTextField(
                                value = draft.vaccineReaction, onValueChange = { vm.patchDraft { d -> d.copy(vaccineReaction = it) } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (s.isThai) "อาการหลังฉีด (ถ้ามี)" else "Post-vaccine reactions (if any)") }
                            )
                        }
                    }
                }
            }
            // Vaccine booking only: save auto-syncs the dose; this button sends ใบรับรอง (PDF + notify parent).
            if (isVaccineVisit) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Verified, null, tint = Tertiary)
                                Text(
                                    if (s.isThai) "ใบรับรองวัคซีน" else "Vaccine certificate",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                if (s.isThai)
                                    "การฉีดจะอัปเดตในสมุดวัคซีนของผู้ปกครองอัตโนมัติเมื่อบันทึกทะเบียน — กดปุ่มด้านล่างเฉพาะเมื่อต้องการส่งใบรับรอง"
                                else
                                    "The dose syncs to the parent's vaccine book when you save — tap below only to send an official certificate.",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                            OutlinedButton(
                                onClick = {
                                    val childId = state.appointment?.childId ?: return@OutlinedButton
                                    certVm.clearError()
                                    certVm.preparePreview(
                                        context = context,
                                        childId = childId,
                                        vaccineName = draft.vaccineName,
                                        lotNumber = draft.vaccineLot,
                                        injectionSite = draft.vaccineSite,
                                        reactionNotes = draft.vaccineReaction,
                                        isThai = s.isThai
                                    ) {
                                        navController.navigate(Screen.DoctorVaccineCertificatePreview.route)
                                    }
                                },
                                enabled = !certWorking && draft.vaccineName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (certWorking) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (s.isThai) "ส่งใบยืนยันวัคซีน" else "Send vaccine certificate",
                                        maxLines = 1
                                    )
                                }
                            }
                            certError?.let {
                                Text(it, color = Error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (s.isThai) "นัดหมายติดตามผล" else "Follow-up appointment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Outline)
                        ScheduleDatePickerField(
                            value = draft.followUpDate,
                            onValueChange = { vm.patchDraft { d -> d.copy(followUpDate = it) } },
                            placeholder = if (s.isThai) "เลือกวันที่" else "Pick date",
                            isThai = s.isThai,
                            enabled = !draft.noFollowUp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = draft.noFollowUp,
                                onCheckedChange = {
                                    vm.patchDraft { d -> d.copy(noFollowUp = it, followUpDate = if (it) "" else d.followUpDate) }
                                }
                            )
                            Text(
                                if (s.isThai) "ไม่ต้องนัดหมายเพิ่มเติม" else "No follow-up needed",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            if (s.isThai)
                                "รายละเอียดการตรวจจะถูกเก็บอย่างปลอดภัย และผู้ปกครองที่ได้รับอนุญาตเท่านั้นที่เข้าถึงได้"
                            else
                                "Clinical details are stored securely and visible only to authorised guardians",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary
                        )
                    }
                }
            }
            state.error?.let { item { Text(it, color = Error, style = MaterialTheme.typography.labelMedium) } }
            // Drive is OPTIONAL extra storage, not the system of record — `doctor_visits` in Supabase
            // already holds every clinical field. So a failed Drive copy is an advisory, not an error:
            // amber, non-blocking, and the record is saved regardless. Self-gating, since driveWarning
            // is only ever set when an upload was actually attempted (i.e. the doctor opted in).
            state.driveWarning?.let { warning ->
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF57C00).copy(alpha = 0.10f))
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.CloudOff, null, tint = Color(0xFFF57C00))
                            Column {
                                Text(
                                    if (s.isThai) "สำรองขึ้น Google Drive ไม่สำเร็จ (บันทึกในระบบแล้ว)"
                                    else "Google Drive backup failed — record still saved",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF57C00)
                                )
                                Text(warning, style = MaterialTheme.typography.labelMedium, color = Color(0xFFF57C00))
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            android.widget.Toast.makeText(
                                context,
                                if (s.isThai) "ฟีเจอร์พิมพ์บันทึกการรักษากำลังจะเปิดให้ใช้งานเร็วๆ นี้"
                                else "Print treatment record is coming soon",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        enabled = state.child != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Print, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (s.isThai) "พิมพ์ใบสรุป" else "Print summary",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = {
                            val childId = state.appointment?.childId ?: return@Button
                            val advice = VisitReasonTemplates.buildStructuredAdvice(
                                kind = reasonKind,
                                vaccineName = draft.vaccineName,
                                lotNumber = draft.vaccineLot,
                                injectionSite = draft.vaccineSite,
                                reactionNotes = draft.vaccineReaction,
                                generalAdvice = draft.doctorAdvice
                            )
                            val planWithMeds = buildString {
                                draft.prescriptions.forEach { (name, howTo) ->
                                    if (isNotEmpty()) append("\n")
                                    append("• $name")
                                    if (howTo.isNotBlank()) append(" — $howTo")
                                }
                            }
                            vm.saveRecord(
                                appointmentId = appointmentId, childId = childId, symptoms = draft.symptoms, diagnosis = draft.diagnosis,
                                treatmentPlan = planWithMeds, weightKg = draft.weight.toDoubleOrNull(), heightCm = draft.height.toDoubleOrNull(),
                                temperatureC = draft.temperature.toDoubleOrNull(), nextAppointmentDate = draft.followUpDate.ifBlank { null },
                                doctorAdvice = advice,
                                isThai = s.isThai,
                                isVaccineVisit = isVaccineVisit,
                                vaccineName = draft.vaccineName,
                                vaccineLot = draft.vaccineLot,
                                vaccineSite = draft.vaccineSite,
                                vaccineReaction = draft.vaccineReaction
                            )
                        },
                        enabled = !state.isSaving && state.appointment != null,
                        modifier = Modifier.weight(2f).height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (s.isThai) "บันทึกทะเบียนประวัติ" else "Save Clinical Record", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Same derived chart number the registration list shows, so both screens agree. */
internal fun clinicalHn(childId: String): String {
    val raw = childId.replace("-", "").take(8).uppercase()
    return if (raw.length >= 5) "${raw.take(2)}-${raw.drop(2).take(5)}" else raw
}

/** "อายุ: 2 ปี 3 เดือน | เพศ: ชาย" — null when the child row carries neither. */
internal fun clinicalAgeGender(birthdate: String?, gender: String?, isThai: Boolean): String? {
    val agePart = birthdate?.takeIf { it.isNotBlank() }?.let { bd ->
        runCatching {
            val m = com.lullabyai.app.data.VaccineSetupCatalog.ageMonthsFromBirthdate(bd)
            if (isThai) "อายุ: ${m / 12} ปี ${m % 12} เดือน" else "Age: ${m / 12}y ${m % 12}m"
        }.getOrNull()
    }
    val genderPart = when (gender?.lowercase()) {
        "male", "m" -> if (isThai) "เพศ: ชาย" else "Sex: Male"
        "female", "f" -> if (isThai) "เพศ: หญิง" else "Sex: Female"
        else -> null
    }
    return listOfNotNull(agePart, genderPart).takeIf { it.isNotEmpty() }?.joinToString("  |  ")
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = SurfaceContainerLow) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = Outline)
    }
}

@Composable
private fun StyledVitalRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String,
    highlight: Boolean = false,
    onChange: (String) -> Unit
) {
    val bg = if (highlight) Error.copy(0.08f) else SurfaceContainerLow
    Surface(shape = RoundedCornerShape(16.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (highlight) Error else Primary, modifier = Modifier.size(20.dp))
                }
                Text(label, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = value, onValueChange = onChange, modifier = Modifier.width(88.dp), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (highlight) Error else Primary)
                )
                Text(unit, color = Outline, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun visitKindLabel(kind: VisitReasonKind, isThai: Boolean): String = when (kind) {
    VisitReasonKind.VACCINE -> if (isThai) "นัดวัคซีน" else "Vaccination"
    VisitReasonKind.GENERAL -> if (isThai) "นัดหมายปกติ" else "Routine visit"
}

@Composable
private fun VitalRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, unit: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = value, onValueChange = onChange, modifier = Modifier.width(90.dp), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )
            Text(unit, color = Outline, style = MaterialTheme.typography.labelSmall)
        }
    }
}

package com.lullabyai.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.VaccineSetupCatalog
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.OnboardingViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingStep3Screen(
    onboardingViewModel: OnboardingViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state by onboardingViewModel.uiState.collectAsState()
    var questionIndex by remember { mutableIntStateOf(0) }
    val totalQuestions = 4

    var hasMedication by remember { mutableStateOf(state.hasTakenMedication) }
    var medDetails by remember { mutableStateOf(state.medicationDetails) }
    var conditions by remember { mutableStateOf(state.underlyingConditions) }

    var allergyName by remember { mutableStateOf("") }
    var allergySymptoms by remember { mutableStateOf("") }

    var selectedVaccines by remember {
        mutableStateOf(
            state.selectedVaccines.map { VaccineSetupCatalog.normalizeSelection(it) ?: it }.toMutableSet()
        )
    }
    var customVaccines by remember { mutableStateOf(state.selectedVaccines.filterNot { it in VaccineSetupCatalog.ALL_DB_NAMES }) }
    var customInput by remember { mutableStateOf("") }

    var notes by remember { mutableStateOf(state.additionalNotes) }

    val childAgeMonths = remember(state.birthdate) { VaccineSetupCatalog.ageMonthsFromBirthdate(state.birthdate) }
    val suggestedDoses = remember(childAgeMonths) { VaccineSetupCatalog.suggestedDosesForAge(childAgeMonths) }

    fun commitAndFinish() {
        onboardingViewModel.updateStep3(
            hasMed = hasMedication,
            medDetails = medDetails,
            vaccines = selectedVaccines.toList(),
            conditions = conditions,
            notes = notes
        )
        onboardingViewModel.completeOnboarding {
            onNext()
        }
    }

    fun handleBack() {
        if (questionIndex > 0) {
            questionIndex--
        } else {
            onBack()
        }
    }

    BackHandler(onBack = { handleBack() })

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { handleBack() }) {
                    Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ข้อมูลเพิ่มเติมเพื่อการวิเคราะห์ AI",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "คำถามที่ ${questionIndex + 1} จาก $totalQuestions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(modifier = Modifier.size(36.dp))
            }

            // Step Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (0 until totalQuestions).forEach { idx ->
                    val isCurrent = idx == questionIndex
                    val isDone = idx < questionIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(if (isCurrent) 28.dp else 12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent -> Primary
                                    isDone -> Primary.copy(alpha = 0.5f)
                                    else -> Outline.copy(alpha = 0.25f)
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedContent(
                    targetState = questionIndex,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "QuestionTransition"
                ) { targetIndex ->
                    when (targetIndex) {
                        0 -> Question1MedicationAndConditions(
                            hasMedication = hasMedication,
                            onHasMedicationChange = { hasMedication = it },
                            medDetails = medDetails,
                            onMedDetailsChange = { medDetails = it },
                            conditions = conditions,
                            onConditionsChange = { conditions = it }
                        )
                        1 -> Question2Allergies(
                            state = state,
                            onboardingViewModel = onboardingViewModel,
                            allergyName = allergyName,
                            onAllergyNameChange = { allergyName = it },
                            allergySymptoms = allergySymptoms,
                            onAllergySymptomsChange = { allergySymptoms = it }
                        )
                        2 -> Question3Vaccines(
                            birthdate = state.birthdate,
                            childAgeMonths = childAgeMonths,
                            suggestedDoses = suggestedDoses,
                            selectedVaccines = selectedVaccines,
                            onVaccinesChange = { selectedVaccines = it },
                            customVaccines = customVaccines,
                            onCustomVaccinesChange = { customVaccines = it },
                            customInput = customInput,
                            onCustomInputChange = { customInput = it },
                            onboardingViewModel = onboardingViewModel,
                            pendingDocsCount = state.pendingDocs.size,
                            pendingDocs = state.pendingDocs,
                            isLoading = state.isLoading
                        )
                        else -> Question4NotesAndConcerns(
                            notes = notes,
                            onNotesChange = { notes = it }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action Buttons
                if (questionIndex < totalQuestions - 1) {
                    PrimaryButton(
                        text = "ถัดไป →",
                        onClick = {
                            questionIndex++
                        }
                    )
                    TextButton(
                        onClick = {
                            questionIndex++
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(
                            "ข้ามคำถามนี้",
                            color = Primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    PrimaryButton(
                        text = if (state.isLoading) "..." else "เสร็จสิ้น เข้าสู่หน้าหลักเลย ✓",
                        enabled = !state.isLoading,
                        onClick = { commitAndFinish() }
                    )
                    TextButton(
                        onClick = {
                            onboardingViewModel.setNoKnownAllergies(true)
                            commitAndFinish()
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
                        enabled = !state.isLoading
                    ) {
                        Text(
                            "ข้ามและเข้าสู่หน้าหลัก",
                            color = Primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Question1MedicationAndConditions(
    hasMedication: Boolean,
    onHasMedicationChange: (Boolean) -> Unit,
    medDetails: String,
    onMedDetailsChange: (String) -> Unit,
    conditions: String,
    onConditionsChange: (String) -> Unit
) {
    MedicalCard {
        Text("คำถามที่ 1", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("ประวัติยาและโรคประจำตัว", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("ลูกของคุณเคยได้รับยาประจำ หรือมีโรคประจำตัวหรือไม่?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Text("การใช้ยาประจำ:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(true to "เคยได้รับยา", false to "ไม่เคย").forEach { (value, label) ->
                OutlinedButton(
                    onClick = { onHasMedicationChange(value) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    border = if (hasMedication == value) androidx.compose.foundation.BorderStroke(1.5.dp, Primary) else ButtonDefaults.outlinedButtonBorder,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (hasMedication == value) Primary.copy(0.1f) else Color.Transparent,
                        contentColor = if (hasMedication == value) Primary else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    if (hasMedication == value) Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp), tint = Primary)
                    else Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(16.dp), tint = Error)
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 13.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        if (hasMedication) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = medDetails, onValueChange = onMedDetailsChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ระบุชื่อยาหรือข้อมูลการใช้ยา", color = Outline) },
                shape = RoundedCornerShape(12.dp), colors = fieldColors()
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("โรคประจำตัว / ข้อควรระวังทางการแพทย์:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = conditions, onValueChange = onConditionsChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("เช่น หอบหืด, G6PD, โรคหัวใจ (ถ้าไม่มีสามารถเว้นว่างได้)", color = Outline) },
            shape = RoundedCornerShape(12.dp), colors = fieldColors(), minLines = 2
        )
    }
}

@Composable
private fun Question2Allergies(
    state: com.lullabyai.app.viewmodel.OnboardingUiState,
    onboardingViewModel: OnboardingViewModel,
    allergyName: String,
    onAllergyNameChange: (String) -> Unit,
    allergySymptoms: String,
    onAllergySymptomsChange: (String) -> Unit
) {
    MedicalCard {
        Text("คำถามที่ 2", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("ประวัติการแพ้ (Allergies)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("ลูกของคุณมีประวัติการแพ้อาหารหรือยาหรือไม่?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (state.noKnownAllergies) Primary.copy(0.08f) else Color.Transparent)
                .clickable {
                    onboardingViewModel.setNoKnownAllergies(!state.noKnownAllergies)
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.noKnownAllergies,
                onCheckedChange = { onboardingViewModel.setNoKnownAllergies(it) }
            )
            Text("ไม่มีประวัติแพ้ (No known allergies)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }

        if (!state.noKnownAllergies) {
            Spacer(Modifier.height(12.dp))
            Text("เพิ่มสิ่งที่แพ้:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = allergyName, onValueChange = onAllergyNameChange,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("สารก่อภูมิแพ้ เช่น นมวัว, ไข่ขาว, อาหารทะเล, ยาพารา", color = Outline) },
                shape = RoundedCornerShape(12.dp), colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = allergySymptoms, onValueChange = onAllergySymptomsChange,
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("อาการ เช่น ผื่นลมพิษ, หายใจมีเสียงวี้ด", color = Outline) },
                    shape = RoundedCornerShape(12.dp), colors = fieldColors()
                )
                FilledIconButton(
                    onClick = {
                        if (allergyName.isNotBlank()) {
                            onboardingViewModel.addAllergyEntry(allergyName, allergySymptoms)
                            onAllergyNameChange("")
                            onAllergySymptomsChange("")
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary)
                ) { Icon(Icons.Filled.Add, "เพิ่มการแพ้", tint = Color.White) }
            }

            if (state.allergyEntries.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("รายการที่บันทึกแล้ว:", style = MaterialTheme.typography.labelSmall, color = Outline)
                state.allergyEntries.forEachIndexed { i, a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Primary)
                            if (a.symptoms.isNotBlank()) Text("อาการ: ${a.symptoms}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onboardingViewModel.removeAllergyEntry(i) }) {
                            Icon(Icons.Filled.DeleteOutline, "ลบ", tint = Error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Question3Vaccines(
    birthdate: String,
    childAgeMonths: Int,
    suggestedDoses: Set<String>,
    selectedVaccines: MutableSet<String>,
    onVaccinesChange: (MutableSet<String>) -> Unit,
    customVaccines: List<String>,
    onCustomVaccinesChange: (List<String>) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    onboardingViewModel: OnboardingViewModel,
    pendingDocsCount: Int,
    pendingDocs: List<com.lullabyai.app.viewmodel.PendingDoc>,
    isLoading: Boolean
) {
    MedicalCard {
        Text("คำถามที่ 3", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("ประวัติการฉีดวัคซีน", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "เลือกวัคซีนที่ลูกเคยได้รับแล้ว (ดูจากสมุดสุขภาพสีชมพู) เพื่อให้ AI คำนวณตารางวัคซีนถัดไป",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (birthdate.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "อายุลูกประมาณ $childAgeMonths เดือน — แนะนำเลือกวัคซีนตามเกณฑ์อายุ",
                style = MaterialTheme.typography.bodySmall,
                color = Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onVaccinesChange(mutableSetOf()) },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ยังไม่เคยฉีด", style = MaterialTheme.typography.labelMedium, fontSize = 13.sp, maxLines = 1)
            }
            Button(
                onClick = { onVaccinesChange(suggestedDoses.toMutableSet()) },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = birthdate.isNotBlank()
            ) {
                Text(
                    "ตามเกณฑ์ ($childAgeMonths ด.)",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "เลือกแล้ว ${selectedVaccines.count { it in VaccineSetupCatalog.ALL_DB_NAMES }} / ${VaccineSetupCatalog.SCHEDULE.size} เข็ม",
            style = MaterialTheme.typography.labelSmall,
            color = Outline
        )
        Spacer(Modifier.height(12.dp))

        VaccineSetupCatalog.SETUP_GROUPS.forEach { group ->
            val groupNames = group.doses.map { it.dbName }
            val allSelected = groupNames.all { it in selectedVaccines }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("อายุ ${group.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (group.hint.isNotBlank()) {
                                Text(group.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = {
                            val newSet = selectedVaccines.toMutableSet().apply {
                                if (allSelected) groupNames.forEach { remove(it) }
                                else addAll(groupNames)
                            }
                            onVaccinesChange(newSet)
                        }) {
                            Text(if (allSelected) "ยกเลิกทั้งหมด" else "เลือกทั้งหมด", fontSize = 12.sp, color = Primary)
                        }
                    }
                    group.doses.forEach { dose ->
                        val checked = dose.dbName in selectedVaccines
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = selectedVaccines.toMutableSet().apply {
                                        if (checked) remove(dose.dbName) else add(dose.dbName)
                                    }
                                    onVaccinesChange(newSet)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    val newSet = selectedVaccines.toMutableSet().apply {
                                        if (isChecked) add(dose.dbName) else remove(dose.dbName)
                                    }
                                    onVaccinesChange(newSet)
                                }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(dose.dbName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(dose.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("แนบรูปถ่ายสมุดวัคซีน (สมุดสีชมพู):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OnboardingVaccineBookSection(
            onboardingViewModel = onboardingViewModel,
            pendingCount = pendingDocsCount,
            enabled = !isLoading
        )
        OnboardingVaccineBookPendingList(
            onboardingViewModel = onboardingViewModel,
            pendingDocs = pendingDocs,
            enabled = !isLoading
        )
    }
}

@Composable
private fun Question4NotesAndConcerns(
    notes: String,
    onNotesChange: (String) -> Unit
) {
    MedicalCard {
        Text("คำถามที่ 4", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("ข้อมูลเพิ่มเติมและข้อกังวล", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("มีข้อมูลหรือพฤติกรรมใดของลูกน้อยที่ต้องการให้ AI ช่วยติดตามเป็นพิเศษหรือไม่?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = notes, onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("เช่น น้องนอนหลับยาก, กังวลเรื่องการทานอาหาร, ต้องการคำแนะนำเรื่องการเดิน...", color = Outline) },
            shape = RoundedCornerShape(12.dp), colors = fieldColors(), minLines = 4
        )

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text("ข้อมูลสุขภาพทั้งหมดถูกจัดเก็บอย่างปลอดภัยและเป็นส่วนตัวตามมาตรฐาน PDPA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MedicalCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

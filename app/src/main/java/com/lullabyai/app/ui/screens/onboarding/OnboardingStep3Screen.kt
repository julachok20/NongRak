package com.lullabyai.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.VaccineSetupCatalog
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.OnboardingViewModel

// Vaccine schedule UI uses [VaccineSetupCatalog] — same names as the vaccine book.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingStep3Screen(
    onboardingViewModel: OnboardingViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state by onboardingViewModel.uiState.collectAsState()
    var hasMedication by remember { mutableStateOf(state.hasTakenMedication) }
    var medDetails by remember { mutableStateOf(state.medicationDetails) }
    var selectedVaccines by remember {
        mutableStateOf(
            state.selectedVaccines.map { VaccineSetupCatalog.normalizeSelection(it) ?: it }.toMutableSet()
        )
    }
    var conditions by remember { mutableStateOf(state.underlyingConditions) }
    var allergyName by remember { mutableStateOf("") }
    var allergySymptoms by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(state.additionalNotes) }
    var showAllergyError by remember { mutableStateOf(false) }
    val allergyValid = state.noKnownAllergies || state.allergyEntries.isNotEmpty()

    var customVaccines by remember { mutableStateOf(state.selectedVaccines.filterNot { it in VaccineSetupCatalog.ALL_DB_NAMES }) }
    var customInput by remember { mutableStateOf("") }

    val childAgeMonths = remember(state.birthdate) { VaccineSetupCatalog.ageMonthsFromBirthdate(state.birthdate) }
    val suggestedDoses = remember(childAgeMonths) { VaccineSetupCatalog.suggestedDosesForAge(childAgeMonths) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.weight(1f))
                Text("ข้อมูลของเด็ก", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp))
            }

            OnboardingProgressBar(currentStep = 3)
            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Medical History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("ประวัติทางการแพทย์", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Question 1
                MedicalCard {
                    Text("Question 1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Has your child ever taken medication?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("ลูกของคุณเคยได้รับยาแผนกปัจจุบันหรือไม่", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(true to "Yes", false to "No").forEach { (value, label) ->
                            OutlinedButton(
                                onClick = { hasMedication = value },
                                modifier = Modifier.weight(1f),
                                border = if (hasMedication == value) ButtonDefaults.outlinedButtonBorder else null,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (hasMedication == value) Primary.copy(0.1f) else Color.Transparent,
                                    contentColor = if (hasMedication == value) Primary else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                if (hasMedication == value) Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                                else Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(16.dp), tint = Error)
                                Spacer(Modifier.width(4.dp))
                                Text(label)
                            }
                        }
                    }
                    if (hasMedication) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = medDetails, onValueChange = { medDetails = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("หากต้องการยา กรุณาระบุชื่อยา", color = Outline) },
                            shape = RoundedCornerShape(12.dp), colors = fieldColors()
                        )
                    }
                }

                // Question 2 — vaccination (grouped by age for beginner parents)
                MedicalCard {
                    Text("Question 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Vaccination history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("ประวัติการฉีดวัคซีน", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "เลือกวัคซีนที่ลูกได้รับแล้ว — ดูจากสมุดสุขภาพเด็ก (สมุดชมพู) หรือใบรับรองจากโรงพยาบาล",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.birthdate.isNotBlank()) {
                        Text(
                            "อายุลูกประมาณ $childAgeMonths เดือน — แนะนำเลือกวัคซีนที่ควรได้รับแล้วตามอายุ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { selectedVaccines = mutableSetOf() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("ยังไม่เคยฉีด", style = MaterialTheme.typography.labelMedium) }
                        Button(
                            onClick = { selectedVaccines = suggestedDoses.toMutableSet() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = state.birthdate.isNotBlank()
                        ) { Text("เลือกตามอายุ", style = MaterialTheme.typography.labelMedium) }
                    }
                    Spacer(Modifier.height(6.dp))
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
                                        selectedVaccines = selectedVaccines.toMutableSet().apply {
                                            if (allSelected) groupNames.forEach { remove(it) }
                                            else addAll(groupNames)
                                        }
                                    }) {
                                        Text(if (allSelected) "ยกเลิกทั้งหมด" else "เลือกทั้งหมด", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                group.doses.forEach { dose ->
                                    val checked = dose.dbName in selectedVaccines
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedVaccines = selectedVaccines.toMutableSet().apply {
                                                    if (checked) remove(dose.dbName) else add(dose.dbName)
                                                }
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                selectedVaccines = selectedVaccines.toMutableSet().apply {
                                                    if (isChecked) add(dose.dbName) else remove(dose.dbName)
                                                }
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

                    Spacer(Modifier.height(4.dp))
                    Text("วัคซีนอื่น ๆ (ถ้ามี)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customVaccines.forEach { vaccine ->
                            val checked = selectedVaccines.contains(vaccine)
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    selectedVaccines = selectedVaccines.toMutableSet().apply {
                                        if (checked) remove(vaccine) else add(vaccine)
                                    }
                                },
                                label = { Text(vaccine) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close, "ลบ",
                                        modifier = Modifier.size(16.dp).clickable {
                                            customVaccines = customVaccines - vaccine
                                            selectedVaccines = selectedVaccines.toMutableSet().apply { remove(vaccine) }
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(0.12f),
                                    selectedLabelColor = Primary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("เพิ่มวัคซีนอื่น ๆ ที่ลูกได้รับ", color = Outline) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors()
                        )
                        FilledIconButton(
                            onClick = {
                                val v = customInput.trim()
                                if (v.isNotEmpty() && v !in VaccineSetupCatalog.ALL_DB_NAMES && v !in customVaccines) {
                                    customVaccines = customVaccines + v
                                    selectedVaccines = selectedVaccines.toMutableSet().apply { add(v) }
                                    customInput = ""
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Filled.Add, "เพิ่ม", tint = Color.White)
                        }
                    }
                }

                // Question 3
                MedicalCard {
                    Text("Question 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Any underlying medical conditions?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("ลูกของคุณมีโรคประจำตัวหรือไม่", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text("Important for AI Insight\nSharing conditions helps NongRak provide more accurate health tips.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = conditions, onValueChange = { conditions = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Asthma, Allergies...\nType 'None' if not applicable", color = Outline) },
                        shape = RoundedCornerShape(12.dp), colors = fieldColors(), minLines = 2
                    )
                }

                // Question 4 — list-based allergies
                MedicalCard {
                    Text("Question 4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Allergy history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("ประวัติการแพ้", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onboardingViewModel.setNoKnownAllergies(!state.noKnownAllergies)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.noKnownAllergies,
                            onCheckedChange = { onboardingViewModel.setNoKnownAllergies(it) }
                        )
                        Text("ไม่มีประวัติแพ้ (No known allergies)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (!state.noKnownAllergies) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = allergyName, onValueChange = { allergyName = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("สารก่อภูมิแพ้ เช่น ถั่ว, นม", color = Outline) },
                            shape = RoundedCornerShape(12.dp), colors = fieldColors()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = allergySymptoms, onValueChange = { allergySymptoms = it },
                                modifier = Modifier.weight(1f), singleLine = true,
                                placeholder = { Text("อาการ เช่น ผื่น, บวม", color = Outline) },
                                shape = RoundedCornerShape(12.dp), colors = fieldColors()
                            )
                            FilledIconButton(
                                onClick = {
                                    if (allergyName.isNotBlank()) {
                                        onboardingViewModel.addAllergyEntry(allergyName, allergySymptoms)
                                        allergyName = ""; allergySymptoms = ""
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary)
                            ) { Icon(Icons.Filled.Add, "เพิ่มการแพ้", tint = Color.White) }
                        }
                        if (state.allergyEntries.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Added allergies", style = MaterialTheme.typography.labelSmall, color = Outline)
                            state.allergyEntries.forEachIndexed { i, a ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (a.symptoms.isNotBlank()) Text(a.symptoms, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onboardingViewModel.removeAllergyEntry(i) }) {
                                        Icon(Icons.Filled.DeleteOutline, "ลบ", tint = Error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (showAllergyError && !allergyValid) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "กรุณากรอกข้อมูลการแพ้ หรือติ๊กว่าไม่มีประวัติแพ้ (Please fill in allergy info, or check 'No known allergies')",
                            style = MaterialTheme.typography.bodySmall, color = Error, fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Anything else / notes
                MedicalCard {
                    Text("Anything else you'd like us to know?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("ข้อมูลเพิ่มเติมอื่น ๆ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("รายละเอียดหรือข้อกังวลเพิ่มเติม", color = Outline) },
                        shape = RoundedCornerShape(12.dp), colors = fieldColors(), minLines = 2
                    )
                }

                // Ministry vaccine book (optional)
                MedicalCard {
                    OnboardingVaccineBookSection(
                        onboardingViewModel = onboardingViewModel,
                        pendingCount = state.pendingDocs.size,
                        enabled = !state.isLoading
                    )
                    Spacer(Modifier.height(8.dp))
                    OnboardingVaccineBookPendingList(
                        onboardingViewModel = onboardingViewModel,
                        pendingDocs = state.pendingDocs,
                        enabled = !state.isLoading
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, null, tint = Color.White)
                        Text("Secured Medical Data", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                PrimaryButton(
                    text = if (state.isLoading) "..." else "Complete ✓",
                    enabled = !state.isLoading,
                    onClick = {
                        if (!allergyValid) {
                            showAllergyError = true
                        } else {
                            onboardingViewModel.updateStep3(hasMedication, medDetails, selectedVaccines.toList(), conditions, notes)
                            onNext()
                        }
                    }
                )
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("เร็วๆ นี้", color = Primary)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MedicalCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
